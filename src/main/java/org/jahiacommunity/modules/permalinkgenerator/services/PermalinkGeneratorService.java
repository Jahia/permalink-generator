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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

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

    // -------------------------------------------------------------------------
    // String constants (S1192)
    // -------------------------------------------------------------------------
    private static final String MIXIN_PERMALINK_GENERATED = "jmix:permalinkGenerated";
    private static final String MIXIN_PERMALINK_EXCLUDED  = "jmix:permalinkExcluded";
    private static final String MODULE_NAME               = "permalink-generator";
    private static final String PERMISSION_SITE_ADMIN     = "siteAdminPermalinkGenerator";
    private static final String PROP_VANITY_MAPPING       = "vanityUrlMapping";
    private static final String PROP_JCR_LANGUAGE         = "jcr:language";
    private static final String PROP_J_DEFAULT            = "j:default";
    private static final String PROP_J_ACTIVE             = "j:active";
    private static final String PROP_J_URL                = "j:url";
    private static final String PROP_J_IS_HOME_PAGE       = "j:isHomePage";
    private static final String MIXIN_NAV_MENU_ITEM       = "jmix:navMenuItem";
    private static final String NT_FILE                   = "jnt:file";
    private static final String SITES_PREFIX              = "/sites/";
    private static final String MIXIN_MARKED_FOR_DELETION      = "jmix:markedForDeletion";
    private static final String MIXIN_MARKED_FOR_DELETION_ROOT = "jmix:markedForDeletionRoot";

    private static final Slugify SLUGIFY = Slugify.builder().build();
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

    @Reference
    private VanityUrlManager vanityUrlManager;

    // -------------------------------------------------------------------------
    // Drools entry points
    // -------------------------------------------------------------------------

    /**
     * Drools entry point: {@code jcr:title} was set — create or update the vanity URL for the node.
     *
     * @param drools Drools helper injected by the rule engine (required by DSL, do not remove)
     */
    @SuppressWarnings({"java:S1172", "java:S112"})
    public void addVanity(final AddedNodeFact nodeFact, final String language, KnowledgeHelper drools) throws Exception {
        if (language == null) return;
        try {
            JCRSiteNode site = nodeFact.getNode().getResolveSite();
            if (site == null || !site.getInstalledModules().contains(MODULE_NAME)) return;
            Locale locale = Locale.forLanguageTag(language);
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, locale, null);
            JCRNodeWrapper node = resolveNode(nodeFact.getPath(), session);
            if (node == null) return;
            updateVanityForNode(node, language, site, session);
            updateVanityForDescendants(node, language, site, session);
        } catch (RepositoryException re) {
            logger.warn("Could not process vanity for node {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    /**
     * Drools entry point: node was moved — update vanity URLs for the node and all descendants.
     *
     * @param drools Drools helper injected by the rule engine (required by DSL, do not remove)
     */
    @SuppressWarnings({"java:S1172", "java:S112"})
    public void onNodeMoved(final MovedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSiteNode site = nodeFact.getNode().getResolveSite();
            if (site == null || !site.getInstalledModules().contains(MODULE_NAME)) return;
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

    /**
     * Drools entry point: node was deleted — remove all auto-generated vanity URLs for the node.
     *
     * @param drools Drools helper injected by the rule engine (required by DSL, do not remove)
     */
    @SuppressWarnings({"java:S1172", "java:S112"})
    public void onNodeDeleted(final DeletedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
            String path = nodeFact.getPath();
            if (!isSiteModuleEnabled(path, session)) return;
            JCRNodeWrapper node = resolveNode(path, session);
            if (node == null) return;
            cleanAllAutoGeneratedVanities(node, session);
        } catch (RepositoryException re) {
            logger.warn("Could not clean vanities for deleted node {} -> {}", nodeFact.getPath(), re.getMessage());
        }
    }

    /**
     * Checks whether the permalink-generator module is enabled for the site owning the given path.
     * Returns {@code false} (safe default — skip processing) on any exception.
     */
    private boolean isSiteModuleEnabled(String path, JCRSessionWrapper session) {
        if (!path.startsWith(SITES_PREFIX)) return true; // non-site path: let caller decide
        String afterSites = path.substring(SITES_PREFIX.length());
        String siteKey = afterSites.contains("/") ? afterSites.substring(0, afterSites.indexOf('/')) : afterSites;
        try {
            JCRSiteNode site = (JCRSiteNode) session.getNode(SITES_PREFIX + siteKey);
            return site.getInstalledModules().contains(MODULE_NAME);
        } catch (RepositoryException | ClassCastException ignored) {
            // Node inaccessible or not a site node — skip safely
            return false;
        }
    }

    /**
     * Drools entry point: editor changed {@code j:url} directly — strip {@code jmix:permalinkGenerated}
     * so the vanity is treated as manual.
     *
     * <p>Re-entrancy guard: when the triggering session is a system session (module writes),
     * this method is a no-op to avoid stripping the mixin we just added.</p>
     *
     * @param drools Drools helper injected by the rule engine (required by DSL, do not remove)
     */
    @SuppressWarnings({"java:S1172", "java:S112"})
    public void removePermalinkMixin(final AddedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRNodeWrapper vanityNode = nodeFact.getNode();
            if (vanityNode == null) return;
            // Re-entrancy guard: system session writes come from this module itself — do not strip.
            if (vanityNode.getSession().isSystem()) {
                logger.debug("removePermalinkMixin: skipping system-session write on {}", vanityNode.getPath());
                return;
            }
            if (vanityNode.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                vanityNode.removeMixin(MIXIN_PERMALINK_GENERATED);
                vanityNode.getSession().save();
                logger.debug("Vanity {} manually edited — removed permalink mixin", vanityNode.getPath());
            }
        } catch (RepositoryException e) {
            logger.warn("Could not remove permalink mixin from {}: {}", nodeFact.getPath(), e.getMessage());
        }
    }

    /**
     * Drools entry point: node was copied — remove module-managed vanities from the copy so they
     * are regenerated fresh.
     *
     * @param drools Drools helper injected by the rule engine (required by DSL, do not remove)
     */
    @SuppressWarnings({"java:S1172", "java:S112"})
    public void stripCopiedVanities(final CopiedNodeFact nodeFact, KnowledgeHelper drools) throws Exception {
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
            JCRNodeWrapper node = resolveNode(nodeFact.getPath(), session);
            if (node == null || !node.hasNode(PROP_VANITY_MAPPING)) return;
            List<JCRNodeWrapper> toRemove = new ArrayList<>();
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
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
        if (isNodeSkipped(node, site)) return null;
        if (!isDisplayableNonFile(node, site, language)) return null;

        String url = computeVanityUrl(node, language, site.getDefaultLanguage());
        if (url == null) {
            logger.info("Skip {} [{}]: null URL computed", node.getPath(), language); return null;
        }

        PermalinkMode mode = getMode(site);

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

        return applyVanityUpdate(node, language, site, session, candidateUrl);
    }

    /**
     * Returns false (and logs) if the node is not displayable or is a file type.
     * Extracted from {@link #updateVanityForNode} to reduce cognitive complexity.
     */
    private boolean isDisplayableNonFile(JCRNodeWrapper node, JCRSiteNode site, String language) throws RepositoryException {
        RenderContext context = new RenderContext(null, null, node.getSession().getUser());
        context.setSite(site);
        if (!JCRContentUtils.isADisplayableNode(node, context) || node.isNodeType(NT_FILE)) {
            logger.info("Skip {} [{}]: not displayable or is file", node.getPath(), language);
            return false;
        }
        return true;
    }

    /**
     * Applies the vanity mutation: promote an existing auto-generated vanity or create a fresh one.
     * Extracted from {@link #updateVanityForNode} to reduce cognitive complexity.
     */
    private VanityOp applyVanityUpdate(JCRNodeWrapper node, String language, JCRSiteNode site,
                                       JCRSessionWrapper session, String candidateUrl) throws RepositoryException {
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
        return createFreshVanity(node, language, site, session, candidateUrl, oldUrl);
    }

    /**
     * Saves a brand-new auto-generated vanity and returns the resulting {@link VanityOp}.
     * Extracted from {@link #applyVanityUpdate} to reduce cognitive complexity.
     */
    private VanityOp createFreshVanity(JCRNodeWrapper node, String language, JCRSiteNode site,
                                       JCRSessionWrapper session, String candidateUrl, String oldUrl) throws RepositoryException {
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

    /**
     * Common early-exit checks: homepage, excluded mixin, excluded path.
     * Returns {@code true} if the node should be skipped.
     */
    private boolean isNodeSkipped(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        if (node.hasProperty(PROP_J_IS_HOME_PAGE) && node.getProperty(PROP_J_IS_HOME_PAGE).getBoolean()) {
            logger.debug("Skip {}: homepage", node.getPath()); return true;
        }
        if (node.isNodeType(MIXIN_PERMALINK_EXCLUDED)) {
            logger.debug("Skip {}: permalinkExcluded", node.getPath()); return true;
        }
        if (isExcludedPath(node, site)) {
            logger.debug("Skip {}: excluded path", node.getPath()); return true;
        }
        return false;
    }

    private void updateVanityForDescendants(JCRNodeWrapper node, String language, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType(MIXIN_NAV_MENU_ITEM)) {
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
        updateManualVanityUrl(node, language, existingUrl, newUrl, session);
    }

    /** Locates the active+default manual vanity for the node/language and updates its j:url. */
    private void updateManualVanityUrl(JCRNodeWrapper node, String language, String existingUrl, String newUrl, JCRSessionWrapper session) {
        try {
            if (!node.hasNode(PROP_VANITY_MAPPING)) return;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) continue;
                if (!v.hasProperty(PROP_J_ACTIVE) || !v.getProperty(PROP_J_ACTIVE).getBoolean()) continue;
                if (!v.hasProperty(PROP_J_DEFAULT) || !v.getProperty(PROP_J_DEFAULT).getBoolean()) continue;
                v.setProperty(PROP_J_URL, newUrl);
                session.save();
                logger.debug("Updated manual vanity prefix {} -> {} for {}", existingUrl, newUrl, node.getPath());
                return;
            }
        } catch (RepositoryException e) {
            logger.warn("Could not update manual vanity prefix for {}: {}", node.getPath(), e.getMessage());
        }
    }

    private void refreshVanityForAllLanguages(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        if (!node.hasNode(PROP_VANITY_MAPPING)) return;
        NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper vanityNode = (JCRNodeWrapper) it.nextNode();
            if (!vanityNode.hasProperty(PROP_JCR_LANGUAGE)) continue;
            String lang = vanityNode.getProperty(PROP_JCR_LANGUAGE).getString();
            String existingUrl = vanityNode.hasProperty(PROP_J_URL) ? vanityNode.getProperty(PROP_J_URL).getString() : null;
            Locale locale = Locale.forLanguageTag(lang);
            JCRSessionWrapper langSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, locale, null);
            langSession.refresh(false);
            JCRNodeWrapper freshNode = langSession.getNodeByIdentifier(node.getIdentifier());
            if (vanityNode.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                // Auto-generated: recompute using move logic (preserve slug, update prefix)
                updateVanityForMovedNode(freshNode, lang, existingUrl, site, langSession);
            } else if (isActiveDefault(vanityNode)) {
                // Manual active+default: preserve slug, update parent prefix
                updateManualVanityPrefix(freshNode, lang, site, langSession);
            }
        }
    }

    /** Returns true if the vanity node has j:active=true and j:default=true. */
    private boolean isActiveDefault(JCRNodeWrapper vanityNode) throws RepositoryException {
        return vanityNode.hasProperty(PROP_J_ACTIVE) && vanityNode.getProperty(PROP_J_ACTIVE).getBoolean()
                && vanityNode.hasProperty(PROP_J_DEFAULT) && vanityNode.getProperty(PROP_J_DEFAULT).getBoolean();
    }

    private void updateVanityForMovedNode(JCRNodeWrapper node, String language, String existingVanityUrl, JCRSiteNode site, JCRSessionWrapper session) throws RepositoryException {
        if (isNodeSkipped(node, site)) return;

        RenderContext context = new RenderContext(null, null, node.getSession().getUser());
        context.setSite(site);
        if (!JCRContentUtils.isADisplayableNode(node, context) || node.isNodeType(NT_FILE)) return;

        String url = computeVanityUrlOnMove(node, language, site.getDefaultLanguage(), existingVanityUrl);
        if (url == null) return;

        PermalinkMode mode = getMode(site);
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

    // Recursive: process each node BEFORE its children so parent vanity is ready when child computes its prefix
    private void refreshChildrenRecursive(JCRNodeWrapper node, JCRSiteNode site) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.isNodeType(MIXIN_NAV_MENU_ITEM)) {
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

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, MIXIN_NAV_MENU_ITEM);

        // Prefer parent's existing active+default vanity as base
        if (!parents.isEmpty()) {
            String parentVanity = getActiveDefaultVanityUrl(parents.get(0), language);
            if (parentVanity != null) {
                return parentVanity + "/" + slug;
            }
        }

        // Fallback: rebuild full path from parent titles
        return buildUrlFromParentTitles(slug, parents, language, defaultLanguage);
    }

    /** On move: preserve existing slug, only update the parent prefix. */
    private String computeVanityUrlOnMove(JCRNodeWrapper node, String language, String defaultLanguage, String existingVanityUrl) throws RepositoryException {
        // Extract slug from existing vanity URL
        String slug = extractSlug(existingVanityUrl);
        // Fallback: compute from title if no existing slug
        if (slug == null) {
            String displayableName = node.getDisplayableName();
            if (displayableName == null) return null;
            slug = SLUGIFY.slugify(displayableName);
            if (slug.isEmpty()) return null;
        }

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, MIXIN_NAV_MENU_ITEM);
        if (!parents.isEmpty()) {
            String parentVanity = getActiveDefaultVanityUrl(parents.get(0), language);
            if (parentVanity != null) return parentVanity + "/" + slug;
        }

        // Fallback: rebuild prefix from parent titles
        return buildUrlFromParentTitles(slug, parents, language, defaultLanguage);
    }

    /**
     * Shared helper: builds a URL from parent node display names (slugified) plus the given slug,
     * with an optional language prefix when not the default language.
     *
     * <p>This is the fallback path used when no parent has an active+default vanity to use as base.
     * Used by {@link #computeVanityUrl}, {@link #computeVanityUrlOnMove}, and
     * {@link #computeVanityUrlCascade}.
     */
    private String buildUrlFromParentTitles(String slug, List<JCRNodeWrapper> parents, String language, String defaultLanguage) throws RepositoryException {
        StringBuilder url = new StringBuilder("/").append(slug);
        for (JCRNodeWrapper parent : parents) {
            // U8 fix: skip the ancestor only when it is *actually* the home page. The old check tested
            // property PRESENCE only, so an autocreated "j:isHomePage=false" on a normal page wrongly
            // dropped its title segment. Read the boolean value, consistent with isNodeSkipped (line 337).
            boolean parentIsHomePage = parent.hasProperty(PROP_J_IS_HOME_PAGE)
                    && parent.getProperty(PROP_J_IS_HOME_PAGE).getBoolean();
            if (!parentIsHomePage) {
                String parentName = parent.getDisplayableName();
                if (parentName != null) {
                    String parentSlug = SLUGIFY.slugify(parentName);
                    if (!parentSlug.isEmpty()) url.insert(0, "/" + parentSlug);
                }
            }
        }
        if (!language.equals(defaultLanguage)) {
            url.insert(0, "/" + language);
        }
        return url.toString();
    }

    /** Extracts the last path segment from a vanity URL, or {@code null} if none. */
    // package-private for testing
    static String extractSlug(String vanityUrl) {
        if (vanityUrl == null || vanityUrl.isEmpty()) return null;
        String last = vanityUrl.substring(vanityUrl.lastIndexOf('/') + 1);
        return last.isEmpty() ? null : last;
    }

    /**
     * Package-private bridge for unit tests: delegates to {@link #buildUrlFromParentTitles}
     * with an empty parent list (no JCR access required).
     */
    // package-private for testing
    String buildUrlFromParentTitlesForTest(String slug, List<JCRNodeWrapper> parents,
                                           String language, String defaultLanguage) throws RepositoryException {
        return buildUrlFromParentTitles(slug, parents, language, defaultLanguage);
    }

    /**
     * Package-private setter used by unit tests to inject a mock {@link VanityUrlManager}
     * without requiring an OSGi container.
     */
    // package-private for testing
    void setVanityUrlManager(VanityUrlManager manager) {
        this.vanityUrlManager = manager;
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
            if (!node.hasNode(PROP_VANITY_MAPPING)) return false;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue; // auto-generated, skip
                if (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) continue;
                if (v.hasProperty(PROP_J_ACTIVE) && v.getProperty(PROP_J_ACTIVE).getBoolean()
                        && v.hasProperty(PROP_J_DEFAULT) && v.getProperty(PROP_J_DEFAULT).getBoolean()) {
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
            if (!node.hasNode(PROP_VANITY_MAPPING)) return null;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.hasProperty(PROP_JCR_LANGUAGE) && language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())
                        && v.hasProperty(PROP_J_ACTIVE) && v.getProperty(PROP_J_ACTIVE).getBoolean()
                        && v.hasProperty(PROP_J_DEFAULT) && v.getProperty(PROP_J_DEFAULT).getBoolean()) {
                    return v.getProperty(PROP_J_URL).getString();
                }
            }
        } catch (RepositoryException e) {
            logger.debug("Could not read parent vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return null;
    }

    /** Try baseUrl, then baseUrl-2 ... baseUrl-N until a free slot is found. */
    private String resolveUniqueUrl(String baseUrl, JCRNodeWrapper node, String siteKey, JCRSessionWrapper session) throws RepositoryException {
        String url = baseUrl;
        for (int attempt = 1; attempt <= MAX_URL_ATTEMPTS; attempt++) {
            List<VanityUrl> conflict = vanityUrlManager.findExistingVanityUrls(url, siteKey, session);
            if (conflict == null || conflict.isEmpty()) return url;
            // Idempotent: conflict on THIS node — URL is already correct
            if (conflictIsOnSameNode(conflict, node)) return url;
            url = baseUrl + "-" + (attempt + 1);
        }
        logger.warn("No unique URL found for {} after {} attempts, skipping", node.getPath(), MAX_URL_ATTEMPTS);
        return null;
    }

    /**
     * Returns true if ALL conflicting vanity entries point back to the given node.
     * VanityUrl.getIdentifier() returns the content-node UUID directly — no parent navigation needed.
     * An entry with a null identifier is treated as belonging to a different node (conservative).
     */
    private boolean conflictIsOnSameNode(List<VanityUrl> conflict, JCRNodeWrapper node) throws RepositoryException {
        boolean allSame = true;
        for (VanityUrl v : conflict) {
            if (v.getIdentifier() == null) {
                allSame = false;
                break;
            }
            if (!node.getIdentifier().equals(v.getIdentifier())) {
                allSame = false;
                break;
            }
        }
        return allSame;
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
        if (!contentNode.hasNode(PROP_VANITY_MAPPING)) return;
        JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null);
        try {
            List<JCRNodeWrapper> toProcess = collectAutoGeneratedVanities(contentNode, language);
            for (JCRNodeWrapper vanityNode : toProcess) {
                try {
                    boolean publishedInLive = isPublishedInLive(vanityNode, liveSession);
                    if (publishedInLive) {
                        // Keep active as redirect — do NOT set j:active=false, old URL must not 404
                        vanityNode.setProperty(PROP_J_DEFAULT, false);
                    } else {
                        vanityNode.remove();
                    }
                } catch (Exception e) {
                    logger.debug("Could not rotate vanity {}: {}", vanityNode.getPath(), e.getMessage());
                }
            }
            if (!toProcess.isEmpty()) session.save();
        } finally {
            liveSession.logout();
        }
    }

    /**
     * On deletion: deactivate published (both {@code j:active=false} and {@code j:default=false}),
     * delete unpublished.
     *
     * <p>Contract (U7/D7): unlike rename/move — where the content still exists at a new path so the old
     * vanity is kept as an active redirect ({@link #removeAutoGeneratedVanities}) — a deleted page has no
     * live target to redirect to. The vanity is therefore fully deactivated and its old URL returns 404.
     * This is intentional: a redirect to a removed page would be a dangling mapping. The README documents
     * this delete-specific 404 behaviour.</p>
     */
    private void cleanAllAutoGeneratedVanities(JCRNodeWrapper contentNode, JCRSessionWrapper session) throws RepositoryException {
        if (!contentNode.hasNode(PROP_VANITY_MAPPING)) return;
        JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null);
        try {
            List<JCRNodeWrapper> toProcess = collectAutoGeneratedVanities(contentNode, null);
            for (JCRNodeWrapper vanityNode : toProcess) {
                try {
                    if (isPublishedInLive(vanityNode, liveSession)) {
                        vanityNode.setProperty(PROP_J_ACTIVE, false);
                        vanityNode.setProperty(PROP_J_DEFAULT, false);
                    } else {
                        vanityNode.remove();
                    }
                } catch (Exception e) {
                    logger.debug("Could not clean vanity on delete {}: {}", vanityNode.getPath(), e.getMessage());
                }
            }
            if (!toProcess.isEmpty()) session.save();
        } finally {
            liveSession.logout();
        }
    }

    /** Collect auto-generated vanity nodes for a given language (null = all languages). */
    private List<JCRNodeWrapper> collectAutoGeneratedVanities(JCRNodeWrapper contentNode, String language) throws RepositoryException {
        List<JCRNodeWrapper> result = new ArrayList<>();
        if (!contentNode.hasNode(PROP_VANITY_MAPPING)) return result;
        NodeIterator it = contentNode.getNode(PROP_VANITY_MAPPING).getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
            if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
            if (language != null && (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString()))) continue;
            result.add(v);
        }
        return result;
    }

    /**
     * Returns {@code true} if the vanity is published in live workspace.
     * Returns {@code false} when the node is not found (safely not published).
     * Returns {@code true} (preserve — do not delete) on any other {@link RepositoryException}
     * to avoid destroying a published redirect due to a transient error.
     */
    private boolean isPublishedInLive(JCRNodeWrapper vanityNode, JCRSessionWrapper liveSession) {
        try {
            liveSession.getNodeByIdentifier(vanityNode.getIdentifier());
            return true;
        } catch (ItemNotFoundException e) {
            return false;
        } catch (RepositoryException e) {
            logger.warn("Could not verify live status for vanity {} — treating as published to preserve redirect: {}",
                    vanityNode.getPath(), e.getMessage());
            return true;
        }
    }

    /**
     * Saves a vanity URL mapping and atomically adds the {@code jmix:permalinkGenerated} mixin
     * in a single JCR save to prevent re-entrancy with the Drools rule
     * "Mark vanity as manual when editor changes its URL".
     */
    private void saveVanityWithMixin(JCRNodeWrapper contentNode, VanityUrl vanityUrl, JCRSessionWrapper session) throws RepositoryException, NonUniqueUrlMappingException {
        vanityUrlManager.saveVanityUrlMapping(contentNode, vanityUrl, session);
        addMixinToSavedVanity(contentNode, vanityUrl);
    }

    /**
     * After {@code saveVanityUrlMapping}, locate the new vanity node and add the mixin.
     * If the mixin is already present (idempotent re-call), nothing changes.
     * The save is done in one call covering the mixin addition — this closes the
     * create-then-separate-save window that could trigger re-entrancy.
     */
    private void addMixinToSavedVanity(JCRNodeWrapper contentNode, VanityUrl vanityUrl) throws RepositoryException {
        if (!contentNode.hasNode(PROP_VANITY_MAPPING)) return;
        NodeIterator it = contentNode.getNode(PROP_VANITY_MAPPING).getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
            if (!v.hasProperty(PROP_J_URL) || !vanityUrl.getUrl().equals(v.getProperty(PROP_J_URL).getString())) continue;
            if (!v.hasProperty(PROP_JCR_LANGUAGE) || !vanityUrl.getLanguage().equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) continue;
            if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) {
                v.addMixin(MIXIN_PERMALINK_GENERATED);
                // Single save: mixin addition is flushed in the same transaction as the vanity creation.
                // The session is a system session, so the re-entrancy guard in removePermalinkMixin
                // will no-op if the Drools rule fires on this write.
                v.getSession().save();
            }
            break;
        }
    }

    private void unmarkForDeletion(JCRNodeWrapper node, JCRSessionWrapper session) throws RepositoryException {
        boolean changed = false;
        if (node.isNodeType(MIXIN_MARKED_FOR_DELETION_ROOT)) { node.removeMixin(MIXIN_MARKED_FOR_DELETION_ROOT); changed = true; }
        if (node.isNodeType(MIXIN_MARKED_FOR_DELETION))    { node.removeMixin(MIXIN_MARKED_FOR_DELETION);    changed = true; }
        if (changed) {
            session.save();
            logger.debug("Unmarked for deletion: {}", node.getPath());
        }
    }

    /** Returns the auto-generated vanity node with the given URL for this node/language, or null. */
    private JCRNodeWrapper findAutoGeneratedVanity(JCRNodeWrapper node, String language, String url) {
        try {
            if (!node.hasNode(PROP_VANITY_MAPPING)) return null;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) continue;
                if (v.hasProperty(PROP_J_URL) && url.equals(v.getProperty(PROP_J_URL).getString())) return v;
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
        if (contentNode.hasNode(PROP_VANITY_MAPPING)) {
            NodeIterator it = contentNode.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.getIdentifier().equals(vanityToPromote.getIdentifier())) continue;
                if (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) continue;
                if (v.hasProperty(PROP_J_DEFAULT) && v.getProperty(PROP_J_DEFAULT).getBoolean()) {
                    v.setProperty(PROP_J_DEFAULT, false);
                }
            }
        }
        vanityToPromote.setProperty(PROP_J_ACTIVE, true);
        vanityToPromote.setProperty(PROP_J_DEFAULT, true);
        session.save();
    }

    private boolean hasAutoGeneratedActiveDefaultVanity(JCRNodeWrapper node, String language, String url) {
        try {
            if (!node.hasNode(PROP_VANITY_MAPPING)) return false;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (isMatchingAutoGeneratedActiveDefault(v, language, url)) return true;
            }
        } catch (RepositoryException e) {
            logger.debug("Could not check auto vanity for {}: {}", node.getPath(), e.getMessage());
        }
        return false;
    }

    /**
     * Returns true if the vanity node is auto-generated, not marked for deletion,
     * matches the given language and URL, and is active+default.
     * Extracted from {@link #hasAutoGeneratedActiveDefaultVanity} to reduce cognitive complexity.
     */
    private boolean isMatchingAutoGeneratedActiveDefault(JCRNodeWrapper v, String language, String url) throws RepositoryException {
        if (!v.isNodeType(MIXIN_PERMALINK_GENERATED)) return false;
        if (v.isNodeType(MIXIN_MARKED_FOR_DELETION)) return false;
        if (!v.hasProperty(PROP_JCR_LANGUAGE) || !language.equals(v.getProperty(PROP_JCR_LANGUAGE).getString())) return false;
        if (!v.hasProperty(PROP_J_URL) || !url.equals(v.getProperty(PROP_J_URL).getString())) return false;
        return v.hasProperty(PROP_J_ACTIVE) && v.getProperty(PROP_J_ACTIVE).getBoolean()
                && v.hasProperty(PROP_J_DEFAULT) && v.getProperty(PROP_J_DEFAULT).getBoolean();
    }

    private boolean isExcludedPath(JCRNodeWrapper node, JCRSiteNode site) {
        try {
            if (!site.isNodeType("jmix:permalinkGeneratorSettings") || !site.hasProperty("j:excludedPaths")) return false;
            Value[] values = site.getProperty("j:excludedPaths").getValues();
            String nodePath = node.getPath();
            for (Value v : values) {
                String excluded = v.getString().trim();
                // Normalise a trailing slash so "/home/blog/" and "/home/blog" behave identically.
                if (excluded.endsWith("/")) excluded = excluded.substring(0, excluded.length() - 1);
                // U4 fix: match on a path-segment boundary, not a raw prefix. A raw startsWith made
                // "/home/blog" wrongly exclude siblings like "/home/blog-archive" and "/home/blogging".
                if (!excluded.isEmpty()
                        && (nodePath.equals(excluded) || nodePath.startsWith(excluded + "/"))) return true;
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
    @SuppressWarnings("java:S1172") // session IS used: passed to sortNodeIdsByDepth for path-depth resolution
    public List<Map<String, String>> previewVanityForNodeIds(
            List<String> nodeIds, List<String> languages,
            JCRSessionWrapper session, boolean bypassExcluded) {

        List<Map<String, String>> results = new ArrayList<>();
        if (nodeIds.isEmpty() || languages.isEmpty()) return results;

        // Sort shallow-first so parents are in the cache before children are evaluated
        List<String> sortedIds = sortNodeIdsByDepth(nodeIds, session);

        PreviewCaches caches = new PreviewCaches(new HashMap<>(), new HashMap<>(), new HashMap<>());

        for (String language : languages) {
            JCRSessionWrapper langSession = openLangSession(language);
            if (langSession == null) continue;
            JCRSiteNode[] siteHolder = { null };
            String[] defaultLangHolder = { null };

            for (String nodeId : sortedIds) {
                try {
                    JCRNodeWrapper node = langSession.getNodeByIdentifier(nodeId);
                    if (siteHolder[0] == null) {
                        siteHolder[0] = node.getResolveSite();
                        if (siteHolder[0] == null || !siteHolder[0].getInstalledModules().contains(MODULE_NAME)) break;
                        defaultLangHolder[0] = siteHolder[0].getDefaultLanguage();
                    }
                    Map<String, String> entry = buildPreviewEntry(
                            node, nodeId, language, defaultLangHolder[0], siteHolder[0],
                            bypassExcluded, caches);
                    if (entry != null) results.add(entry);
                } catch (Exception e) {
                    logger.warn("Could not preview vanity for node {} lang {}: {}", nodeId, language, e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * Evaluates one node×language preview entry. Returns {@code null} when the node should be skipped.
     * Even when skipping, the node's current vanities are loaded into {@code caches.currentVanity} so
     * that descendants see a consistent picture of their parent's state (fix: issue #12).
     * Extracted from {@link #previewVanityForNodeIds} to reduce cognitive complexity.
     */
    private Map<String, String> buildPreviewEntry(
            JCRNodeWrapper node, String nodeId, String language, String defaultLang,
            JCRSiteNode site, boolean bypassExcluded,
            PreviewCaches caches) throws RepositoryException {

        // Always populate the currentVanity cache before any early return, so that
        // descendant nodes can see this node's current URLs even when this node is skipped.
        caches.currentVanity.computeIfAbsent(nodeId, k -> loadCurrentVanitiesForNode(node));

        if (node.hasProperty(PROP_J_IS_HOME_PAGE) && node.getProperty(PROP_J_IS_HOME_PAGE).getBoolean()) return null;
        if (node.isNodeType(MIXIN_PERMALINK_EXCLUDED)) return null;
        if (!bypassExcluded && isExcludedPath(node, site)) return null;

        RenderContext ctx = new RenderContext(null, null, node.getSession().getUser());
        ctx.setSite(site);
        if (!JCRContentUtils.isADisplayableNode(node, ctx) || node.isNodeType(NT_FILE)) return null;

        String computedUrl = computeVanityUrlCascade(node, language, defaultLang, caches.computed, caches.currentVanity);

        // Cache computed URL so descendants can reuse the parent's new value
        if (computedUrl != null) {
            caches.computed.computeIfAbsent(nodeId, k -> new HashMap<>()).put(language, computedUrl);
        }

        // currentVanity already populated at method entry (before early-return checks)
        Map<String, String> nodeVanities = caches.currentVanity.get(nodeId);
        Set<String> manualLangs = caches.manualLang.computeIfAbsent(nodeId,
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
        return entry;
    }

    public List<Map<String, String>> generateVanityForNodeIds(List<String> nodeIds, List<String> languages, JCRSessionWrapper session) {
        return generateVanityForNodeIds(nodeIds, languages, session, false);
    }

    /**
     * U1 fix — per-node/per-site authorization re-check on the CALLER's own (ACL-bound) session.
     *
     * <p>The bulk mutation ({@link #generateVanityForNodeIds}) resolves and writes every node through
     * an elevated <em>system</em> session that bypasses JCR ACLs, and the only per-node gate is
     * {@code isSiteModuleEnabled} ({@link #processGenerateNode}). The site-level permission the action
     * declares is evaluated by the dispatcher against a single URL-resolved node only, so it does NOT
     * constrain which sites' nodes actually get mutated. A site-A-scoped admin could therefore submit
     * site-B {@code nodeIds} and overwrite/demote site B's editorial vanities.</p>
     *
     * <p>This method closes that hole: for every requested node it re-checks, on the <em>caller's</em>
     * session, that the caller holds {@code siteAdminPermalinkGenerator} at that node's site
     * ({@code /sites/<siteKey>}). Callers that lack the permission — or cannot even read the node on
     * their own session — fail closed. Callers must reject the whole request (HTTP 403) when this
     * returns a non-empty list, before invoking {@link #generateVanityForNodeIds}.</p>
     *
     * @param nodeIds       the UUIDs the caller asked to process
     * @param callerSession the caller's ACL-bound JCR session (NOT a system session)
     * @return the sub-list of {@code nodeIds} the caller is NOT authorized to administer
     *         (empty = every node authorized)
     */
    public List<String> findUnauthorizedNodeIds(List<String> nodeIds, JCRSessionWrapper callerSession) {
        List<String> denied = new ArrayList<>();
        if (nodeIds == null) return denied;
        for (String nodeId : nodeIds) {
            if (!callerCanAdministerNodeSite(nodeId, callerSession)) {
                denied.add(nodeId);
            }
        }
        return denied;
    }

    /**
     * Returns {@code true} only if the caller holds {@link #PERMISSION_SITE_ADMIN} at the site of the
     * given node, evaluated on the caller's own session. Any repository failure (node not readable by
     * the caller, site unresolvable, etc.) fails closed to {@code false}.
     */
    private boolean callerCanAdministerNodeSite(String nodeId, JCRSessionWrapper callerSession) {
        try {
            JCRNodeWrapper node = callerSession.getNodeByIdentifier(nodeId);
            JCRSiteNode site = node.getResolveSite();
            if (site == null) return false;
            JCRNodeWrapper siteNode = callerSession.getNode(SITES_PREFIX + site.getSiteKey());
            return siteNode.hasPermission(PERMISSION_SITE_ADMIN);
        } catch (RepositoryException e) {
            logger.warn("Per-node authorization re-check failed for node {} — denying: {}", nodeId, e.getMessage());
            return false;
        }
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

        // Depth-sort: parents before children -> child reads parent's freshly-saved URL
        List<String> sortedIds = sortNodeIdsByDepth(nodeIds, session);

        for (String language : languages) {
            JCRSessionWrapper langSession = openLangSession(language);
            if (langSession == null) continue;
            for (String nodeId : sortedIds) {
                try {
                    Map<String, String> entry = processGenerateNode(nodeId, language, langSession, forceRegen);
                    if (entry != null) results.add(entry);
                } catch (Exception e) {
                    logger.warn("Could not generate vanity for node {} lang {}: {}", nodeId, language, e.getMessage(), e);
                }
            }
        }
        return results;
    }

    /**
     * Processes one node×language in the generate bulk pass. Returns a result map entry when a
     * vanity operation was performed, or {@code null} when skipped.
     * Extracted from {@link #generateVanityForNodeIds} to reduce cognitive complexity.
     */
    private Map<String, String> processGenerateNode(String nodeId, String language,
                                                    JCRSessionWrapper langSession, boolean forceRegen) throws RepositoryException {
        JCRNodeWrapper node = langSession.getNodeByIdentifier(nodeId);
        JCRSiteNode site = node.getResolveSite();
        if (site == null || !site.getInstalledModules().contains(MODULE_NAME)) {
            logger.info("Skip {} [{}]: site null or module not installed", nodeId, language);
            return null;
        }
        logger.info("generateVanityForNodeIds: processing {} [{}] path={} force={}", nodeId, language, node.getPath(), forceRegen);
        VanityOp op = updateVanityForNode(node, language, site, langSession, forceRegen);
        if (op == null) return null;

        logger.info("[permalink-admin] {} [{}]: {} -> {}{}",
                node.getPath(), language, op.action, op.url,
                (op.oldUrl != null) ? " (was: " + op.oldUrl + ")" : "");
        Map<String, String> entry = new HashMap<>();
        entry.put("uuid",     nodeId);
        entry.put("path",     node.getPath());
        entry.put("language", language);
        entry.put("action",   op.action);
        entry.put("url",      op.url != null ? op.url : "");
        entry.put("oldUrl",   op.oldUrl != null ? op.oldUrl : "");
        return entry;
    }

    // -------------------------------------------------------------------------
    // Bulk optimisation helpers
    // -------------------------------------------------------------------------

    /**
     * Opens a locale-specific system session, returning {@code null} (and logging a warning)
     * on failure.
     */
    private JCRSessionWrapper openLangSession(String language) {
        try {
            return JCRSessionFactory.getInstance()
                    .getCurrentSystemSession(Constants.EDIT_WORKSPACE, Locale.forLanguageTag(language), null);
        } catch (RepositoryException e) {
            logger.warn("Could not open session for language {}: {}", language, e.getMessage());
            return null;
        }
    }

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
     * Returns a lang->url map covering every language that has an active default vanity.
     * Result is cached by callers so this scan runs at most once per node per bulk call.
     */
    private Map<String, String> loadCurrentVanitiesForNode(JCRNodeWrapper node) {
        Map<String, String> result = new HashMap<>();
        try {
            if (!node.hasNode(PROP_VANITY_MAPPING)) return result;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (!v.hasProperty(PROP_JCR_LANGUAGE)) continue;
                if (!v.hasProperty(PROP_J_ACTIVE) || !v.getProperty(PROP_J_ACTIVE).getBoolean()) continue;
                if (!v.hasProperty(PROP_J_DEFAULT) || !v.getProperty(PROP_J_DEFAULT).getBoolean()) continue;
                String lang = v.getProperty(PROP_JCR_LANGUAGE).getString();
                if (v.hasProperty(PROP_J_URL)) result.put(lang, v.getProperty(PROP_J_URL).getString());
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
            if (!node.hasNode(PROP_VANITY_MAPPING)) return result;
            NodeIterator it = node.getNode(PROP_VANITY_MAPPING).getNodes();
            while (it.hasNext()) {
                JCRNodeWrapper v = (JCRNodeWrapper) it.nextNode();
                if (v.isNodeType(MIXIN_PERMALINK_GENERATED)) continue;
                if (!v.hasProperty(PROP_JCR_LANGUAGE)) continue;
                if (!v.hasProperty(PROP_J_ACTIVE) || !v.getProperty(PROP_J_ACTIVE).getBoolean()) continue;
                if (!v.hasProperty(PROP_J_DEFAULT) || !v.getProperty(PROP_J_DEFAULT).getBoolean()) continue;
                result.add(v.getProperty(PROP_JCR_LANGUAGE).getString());
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
     *                        across all languages (shared Map&lt;lang,url&gt; per node).
     *                        Falls back to this when parent is not (yet) in computedCache.
     */
    private String computeVanityUrlCascade(JCRNodeWrapper node, String language, String defaultLanguage,
            Map<String, Map<String, String>> computedCache,
            Map<String, Map<String, String>> currentVanityCache) throws RepositoryException {

        String displayableName = node.getDisplayableName();
        if (displayableName == null) return null;
        String slug = SLUGIFY.slugify(displayableName);
        if (slug.isEmpty()) return null;

        List<JCRNodeWrapper> parents = JCRTagUtils.getParentsOfType(node, MIXIN_NAV_MENU_ITEM);

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
        return buildUrlFromParentTitles(slug, parents, language, defaultLanguage);
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

    /** Bundles the three per-bulk-call caches used by {@link #buildPreviewEntry}. */
    private static final class PreviewCaches {
        final Map<String, Map<String, String>> computed;
        final Map<String, Map<String, String>> currentVanity;
        final Map<String, Set<String>> manualLang;

        PreviewCaches(Map<String, Map<String, String>> computed,
                      Map<String, Map<String, String>> currentVanity,
                      Map<String, Set<String>> manualLang) {
            this.computed = computed;
            this.currentVanity = currentVanity;
            this.manualLang = manualLang;
        }
    }
}
