package org.jahiacommunity.modules.permalinkgenerator.services;

import com.github.slugify.Slugify;
import org.drools.core.spi.KnowledgeHelper;
import org.jahia.api.Constants;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.content.rules.CopiedNodeFact;
import org.jahia.services.content.rules.DeletedNodeFact;
import org.jahia.services.content.rules.MovedNodeFact;
import org.jahia.services.render.RenderContext;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.NonUniqueUrlMappingException;
import org.jahia.services.seo.jcr.VanityUrlManager;
import org.jahia.taglibs.jcr.node.JCRTagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates and maintains permanent vanity URLs for pages and displayable content (jmix:renderableMainResource).
 * Auto-generated vanities are tagged with jmix:permalinkGenerated to distinguish
 * them from manually-created vanities, which are never touched.
 */
public class PermalinkGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PermalinkGeneratorService.class);
    private static final String MIXIN_PERMALINK_GENERATED = "jmix:permalinkGenerated";
    private static final String MIXIN_PERMALINK_EXCLUDED = "jmix:permalinkExcluded";
    private static final Slugify SLUGIFY = new Slugify();
    private static final int MAX_URL_ATTEMPTS = 10;

    private enum PermalinkMode { SMART, FORCE }

    /** Outcome of a single node×language vanity operation. */
    public static class VanityOp {
        public final String action; // "created" | "promoted" | "already_correct" | "skipped"
        public final String url;
        public final String oldUrl; // previous active+default vanity, null if none or unchanged
        public VanityOp(String action, String url, String oldUrl) {
            this.action = action; this.url = url; this.oldUrl = oldUrl;
        }
    }

    @Autowired
    private VanityUrlManager vanityUrlManager;

    // -------------------------------------------------------------------------
    // Drools entry points
    // -------------------------------------------------------------------------

    /** jcr:title set — create/update vanity for node and propagate to descendants. */
    public void addVanity(final AddedNodeFact nodeFact, final String language, KnowledgeHelper drools) throws Exception {
        if (language == null) return;
        try {
            JCRSiteNode site = nodeFact.getNode().getResolveSite();
            if (site == null || !site.getInstalledModules().contains("permalink-generator")) return;
            Locale locale = new Locale(language);
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, locale, null);
            JCRNodeWrapper node = resolveNode(nodeFact.getPath(), session);
            if (node == null) return;
            updateVanityForNode(node, language, site, session);
            updateVanityForDescendants(node, language, site, session);
        } catch (RepositoryException re) {
            logger.warn("Could not process vanity for node {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    /** Node moved — update vanity for the node and all descendants across all languages. */
    public void onNodeMoved(final MovedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSiteNode site = nodeFact.getNode().getResolveSite();
            if (site == null || !site.getInstalledModules().contains("permalink-generator")) return;
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
            JCRNodeWrapper node = resolveNode(nodeFact.getPath(), session);
            if (node == null) return;
            logger.debug("onNodeMoved: processing {}", node.getPath());
            refreshVanityForAllLanguages(node, site);
            refreshChildrenRecursive(node, site);
        } catch (RepositoryException re) {
            logger.warn("Could not process vanity for moved node {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    /** Node deleted — deactivate published vanities, delete unpublished ones. */
    public void onNodeDeleted(final DeletedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
            String path = nodeFact.getPath();
            if (path.startsWith("/sites/")) {
                String afterSites = path.substring("/sites/".length());
                String siteKey = afterSites.contains("/") ? afterSites.substring(0, afterSites.indexOf('/')) : afterSites;
                try {
                    JCRSiteNode site = (JCRSiteNode) session.getNode("/sites/" + siteKey);
                    if (!site.getInstalledModules().contains("permalink-generator")) return;
                } catch (RepositoryException ignored) { return; }
            }
            JCRNodeWrapper node = resolveNode(path, session);
            if (node == null) return;
            cleanAllAutoGeneratedVanities(node, session);
        } catch (RepositoryException re) {
            logger.warn("Could not clean vanities for deleted node {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    /**
     * Editor manually changed j:url on a auto-generated vanity — remove the mixin so the
     * vanity is treated as manual from now on and never overwritten by the module.
     */
    public void removePermalinkMixin(final AddedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRNodeWrapper vanityNode = nodeFact.getNode();
            if (vanityNode != null && vanityNode.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                vanityNode.removeMixin(MIXIN_PERMALINK_GENERATED);
                vanityNode.getSession().save();
                logger.debug("Vanity {} manually edited — removed permalink mixin", vanityNode.getPath());
            }
        } catch (RepositoryException e) {
            logger.warn("Could not remove permalink mixin from {}: {}", nodeFact.getPath(), e.getMessage());
        }
    }

    /** Page copied — strip inherited auto-generated vanities so fresh ones are computed from title. */
    public void stripCopiedVanities(final CopiedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
            JCRNodeWrapper node = resolveNode(nodeFact.getPath(), session);
            if (node == null || !node.hasNode("vanityUrlMapping")) return;
            List<JCRNodeWrapper> toRemove = new ArrayList<>();
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) toRemove.add(v);
            }
            for (JCRNodeWrapper v : toRemove) v.remove();
            if (!toRemove.isEmpty()) session.save();
        } catch (RepositoryException re) {
            logger.warn("Could not strip copied vanities for {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Core update logic
    // -------------------------------------------------------------------------

    private VanityOp updateVanityForNode(JCRNodeWrapper node, String language, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        return updateVanityForNode(node, language, site, session, false);
    }

    private VanityOp updateVanityForNode(JCRNodeWrapper node, String language, JCRSiteNode site, JCRSessionWrapper session, boolean forceRegen) throws RepositoryException {
        if (node.hasProperty("j:isHomePage") && node.getProperty("j:isHomePage").getBoolean()) {
            logger.debug("Skip {} [{}]: homepage", node.getPath(), language); return null;
        }
        if (node.isNodeType(MIXIN_PERMALINK_EXCLUDED)) {
            logger.debug("Skip {} [{}]: permalinkExcluded", node.getPath(), language); return null;
        }
        if (isExcludedPath(node, site)) {
            logger.debug("Skip {} [{}]: excluded path", node.getPath(), language); return null;
        }

        PermalinkMode mode = getMode(site);

        RenderContext context = new RenderContext(null, null, node.getSession().getUser());
        context.setSite(site);
        if (!JCRContentUtils.isADisplayableNode(node, context) || node.isNodeType("jnt:file")) {
            logger.info("Skip {} [{}]: not displayable or is file", node.getPath(), language); return null;
        }

        String url = computeVanityUrl(node, language, site.getDefaultLanguage());
        if (url == null) {
            logger.info("Skip {} [{}]: null URL computed", node.getPath(), language); return null;
        }

        // SMART: respect existing manual default vanity — skip unless force-regenerating
        if (!forceRegen && mode == PermalinkMode.SMART && hasManualActiveDefaultVanity(node, language)) {
            logger.debug("Skip {} [{}]: manual vanity exists", node.getPath(), language); return null;
        }

        String candidateUrl = resolveUniqueUrl(url, node, site.getSiteKey(), session);
        if (candidateUrl == null) return null;

        // Already correct (active+default auto-generated) — skip unless force
        if (!forceRegen && hasAutoGeneratedActiveDefaultVanity(node, language, candidateUrl)) {
            return new VanityOp("already_correct", candidateUrl, null);
        }

        // Capture the current default before any mutation so we can report what changed
        String oldUrl = getActiveDefaultVanityUrl(node, language);

        // Computed URL already exists as an auto-generated vanity (possibly non-default or inactive):
        // re-promote it instead of remove+recreate, which would fail when the vanity is live-published.
        JCRNodeWrapper existingAutoVanity = findAutoGeneratedVanity(node, language, candidateUrl);
        if (existingAutoVanity != null) {
            promoteVanityAsDefault(existingAutoVanity, node, language, session);
            logger.debug("Promoted existing vanity {} for node {}", candidateUrl, node.getPath());
            String prevUrl = (oldUrl != null && !oldUrl.equals(candidateUrl)) ? oldUrl : null;
            return new VanityOp("promoted", candidateUrl, prevUrl);
        }

        removeAutoGeneratedVanities(node, language, session);

        try {
            saveVanityWithMixin(node, new VanityUrl(candidateUrl, site.getSiteKey(), language, true, true), session);
            logger.debug("Saved vanity {} for node {}", candidateUrl, node.getPath());
            String prevUrl = (oldUrl != null && !oldUrl.equals(candidateUrl)) ? oldUrl : null;
            return new VanityOp("created", candidateUrl, prevUrl);
        } catch (NonUniqueUrlMappingException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private void updateVanityForDescendants(JCRNodeWrapper node, String language, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType("jmix:navMenuItem")) {
                updateVanityForNode(child, language, site, session);
                updateVanityForDescendants(child, language, site, session);
            }
        }
    }

    private void refreshVanityForAllLanguages(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        if (!node.hasNode("vanityUrlMapping")) return;
        NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper vanityNode = (JCRNodeWrapper) it.nextNode();
            if (!vanityNode.isNodeType(MIXIN_PERMALINK_GENERATED) || !vanityNode.hasProperty("jcr:language")) continue;
            String lang = vanityNode.getProperty("jcr:language").getString();
            // Preserve the existing slug — only the prefix (parent path) changes on move
            String existingUrl = vanityNode.hasProperty("j:url") ? vanityNode.getProperty("j:url").getString() : null;
            JCRSessionWrapper langSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(lang), null);
            langSession.refresh(false);
            updateVanityForMovedNode(langSession.getNodeByIdentifier(node.getIdentifier()), lang, existingUrl, site, langSession);
        }
    }

    private void updateVanityForMovedNode(JCRNodeWrapper node, String language, String existingVanityUrl, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        if (node.hasProperty("j:isHomePage") && node.getProperty("j:isHomePage").getBoolean()) return;
        if (node.isNodeType(MIXIN_PERMALINK_EXCLUDED)) return;
        if (isExcludedPath(node, site)) return;

        PermalinkMode mode = getMode(site);

        RenderContext context = new RenderContext(null, null, node.getSession().getUser());
        context.setSite(site);
        if (!JCRContentUtils.isADisplayableNode(node, context) || node.isNodeType("jnt:file")) return;

        String url = computeVanityUrlOnMove(node, language, site.getDefaultLanguage(), existingVanityUrl);
        if (url == null) return;

        if (mode == PermalinkMode.SMART && hasManualActiveDefaultVanity(node, language)) return;

        String candidateUrl = resolveUniqueUrl(url, node, site.getSiteKey(), session);
        if (candidateUrl == null) return;

        if (hasAutoGeneratedActiveDefaultVanity(node, language, candidateUrl)) return;

        removeAutoGeneratedVanities(node, language, session);
        try {
            saveVanityWithMixin(node, new VanityUrl(candidateUrl, site.getSiteKey(), language, true, true), session);
            logger.debug("Saved vanity {} for moved node {}", candidateUrl, node.getPath());
        } catch (NonUniqueUrlMappingException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void refreshDescendantsForAllLanguages(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        refreshChildrenRecursive(node, site);
    }

    // Recursive: process each node BEFORE its children so parent vanity is ready when child computes its prefix
    private void refreshChildrenRecursive(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType("jmix:navMenuItem")) {
                refreshVanityForAllLanguages(child, site);
                refreshChildrenRecursive(child, site);
            }
        }
    }

    // -------------------------------------------------------------------------
    // URL computation
    // -------------------------------------------------------------------------

    private String computeVanityUrl(JCRNodeWrapper node, String language, String defaultLanguage) throws RepositoryException {
        String displayableName = node.getDisplayableName();
        if (displayableName == null) {
            logger.debug("No displayable name for {}, skipping", node.getPath());
            return null;
        }
        String slug = SLUGIFY.slugify(displayableName);
        if (slug.isEmpty()) {
            logger.warn("Empty slug for node {} (name: '{}'), skipping", node.getPath(), displayableName);
            return null;
        }

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, "jmix:navMenuItem");

        // Prefer parent's existing active+default vanity as base
        if (!parents.isEmpty()) {
            String parentVanity = getActiveDefaultVanityUrl(parents.get(0), language);
            if (parentVanity != null) {
                return parentVanity + "/" + slug;
            }
        }

        // Fallback: rebuild full path from parent titles
        String url = "/" + slug;
        for (JCRNodeWrapper parent : parents) {
            if (!parent.hasProperty("j:isHomePage")) {
                String parentName = parent.getDisplayableName();
                if (parentName != null) {
                    String parentSlug = SLUGIFY.slugify(parentName);
                    if (!parentSlug.isEmpty()) url = "/" + parentSlug + url;
                }
            }
        }
        if (!language.equals(defaultLanguage)) {
            url = "/" + language + url;
        }
        return url;
    }

    /** On move: preserve existing slug, only update the parent prefix. */
    private String computeVanityUrlOnMove(JCRNodeWrapper node, String language, String defaultLanguage, String existingVanityUrl) throws RepositoryException {
        // Extract slug from existing vanity URL
        String slug = null;
        if (existingVanityUrl != null && !existingVanityUrl.isEmpty()) {
            String last = existingVanityUrl.substring(existingVanityUrl.lastIndexOf('/') + 1);
            if (!last.isEmpty()) slug = last;
        }
        // Fallback: compute from title if no existing slug
        if (slug == null) {
            String displayableName = node.getDisplayableName();
            if (displayableName == null) return null;
            slug = SLUGIFY.slugify(displayableName);
            if (slug.isEmpty()) return null;
        }

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, "jmix:navMenuItem");
        if (!parents.isEmpty()) {
            String parentVanity = getActiveDefaultVanityUrl(parents.get(0), language);
            if (parentVanity != null) return parentVanity + "/" + slug;
        }

        // Fallback: rebuild prefix from parent titles
        String url = "/" + slug;
        for (JCRNodeWrapper parent : parents) {
            if (!parent.hasProperty("j:isHomePage")) {
                String parentName = parent.getDisplayableName();
                if (parentName != null) {
                    String parentSlug = SLUGIFY.slugify(parentName);
                    if (!parentSlug.isEmpty()) url = "/" + parentSlug + url;
                }
            }
        }
        if (!language.equals(defaultLanguage)) url = "/" + language + url;
        return url;
    }

    private PermalinkMode getMode(JCRSiteNode site) {
        try {
            if (site.isNodeType("jmix:permalinkGeneratorSettings") && site.hasProperty("j:permalinkGeneratorMode")) {
                String value = site.getProperty("j:permalinkGeneratorMode").getString();
                if (!value.isEmpty()) return PermalinkMode.valueOf(value);
            }
        } catch (Exception e) {
            logger.debug("Could not read permalink mode for site {}: {}", site.getSiteKey(), e.getMessage());
        }
        return PermalinkMode.SMART;
    }

    /** Returns true if the node has a manual (manually-created) active+default vanity for the given language. */
    private boolean hasManualActiveDefaultVanity(JCRNodeWrapper node, String language) {
        try {
            if (!node.hasNode("vanityUrlMapping")) return false;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue; // auto-generated, skip
                if (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString())) continue;
                if (v.hasProperty("j:active") && v.getProperty("j:active").getBoolean()
                        && v.hasProperty("j:default") && v.getProperty("j:default").getBoolean()) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            logger.debug("Could not check manual vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return false;
    }

    private String getActiveDefaultVanityUrl(JCRNodeWrapper node, String language) {
        try {
            if (!node.hasNode("vanityUrlMapping")) return null;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.hasProperty("jcr:language") && language.equals(v.getProperty("jcr:language").getString())
                        && v.hasProperty("j:active") && v.getProperty("j:active").getBoolean()
                        && v.hasProperty("j:default") && v.getProperty("j:default").getBoolean()) {
                    return v.getProperty("j:url").getString();
                }
            }
        } catch (RepositoryException e) {
            logger.debug("Could not read parent vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return null;
    }

    /** Try baseUrl, then baseUrl-2 … baseUrl-N until a free slot is found. */
    private String resolveUniqueUrl(String baseUrl, JCRNodeWrapper node, String siteKey, JCRSessionWrapper session) throws RepositoryException {
        String url = baseUrl;
        for (int attempt = 1; attempt <= MAX_URL_ATTEMPTS; attempt++) {
            List<VanityUrl> conflict = vanityUrlManager.findExistingVanityUrls(url, siteKey, session);
            if (conflict == null || conflict.isEmpty()) return url;
            // Idempotent: conflict on THIS node — URL is already correct
            for (VanityUrl v : conflict) {
                if (v.getIdentifier() == null) continue;
                try {
                    if (node.getIdentifier().equals(session.getNodeByIdentifier(v.getIdentifier()).getParent().getParent().getIdentifier())) {
                        return url;
                    }
                } catch (Exception ignored) {}
            }
            url = baseUrl + "-" + (attempt + 1);
        }
        logger.warn("No unique URL found for {} after {} attempts, skipping", node.getPath(), MAX_URL_ATTEMPTS);
        return null;
    }

    // -------------------------------------------------------------------------
    // Vanity lifecycle helpers
    // -------------------------------------------------------------------------

    /**
     * On rename/move: published auto-generated vanities become inactive redirects (j:default=false,
     * j:active stays true so old URLs keep working until next publication).
     * Unpublished ones are deleted.
     */
    private void removeAutoGeneratedVanities(JCRNodeWrapper contentNode, String language, JCRSessionWrapper session) throws RepositoryException {
        if (!contentNode.hasNode("vanityUrlMapping")) return;
        JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null);
        List<JCRNodeWrapper> toProcess = collectAutoGeneratedVanities(contentNode, language);
        for (JCRNodeWrapper vanityNode : toProcess) {
            try {
                boolean publishedInLive = isPublishedInLive(vanityNode, liveSession);
                if (publishedInLive) {
                    // Keep active as redirect — do NOT set j:active=false, old URL must not 404
                    vanityNode.setProperty("j:default", false);
                } else {
                    vanityNode.remove();
                }
            } catch (Exception e) {
                logger.debug("Could not rotate vanity {}: {}", vanityNode.getPath(), e.getMessage());
            }
        }
        if (!toProcess.isEmpty()) session.save();
    }

    /** On deletion: deactivate published, delete unpublished. */
    private void cleanAllAutoGeneratedVanities(JCRNodeWrapper contentNode, JCRSessionWrapper session) throws RepositoryException {
        if (!contentNode.hasNode("vanityUrlMapping")) return;
        JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null);
        List<JCRNodeWrapper> toProcess = collectAutoGeneratedVanities(contentNode, null);
        for (JCRNodeWrapper vanityNode : toProcess) {
            try {
                if (isPublishedInLive(vanityNode, liveSession)) {
                    vanityNode.setProperty("j:active", false);
                    vanityNode.setProperty("j:default", false);
                } else {
                    vanityNode.remove();
                }
            } catch (Exception e) {
                logger.debug("Could not clean vanity on delete {}: {}", vanityNode.getPath(), e.getMessage());
            }
        }
        if (!toProcess.isEmpty()) session.save();
    }

    /** Collect auto-generated vanity nodes for a given language (null = all languages). */
    private List<JCRNodeWrapper> collectAutoGeneratedVanities(JCRNodeWrapper contentNode, String language) throws RepositoryException {
        List<JCRNodeWrapper> result = new ArrayList<>();
        if (!contentNode.hasNode("vanityUrlMapping")) return result;
        NodeIterator it = contentNode.getNode("vanityUrlMapping").getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
            if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
            if (language != null && (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString()))) continue;
            result.add(v);
        }
        return result;
    }

    private boolean isPublishedInLive(JCRNodeWrapper vanityNode, JCRSessionWrapper liveSession) {
        try {
            liveSession.getNodeByIdentifier(vanityNode.getIdentifier());
            return true;
        } catch (ItemNotFoundException ignored) {
            return false;
        } catch (RepositoryException e) {
            return false;
        }
    }

    private void saveVanityWithMixin(JCRNodeWrapper contentNode, VanityUrl vanityUrl, JCRSessionWrapper session) throws RepositoryException, NonUniqueUrlMappingException {
        vanityUrlManager.saveVanityUrlMapping(contentNode, vanityUrl, session);
        if (!contentNode.hasNode("vanityUrlMapping")) return;
        NodeIterator it = contentNode.getNode("vanityUrlMapping").getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
            if (!v.hasProperty("j:url") || !vanityUrl.getUrl().equals(v.getProperty("j:url").getString())) continue;
            if (!v.hasProperty("jcr:language") || !vanityUrl.getLanguage().equals(v.getProperty("jcr:language").getString())) continue;
            if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                v.addMixin(MIXIN_PERMALINK_GENERATED);
                v.getSession().save();
            }
            break;
        }
    }

    /** Returns the auto-generated vanity node with the given URL for this node/language, or null. */
    private JCRNodeWrapper findAutoGeneratedVanity(JCRNodeWrapper node, String language, String url) {
        try {
            if (!node.hasNode("vanityUrlMapping")) return null;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString())) continue;
                if (v.hasProperty("j:url") && url.equals(v.getProperty("j:url").getString())) return v;
            }
        } catch (RepositoryException e) {
            logger.debug("Could not search auto vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return null;
    }

    /**
     * Promotes {@code vanityToPromote} to active+default for its language,
     * demoting all other vanities of the same language on the same node.
     */
    private void promoteVanityAsDefault(JCRNodeWrapper vanityToPromote, JCRNodeWrapper contentNode,
                                        String language, JCRSessionWrapper session) throws RepositoryException {
        if (contentNode.hasNode("vanityUrlMapping")) {
            NodeIterator it = contentNode.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.getIdentifier().equals(vanityToPromote.getIdentifier())) continue;
                if (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString())) continue;
                if (v.hasProperty("j:default") && v.getProperty("j:default").getBoolean()) {
                    v.setProperty("j:default", false);
                }
            }
        }
        vanityToPromote.setProperty("j:active", true);
        vanityToPromote.setProperty("j:default", true);
        session.save();
    }

    private boolean hasAutoGeneratedActiveDefaultVanity(JCRNodeWrapper node, String language, String url) {
        try {
            if (!node.hasNode("vanityUrlMapping")) return false;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString())) continue;
                if (!v.hasProperty("j:url") || !url.equals(v.getProperty("j:url").getString())) continue;
                if (v.hasProperty("j:active") && v.getProperty("j:active").getBoolean()
                        && v.hasProperty("j:default") && v.getProperty("j:default").getBoolean()) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            logger.debug("Could not check auto vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return false;
    }

    private boolean isExcludedPath(JCRNodeWrapper node, JCRSiteNode site) {
        try {
            if (!site.isNodeType("jmix:permalinkGeneratorSettings") || !site.hasProperty("j:excludedPaths")) return false;
            Value[] values = site.getProperty("j:excludedPaths").getValues();
            String nodePath = node.getPath();
            for (Value v : values) {
                String excluded = v.getString().trim();
                if (!excluded.isEmpty() && nodePath.startsWith(excluded)) return true;
            }
        } catch (RepositoryException e) {
            logger.debug("Could not check excluded paths for {}: {}", node.getPath(), e.getMessage());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Bulk generation (audit panel)
    // -------------------------------------------------------------------------

    /**
     * Generate vanity URLs for a list of node UUIDs across the given languages.
     * Skips nodes that cannot be found or are excluded by site configuration.
     * Returns the number of (node × language) pairs processed.
     */
    public List<java.util.Map<String, String>> generateVanityForNodeIds(List<String> nodeIds, List<String> languages, JCRSessionWrapper session) {
        return generateVanityForNodeIds(nodeIds, languages, session, false);
    }

    public List<java.util.Map<String, String>> generateVanityForNodeIds(List<String> nodeIds, List<String> languages, JCRSessionWrapper session, boolean forceRegen) {
        List<java.util.Map<String, String>> results = new ArrayList<>();
        for (String nodeId : nodeIds) {
            for (String language : languages) {
                try {
                    JCRSessionWrapper langSession = JCRSessionFactory.getInstance()
                            .getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(language), null);
                    JCRNodeWrapper node = langSession.getNodeByIdentifier(nodeId);
                    JCRSiteNode site = node.getResolveSite();
                    if (site == null || !site.getInstalledModules().contains("permalink-generator")) {
                        logger.info("Skip {} [{}]: site null or module not installed", nodeId, language); continue;
                    }
                    logger.info("generateVanityForNodeIds: processing {} [{}] path={} force={}", nodeId, language, node.getPath(), forceRegen);
                    VanityOp op = updateVanityForNode(node, language, site, langSession, forceRegen);
                    if (op != null) {
                        logger.info("[permalink-admin] {} [{}]: {} → {}{}",
                                node.getPath(), language, op.action, op.url,
                                (op.oldUrl != null) ? " (was: " + op.oldUrl + ")" : "");
                        java.util.Map<String, String> entry = new java.util.HashMap<>();
                        entry.put("uuid", nodeId);
                        entry.put("path", node.getPath());
                        entry.put("language", language);
                        entry.put("action", op.action);
                        entry.put("url", op.url != null ? op.url : "");
                        entry.put("oldUrl", op.oldUrl != null ? op.oldUrl : "");
                        results.add(entry);
                    }
                } catch (Exception e) {
                    logger.warn("Could not generate vanity for node {} lang {}: {}", nodeId, language, e.getMessage(), e);
                }
            }
        }
        return results;
    }

    private JCRNodeWrapper resolveNode(String path, JCRSessionWrapper session) {
        try {
            return session.getNode(path);
        } catch (PathNotFoundException e) {
            logger.warn("Node not found in EDIT workspace: {}", path);
            return null;
        } catch (RepositoryException e) {
            logger.warn("Could not load node {}: {}", path, e.getMessage());
            return null;
        }
    }
}
