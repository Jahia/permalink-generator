package org.jahiacommunity.modules.permalinkgenerator.services;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure-Mockito / reflection unit tests for logic in {@link PermalinkGeneratorService}
 * that requires no static-collaborator mocking.
 *
 * <p>Covers gaps G6/G14 (excluded-path matching, incl. the {@code startsWith} over-match
 * characterization), G16 (mode fallback), G10 ({@code applyVanityUpdate} promote path),
 * and G15 (ancestor-fallback property-presence-vs-value latent bug U8).</p>
 */
class PermalinkGeneratorServiceLogicTest {

    private PermalinkGeneratorService service;
    private org.jahia.services.seo.jcr.VanityUrlManager vanityUrlManager;

    @BeforeEach
    void setUp() {
        service = new PermalinkGeneratorService();
        vanityUrlManager = mock(org.jahia.services.seo.jcr.VanityUrlManager.class);
        service.setVanityUrlManager(vanityUrlManager);
    }

    // =========================================================================
    // G6 / G14 — isExcludedPath: raw String.startsWith (U4/D5 over-match)
    // =========================================================================

    @Nested
    @DisplayName("isExcludedPath (U4/D5 characterization)")
    class IsExcludedPathTest {

        private JCRSiteNode siteWithExcluded(String... excludedPaths) throws RepositoryException {
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(true);
            when(site.hasProperty("j:excludedPaths")).thenReturn(true);
            JCRPropertyWrapper prop = mock(JCRPropertyWrapper.class);
            when(site.getProperty("j:excludedPaths")).thenReturn(prop);
            JCRValueWrapper[] values = new JCRValueWrapper[excludedPaths.length];
            for (int i = 0; i < excludedPaths.length; i++) {
                JCRValueWrapper v = mock(JCRValueWrapper.class);
                when(v.getString()).thenReturn(excludedPaths[i]);
                values[i] = v;
            }
            when(prop.getValues()).thenReturn(values);
            return site;
        }

        private boolean isExcluded(String nodePath, JCRSiteNode site) throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.getPath()).thenReturn(nodePath);
            Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "isExcludedPath", JCRNodeWrapper.class, JCRSiteNode.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, node, site);
        }

        @Test
        @DisplayName("G14: node exactly at an excluded path -> excluded")
        void exactPath_isExcluded() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog");
            assertThat(isExcluded("/sites/x/home/blog", site)).isTrue();
        }

        @Test
        @DisplayName("G14: node under an excluded subtree -> excluded")
        void subtreePath_isExcluded() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog");
            assertThat(isExcluded("/sites/x/home/blog/post-1", site)).isTrue();
        }

        @Test
        @DisplayName("U4 FIX: sibling '/blog-archive' is NOT excluded (segment-boundary match)")
        void siblingArchive_notOverMatched() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog");
            // After the Stage-7 segment-boundary fix, a sibling sharing a prefix is no longer over-excluded.
            assertThat(isExcluded("/sites/x/home/blog-archive", site)).isFalse();
        }

        @Test
        @DisplayName("U4 FIX: sibling '/blogging' is NOT excluded (segment-boundary match)")
        void siblingBlogging_notOverMatched() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog");
            assertThat(isExcluded("/sites/x/home/blogging", site)).isFalse();
        }

        @Test
        @DisplayName("U4 FIX: a configured trailing slash still matches the subtree")
        void trailingSlashConfig_stillMatchesSubtree() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog/");
            assertThat(isExcluded("/sites/x/home/blog", site)).isTrue();
            assertThat(isExcluded("/sites/x/home/blog/post-1", site)).isTrue();
            assertThat(isExcluded("/sites/x/home/blog-archive", site)).isFalse();
        }

        @Test
        @DisplayName("genuinely unrelated path -> not excluded")
        void unrelatedPath_notExcluded() throws Exception {
            JCRSiteNode site = siteWithExcluded("/sites/x/home/blog");
            assertThat(isExcluded("/sites/x/home/news", site)).isFalse();
        }

        @Test
        @DisplayName("site is not a settings node -> not excluded")
        void notSettingsNode_notExcluded() throws Exception {
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(false);
            assertThat(isExcluded("/sites/x/home/blog", site)).isFalse();
        }
    }

    // =========================================================================
    // G16 — getMode: unknown/empty/non-settings -> SMART fallback (U11)
    // =========================================================================

    @Nested
    @DisplayName("getMode (U11 safe fallback)")
    class GetModeTest {

        private String mode(JCRSiteNode site) throws Exception {
            Method m = PermalinkGeneratorService.class.getDeclaredMethod("getMode", JCRSiteNode.class);
            m.setAccessible(true);
            Object result = m.invoke(service, site);
            return String.valueOf(result);
        }

        private JCRSiteNode siteWithMode(String modeValue) throws RepositoryException {
            JCRSiteNode site = mock(JCRSiteNode.class);
            lenient().when(site.getSiteKey()).thenReturn("mySite");
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(true);
            when(site.hasProperty("j:permalinkGeneratorMode")).thenReturn(true);
            JCRPropertyWrapper prop = mock(JCRPropertyWrapper.class);
            when(site.getProperty("j:permalinkGeneratorMode")).thenReturn(prop);
            when(prop.getString()).thenReturn(modeValue);
            return site;
        }

        @Test
        @DisplayName("valid 'FORCE' -> FORCE")
        void forceValue_returnsForce() throws Exception {
            assertThat(mode(siteWithMode("FORCE"))).isEqualTo("FORCE");
        }

        @Test
        @DisplayName("valid 'SMART' -> SMART")
        void smartValue_returnsSmart() throws Exception {
            assertThat(mode(siteWithMode("SMART"))).isEqualTo("SMART");
        }

        @Test
        @DisplayName("garbage 'BOGUS' -> SMART (IllegalArgumentException swallowed)")
        void bogusValue_fallsBackToSmart() throws Exception {
            assertThat(mode(siteWithMode("BOGUS"))).isEqualTo("SMART");
        }

        @Test
        @DisplayName("empty string -> SMART")
        void emptyValue_fallsBackToSmart() throws Exception {
            assertThat(mode(siteWithMode(""))).isEqualTo("SMART");
        }

        @Test
        @DisplayName("site not a settings node -> SMART")
        void notSettingsNode_returnsSmart() throws Exception {
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(false);
            assertThat(mode(site)).isEqualTo("SMART");
        }
    }

    // =========================================================================
    // G15 — ancestor fallback skips parent by property PRESENCE, not value (U8)
    // =========================================================================

    @Nested
    @DisplayName("buildUrlFromParentTitles ancestor presence-not-value (U8)")
    class AncestorPresenceTest {

        @Test
        @DisplayName("U8 FIX: non-home parent with j:isHomePage PRESENT-but-false keeps its title segment")
        void parentWithPresentButFalseHomeFlag_isIncluded() throws Exception {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            // Property is present (autocreated) but its value is false -> the parent is NOT the home page.
            when(parent.hasProperty("j:isHomePage")).thenReturn(true);
            JCRPropertyWrapper homeProp = mock(JCRPropertyWrapper.class);
            when(parent.getProperty("j:isHomePage")).thenReturn(homeProp);
            when(homeProp.getBoolean()).thenReturn(false);
            when(parent.getDisplayableName()).thenReturn("Section");

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(parent), "en", "en");

            // U8 fix reads the boolean value: a present-but-false flag is a normal page, so its segment stays.
            assertThat(url).isEqualTo("/section/my-page");
        }

        @Test
        @DisplayName("U8: genuine home page (j:isHomePage present AND true) is still skipped")
        void parentThatIsActuallyHomePage_isSkipped() throws Exception {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(true);
            JCRPropertyWrapper homeProp = mock(JCRPropertyWrapper.class);
            when(parent.getProperty("j:isHomePage")).thenReturn(homeProp);
            when(homeProp.getBoolean()).thenReturn(true);

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(parent), "en", "en");

            assertThat(url).isEqualTo("/my-page");
        }
    }

    // =========================================================================
    // G10 — applyVanityUpdate re-promotes an existing (inactive) auto vanity -> "promoted"
    // =========================================================================

    @Nested
    @DisplayName("applyVanityUpdate (F17/D1 promote/undelete)")
    class ApplyVanityUpdateTest {

        @Test
        @DisplayName("existing inactive auto vanity matching computed URL -> unmark+promote, action='promoted'")
        void existingAutoVanity_isPromotedNotDuplicated() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(site.getSiteKey()).thenReturn("mySite");
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);

            // The auto-generated vanity that already carries the computed URL but is NOT active/default
            // and is marked-for-deletion -> should be re-promoted rather than recreated.
            JCRNodeWrapper existingAuto = mock(JCRNodeWrapper.class);
            when(existingAuto.getIdentifier()).thenReturn("auto-1");
            when(existingAuto.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            when(existingAuto.isNodeType("jmix:markedForDeletionRoot")).thenReturn(false);
            when(existingAuto.isNodeType("jmix:markedForDeletion")).thenReturn(true);
            when(existingAuto.hasProperty("jcr:language")).thenReturn(true);
            JCRPropertyWrapper lang = mock(JCRPropertyWrapper.class);
            when(existingAuto.getProperty("jcr:language")).thenReturn(lang);
            when(lang.getString()).thenReturn("en");
            when(existingAuto.hasProperty("j:url")).thenReturn(true);
            JCRPropertyWrapper urlProp = mock(JCRPropertyWrapper.class);
            when(existingAuto.getProperty("j:url")).thenReturn(urlProp);
            when(urlProp.getString()).thenReturn("/my-page");
            // Not currently active+default -> getActiveDefaultVanityUrl returns null (no oldUrl)
            when(existingAuto.hasProperty("j:active")).thenReturn(false);

            // Fresh iterator on every getNodes() call (getActiveDefaultVanityUrl, findAutoGeneratedVanity,
            // promoteVanityAsDefault each iterate the mapping).
            when(mappingNode.getNodes()).thenAnswer(inv -> singleNodeIterator(existingAuto));

            Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "applyVanityUpdate", JCRNodeWrapper.class, String.class, JCRSiteNode.class,
                    JCRSessionWrapper.class, String.class);
            m.setAccessible(true);
            Object op = m.invoke(service, node, "en", site, session, "/my-page");

            assertThat(op).isNotNull();
            assertThat(readField(op, "action")).isEqualTo("promoted");
            assertThat(readField(op, "url")).isEqualTo("/my-page");
            verify(existingAuto).removeMixin("jmix:markedForDeletion");
            verify(existingAuto).setProperty(eq("j:active"), eq(true));
            verify(existingAuto).setProperty(eq("j:default"), eq(true));
            // saveVanityUrlMapping (create-fresh path) must NOT be called for a promote.
            verify(vanityUrlManager, never())
                    .saveVanityUrlMapping(any(), any(), any());
        }

        private String readField(Object vanityOp, String field) throws Exception {
            return String.valueOf(vanityOp.getClass().getField(field).get(vanityOp));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private JCRNodeIteratorWrapper singleNodeIterator(JCRNodeWrapper node) {
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenReturn(true, false);
        when(it.nextNode()).thenReturn(node);
        return it;
    }
}
