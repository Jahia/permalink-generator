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
import org.jahia.services.SpringContextSingleton;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OSGi service that creates and maintains permanent vanity URLs for pages and renderable content.
 *
 * <p>All module-generated vanities carry the {@code jmix:permalinkGenerated} mixin, which allows
 * the service to distinguish them from manually-created vanities. Manual vanities are never
 * overwritten in SMART mode.</p>
 *
 * <p>Entry points consumed by Drools rules ({@code rules.drl}):
 * <ul>
 *   <li>{@link #addVanity} — fired when {@code jcr:title} is set</li>
 *   <li>{@link #onNodeMoved} — fired on node move</li>
 *   <li>{@link #onNodeDeleted} — fired on node deletion</li>
 *   <li>{@link #removePermalinkMixin} — fired when an editor manually edits a vanity URL</li>
 *   <li>{@link #stripCopiedVanities} — fired on node copy</li>
 * </ul>
 *
 * <p>Bulk API consumed by {@code GeneratePermalinksAction}:
 * <ul>
 *   <li>{@link #previewVanityForNodeIds} — compute what would change without persisting</li>
 *   <li>{@link #generateVanityForNodeIds} — apply computed vanities, optionally overwriting manual ones</li>
 * </ul>
 */
@Component(service = PermalinkGeneratorService.class)
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

    private VanityUrlManager vanityUrlManager;

    @Activate
    public void activate() {
        vanityUrlManager = SpringContextSingleton.getInstance().getContext().getBean(VanityUrlManager.class);
    }

    // -------------------------------------------------------------------------
    // Drools entry points
    // -------------------------------------------------------------------------

    /** Drools entry point: {@code jcr:title} was set — create or update the vanity URL for the node. */
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

    /** Drools entry point: node was moved — update vanity URLs for the node and all descendants. */
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

    /** Drools entry point: node was deleted — remove all auto-generated vanity URLs for the node. */
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

    /** Drools entry point: editor changed {@code j:url} directly — strip {@code jmix:permalinkGenerated} so the vanity is treated as manual. */
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

    /** Drools entry point: node was copied — remove module-managed vanities from the copy so they are regenerated fresh. */
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

        // Computed URL already exists as an auto-generated vanity (possibly non-default, inactive, or
        // pending deletion): re-promote it instead of remove+recreate.
        JCRNodeWrapper existingAutoVanity = findAutoGeneratedVanity(node, language, candidateUrl);
        if (existingAutoVanity != null) {
            unmarkForDeletion(existingAutoVanity, session);
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
                PermalinkMode mode = getMode(site);
                if (mode == PermalinkMode.SMART && hasManualActiveDefaultVanity(child, language)) {
                    // Preserve slug, update parent prefix so the full URL stays valid
                    updateManualVanityPrefix(child, language, site, session);
                } else {
                    updateVanityForNode(child, language, site, session);
                }
                updateVanityForDescendants(child, language, site, session);
            }
        }
    }

    /**
     * In SMART cascade: the user chose a custom slug — keep it, but update the parent prefix so the
     * full URL stays valid when an ancestor title changes.  The vanity stays "manual" (no mixin).
     */
    private void updateManualVanityPrefix(JCRNodeWrapper node, String language, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        String existingUrl = getActiveDefaultVanityUrl(node, language);
        if (existingUrl == null) return;
        String newUrl = computeVanityUrlOnMove(node, language, site.getDefaultLanguage(), existingUrl);
        if (newUrl == null || newUrl.equals(existingUrl)) return;
        // Update j:url on the manual vanity node directly (preserves no-mixin / manual status)
        try {
            if (!node.hasNode("vanityUrlMapping")) return;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty("jcr:language") || !language.equals(v.getProperty("jcr:language").getString())) continue;
                if (!v.hasProperty("j:active") || !v.getProperty("j:active").getBoolean()) continue;
                if (!v.hasProperty("j:default") || !v.getProperty("j:default").getBoolean()) continue;
                v.setProperty("j:url", newUrl);
                session.save();
                logger.debug("Updated manual vanity prefix {} → {} for {}", existingUrl, newUrl, node.getPath());
                return;
            }
        } catch (RepositoryException e) {
            logger.warn("Could not update manual vanity prefix for {}: {}", node.getPath(), e.getMessage());
        }
    }

    private void refreshVanityForAllLanguages(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        if (!node.hasNode("vanityUrlMapping")) return;
        NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper vanityNode = (JCRNodeWrapper) it.nextNode();
            if (!vanityNode.hasProperty("jcr:language")) continue;
            String lang = vanityNode.getProperty("jcr:language").getString();
            String existingUrl = vanityNode.hasProperty("j:url") ? vanityNode.getProperty("j:url").getString() : null;
            JCRSessionWrapper langSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(lang), null);
            langSession.refresh(false);
            JCRNodeWrapper freshNode = langSession.getNodeByIdentifier(node.getIdentifier());
            if (vanityNode.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                // Auto-generated: recompute using move logic (preserve slug, update prefix)
                updateVanityForMovedNode(freshNode, lang, existingUrl, site, langSession);
            } else if (vanityNode.hasProperty("j:active") && vanityNode.getProperty("j:active").getBoolean()
                    && vanityNode.hasProperty("j:default") && vanityNode.getProperty("j:default").getBoolean()) {
                // Manual active+default: preserve slug, update parent prefix
                updateManualVanityPrefix(freshNode, lang, site, langSession);
            }
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

    private void unmarkForDeletion(JCRNodeWrapper node, JCRSessionWrapper session) throws RepositoryException {
        boolean changed = false;
        if (node.isNodeType("jmix:markedForDeletionRoot")) { node.removeMixin("jmix:markedForDeletionRoot"); changed = true; }
        if (node.isNodeType("jmix:markedForDeletion"))     { node.removeMixin("jmix:markedForDeletion");     changed = true; }
        if (changed) {
            session.save();
            logger.debug("Unmarked for deletion: {}", node.getPath());
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
                if (v.isNodeType("jmix:markedForDeletion")) continue;
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
     * Compute what vanity URLs would be generated for the given nodes without persisting any change.
     * Used by the admin UI to preview before applying.
     *
     * @param nodeIds   UUIDs of the nodes to preview
     * @param languages language codes (e.g. {@code ["en", "fr"]})
     * @param session   JCR session — used only for read access
     * @param bypassExcluded if {@code true}, ignored excluded-path settings
     * @return one map per node×language with keys {@code uuid}, {@code path}, {@code language},
     *         {@code computedUrl}, {@code currentUrl}, {@code willChange}, {@code isManual}
     */
    public List<Map<String, String>> previewVanityForNodeIds(
            List<String> nodeIds, List<String> languages,
            JCRSessionWrapper session, boolean bypassExcluded) {

        List<Map<String, String>> results = new ArrayList<>();
        if (nodeIds.isEmpty() || languages.isEmpty()) return results;

        // Sort shallow-first so parents are in the cache before children are evaluated
        List<String> sortedIds = sortNodeIdsByDepth(nodeIds, session);

        // nodeId → lang → new computed URL (built as we process; children reuse parent's value)
        Map<String, Map<String, String>> computedCache = new HashMap<>();
        // nodeId → lang → current active+default URL (loaded once per node, all langs)
        Map<String, Map<String, String>> currentVanityCache = new HashMap<>();
        // nodeId → set of langs whose active+default vanity is manual (no jmix:permalinkGenerated)
        Map<String, Set<String>> manualLangCache = new HashMap<>();

        for (String language : languages) {
            JCRSessionWrapper langSession;
            try {
                langSession = JCRSessionFactory.getInstance()
                        .getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(language), null);
            } catch (RepositoryException e) {
                logger.warn("Could not open session for language {}: {}", language, e.getMessage()); continue;
            }
            JCRSiteNode site = null;
            String defaultLang = null;

            for (String nodeId : sortedIds) {
                try {
                    JCRNodeWrapper node = langSession.getNodeByIdentifier(nodeId);
                    if (site == null) {
                        site = node.getResolveSite();
                        if (site == null || !site.getInstalledModules().contains("permalink-generator")) break;
                        defaultLang = site.getDefaultLanguage();
                    }
                    if (node.hasProperty("j:isHomePage") && node.getProperty("j:isHomePage").getBoolean()) continue;
                    if (node.isNodeType(MIXIN_PERMALINK_EXCLUDED)) continue;
                    if (!bypassExcluded && isExcludedPath(node, site)) continue;
                    RenderContext ctx = new RenderContext(null, null, node.getSession().getUser());
                    ctx.setSite(site);
                    if (!JCRContentUtils.isADisplayableNode(node, ctx) || node.isNodeType("jnt:file")) continue;

                    String computedUrl = computeVanityUrlCascade(node, language, defaultLang, computedCache, currentVanityCache);

                    // Cache for children of this node in subsequent iterations
                    if (computedUrl != null) {
                        computedCache.computeIfAbsent(nodeId, k -> new HashMap<>()).put(language, computedUrl);
                    }

                    // Load node's own vanities once (all langs), shared across language passes
                    Map<String, String> nodeVanities = currentVanityCache.computeIfAbsent(nodeId,
                            k -> loadCurrentVanitiesForNode(node));
                    Set<String> manualLangs = manualLangCache.computeIfAbsent(nodeId,
                            k -> loadManualLangsForNode(node));
                    String currentUrl = nodeVanities.get(language);
                    boolean willChange = computedUrl != null && !computedUrl.equals(currentUrl);
                    boolean isManual = manualLangs.contains(language);

                    Map<String, String> entry = new HashMap<>();
                    entry.put("uuid",        nodeId);
                    entry.put("path",        node.getPath());
                    entry.put("language",    language);
                    entry.put("computedUrl", computedUrl != null ? computedUrl : "");
                    entry.put("currentUrl",  currentUrl  != null ? currentUrl  : "");
                    entry.put("willChange",  String.valueOf(willChange));
                    entry.put("isManual",    String.valueOf(isManual));
                    results.add(entry);
                } catch (Exception e) {
                    logger.warn("Could not preview vanity for node {} lang {}: {}", nodeId, language, e.getMessage());
                }
            }
        }
        return results;
    }

    public List<Map<String, String>> generateVanityForNodeIds(List<String> nodeIds, List<String> languages, JCRSessionWrapper session) {
        return generateVanityForNodeIds(nodeIds, languages, session, false);
    }

    /**
     * Apply computed vanity URLs for the given nodes and languages, persisting the result to JCR.
     *
     * @param nodeIds    UUIDs of the nodes to process (depth-sorted internally)
     * @param languages  language codes (e.g. {@code ["en", "fr"]})
     * @param session    JCR session passed to helpers (a locale session is opened per language internally)
     * @param forceRegen if {@code true}, overwrite even manually-set vanity URLs; if {@code false},
     *                   SMART mode applies — manual vanities are preserved
     * @return one map per generated/updated node×language with keys {@code uuid}, {@code path},
     *         {@code language}, {@code action}, {@code url}, {@code oldUrl}
     */
    public List<Map<String, String>> generateVanityForNodeIds(
            List<String> nodeIds, List<String> languages,
            JCRSessionWrapper session, boolean forceRegen) {

        List<Map<String, String>> results = new ArrayList<>();
        if (nodeIds.isEmpty() || languages.isEmpty()) return results;

        // Depth-sort: parents before children → child reads parent's freshly-saved URL
        List<String> sortedIds = sortNodeIdsByDepth(nodeIds, session);

        for (String language : languages) {
            JCRSessionWrapper langSession;
            try {
                langSession = JCRSessionFactory.getInstance()
                        .getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(language), null);
            } catch (RepositoryException e) {
                logger.warn("Could not open session for language {}: {}", language, e.getMessage()); continue;
            }
            for (String nodeId : sortedIds) {
                try {
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
                        Map<String, String> entry = new HashMap<>();
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

    // -------------------------------------------------------------------------
    // Bulk optimisation helpers
    // -------------------------------------------------------------------------

    /**
     * Sort node UUIDs by JCR path depth (shallowest first).
     * Uses path segment count from a neutral session — cheap, no per-locale overhead.
     */
    private List<String> sortNodeIdsByDepth(List<String> nodeIds, JCRSessionWrapper session) {
        final Map<String, Integer> depthMap = new HashMap<>();
        for (String nodeId : nodeIds) {
            try {
                int depth = session.getNodeByIdentifier(nodeId).getPath().split("/").length;
                depthMap.put(nodeId, depth);
            } catch (Exception e) {
                depthMap.put(nodeId, 999);
            }
        }
        List<String> sorted = new ArrayList<>(nodeIds);
        sorted.sort(Comparator.comparingInt(depthMap::get));
        return sorted;
    }

    /**
     * Load all active+default vanity URLs for a node in a single child-node scan.
     * Returns a lang→url map covering every language that has an active default vanity.
     * Result is cached by callers so this scan runs at most once per node per bulk call.
     */
    private Map<String, String> loadCurrentVanitiesForNode(JCRNodeWrapper node) {
        Map<String, String> result = new HashMap<>();
        try {
            if (!node.hasNode("vanityUrlMapping")) return result;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (!v.hasProperty("jcr:language")) continue;
                if (!v.hasProperty("j:active") || !v.getProperty("j:active").getBoolean()) continue;
                if (!v.hasProperty("j:default") || !v.getProperty("j:default").getBoolean()) continue;
                String lang = v.getProperty("jcr:language").getString();
                if (v.hasProperty("j:url")) result.put(lang, v.getProperty("j:url").getString());
            }
        } catch (RepositoryException e) {
            logger.debug("Could not load vanities for {}: {}", node.getPath(), e.getMessage());
        }
        return result;
    }

    /**
     * Returns the set of languages that have a manual (no jmix:permalinkGenerated) active+default
     * vanity URL on this node. Loaded once per node and cached by callers.
     */
    private Set<String> loadManualLangsForNode(JCRNodeWrapper node) {
        Set<String> result = new HashSet<>();
        try {
            if (!node.hasNode("vanityUrlMapping")) return result;
            NodeIterator it = node.getNode("vanityUrlMapping").getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty("jcr:language")) continue;
                if (!v.hasProperty("j:active") || !v.getProperty("j:active").getBoolean()) continue;
                if (!v.hasProperty("j:default") || !v.getProperty("j:default").getBoolean()) continue;
                result.add(v.getProperty("jcr:language").getString());
            }
        } catch (RepositoryException e) {
            logger.debug("Could not load manual langs for {}: {}", node.getPath(), e.getMessage());
        }
        return result;
    }

    /**
     * Like computeVanityUrl but uses two caches to avoid redundant JCR reads:
     *
     *   computedCache    — parent's newly-computed URL from this same bulk call.
     *                      Used when both parent and child are selected for regeneration
     *                      (cascade: child URL reflects parent's new URL, not the old one).
     *
     *   currentVanityCache — parent's current active+default URL, loaded once per parent
     *                        across all languages (shared Map<lang,url> per node).
     *                        Falls back to this when parent is not (yet) in computedCache.
     */
    private String computeVanityUrlCascade(JCRNodeWrapper node, String language, String defaultLanguage,
            Map<String, Map<String, String>> computedCache,
            Map<String, Map<String, String>> currentVanityCache) throws RepositoryException {

        String displayableName = node.getDisplayableName();
        if (displayableName == null) return null;
        String slug = SLUGIFY.slugify(displayableName);
        if (slug.isEmpty()) return null;

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, "jmix:navMenuItem");

        if (!parents.isEmpty()) {
            JCRNodeWrapper directParent = parents.get(0);
            String parentId = directParent.getIdentifier();

            // 1. Cascaded computed URL (parent already processed in this bulk call)
            Map<String, String> parentComputed = computedCache.get(parentId);
            if (parentComputed != null) {
                String parentUrl = parentComputed.get(language);
                if (parentUrl != null) return parentUrl + "/" + slug;
            }

            // 2. Cached current URL (load all langs once per parent, reused across languages)
            Map<String, String> parentCurrent = currentVanityCache.computeIfAbsent(parentId,
                    k -> loadCurrentVanitiesForNode(directParent));
            String parentUrl = parentCurrent.get(language);
            if (parentUrl != null) return parentUrl + "/" + slug;
        }

        // Fallback: rebuild full path from ancestor titles (same logic as computeVanityUrl)
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
