package org.jahiacommunity.modules.permalinkgenerator.services;

import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.VanityUrlManager;
import org.jahia.taglibs.jcr.node.JCRTagUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Static-mock unit tests for {@link PermalinkGeneratorService}, enabled by the newly-added
 * {@code mockito-inline} dependency ({@link MockedStatic} / {@link MockedConstruction}).
 *
 * <p>Covers gaps G4 (slug injection-safety), G11 (displayable gate), G9 (delete-vs-move
 * deactivation divergence, U7/D7), G8 ({@code force=true}/FORCE-mode overwrite effect, U9/D6/S12),
 * G13 (observable action tokens + dead {@code skipped}), G7 (conflict-cap silent drop, U5) and
 * G2 (no per-node permission re-check, U1 root cause).</p>
 */
class PermalinkGeneratorServiceStaticTest {

    private PermalinkGeneratorService service;
    private VanityUrlManager vanityUrlManager;

    @BeforeEach
    void setUp() {
        service = new PermalinkGeneratorService();
        vanityUrlManager = mock(VanityUrlManager.class);
        service.setVanityUrlManager(vanityUrlManager);
    }

    // =========================================================================
    // G4 — slug generation is injection-safe (U3)
    // =========================================================================

    @Nested
    @DisplayName("slug injection-safety (U3)")
    class SlugSafetyTest {

        /** Compute a vanity URL for a node whose displayable name is {@code title}, no parents. */
        private String computeUrl(String title) throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.getDisplayableName()).thenReturn(title);
            try (MockedStatic<JCRTagUtils> tags = mockStatic(JCRTagUtils.class)) {
                tags.when(() -> JCRTagUtils.getParentsOfType(any(), anyString()))
                        .thenReturn(Collections.emptyList());
                Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                        "computeVanityUrl", JCRNodeWrapper.class, String.class, String.class);
                m.setAccessible(true);
                return (String) m.invoke(service, node, "en", "en");
            }
        }

        private void assertSafeSegments(String url) {
            assertThat(url).isNotNull();
            // Only "/" separators plus [a-z0-9-] segments; no traversal, whitespace or empty segments.
            assertThat(url).matches("(/[a-z0-9-]+)+");
            assertThat(url).doesNotContain("..");
            assertThat(url).doesNotContain("//");
            assertThat(url).doesNotContain(" ");
        }

        @Test
        @DisplayName("path-traversal title '../../etc/passwd' -> safe single slug, no traversal")
        void pathTraversal_neutralized() throws Exception {
            assertSafeSegments(computeUrl("../../etc/passwd"));
        }

        @Test
        @DisplayName("embedded slashes 'a/b/c' -> single safe slug (no extra path segments)")
        void embeddedSlashes_neutralized() throws Exception {
            assertSafeSegments(computeUrl("a/b/c"));
        }

        @Test
        @DisplayName("accented 'héllo Wörld' -> ascii-folded safe slug")
        void accented_folded() throws Exception {
            assertSafeSegments(computeUrl("héllo Wörld"));
        }

        @Test
        @DisplayName("markup '  <script>  ' -> safe slug, no angle brackets")
        void markup_stripped() throws Exception {
            String url = computeUrl("  <script>  ");
            assertSafeSegments(url);
            assertThat(url).doesNotContain("<").doesNotContain(">");
        }

        @Test
        @DisplayName("all-symbol '////' -> empty slug -> null (node skipped, not a '/'-only URL)")
        void allSymbols_yieldsNullSkip() throws Exception {
            assertThat(computeUrl("////")).isNull();
        }

        @Test
        @DisplayName("non-transliterable CJK '日本語 タイトル' -> empty slug -> null (skipped)")
        void cjk_yieldsNullSkip() throws Exception {
            assertThat(computeUrl("日本語 タイトル")).isNull();
        }
    }

    // =========================================================================
    // G11 — isDisplayableNonFile displayable gate (D3)
    // =========================================================================

    @Nested
    @DisplayName("isDisplayableNonFile (D3 displayable gate)")
    class DisplayableGateTest {

        private boolean invoke(JCRNodeWrapper node, JCRSiteNode site, boolean displayable) throws Exception {
            when(node.getSession()).thenReturn(mock(JCRSessionWrapper.class));
            try (MockedStatic<JCRContentUtils> cu = mockStatic(JCRContentUtils.class);
                 MockedConstruction<RenderContext> rc = mockConstruction(RenderContext.class)) {
                cu.when(() -> JCRContentUtils.isADisplayableNode(any(), any())).thenReturn(displayable);
                Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                        "isDisplayableNonFile", JCRNodeWrapper.class, JCRSiteNode.class, String.class);
                m.setAccessible(true);
                return (boolean) m.invoke(service, node, site, "en");
            }
        }

        @Test
        @DisplayName("core says not displayable -> false")
        void notDisplayable_false() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            assertThat(invoke(node, mock(JCRSiteNode.class), false)).isFalse();
        }

        @Test
        @DisplayName("displayable and not a file -> true")
        void displayableNonFile_true() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.isNodeType("jnt:file")).thenReturn(false);
            assertThat(invoke(node, mock(JCRSiteNode.class), true)).isTrue();
        }

        @Test
        @DisplayName("displayable BUT jnt:file -> false")
        void displayableButFile_false() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.isNodeType("jnt:file")).thenReturn(true);
            assertThat(invoke(node, mock(JCRSiteNode.class), true)).isFalse();
        }
    }

    // =========================================================================
    // G9 — delete deactivates (active=false + default=false) vs move (default=false only)
    // =========================================================================

    @Nested
    @DisplayName("delete vs move vanity deactivation (U7/D7)")
    class DeactivationTest {

        private JCRNodeWrapper publishedAutoVanity() throws RepositoryException {
            JCRNodeWrapper v = mock(JCRNodeWrapper.class);
            when(v.getIdentifier()).thenReturn("v-1");
            when(v.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            when(v.hasProperty("jcr:language")).thenReturn(true);
            JCRPropertyWrapper lang = mock(JCRPropertyWrapper.class);
            when(v.getProperty("jcr:language")).thenReturn(lang);
            when(lang.getString()).thenReturn("en");
            return v;
        }

        private JCRNodeWrapper contentNodeWith(JCRNodeWrapper vanity) throws RepositoryException {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mapping = mock(JCRNodeWrapper.class);
            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mapping);
            when(mapping.getNodes()).thenAnswer(inv -> singleNodeIterator(vanity));
            return node;
        }

        @Test
        @DisplayName("DELETE (cleanAllAutoGeneratedVanities): published vanity -> active=false AND default=false")
        void delete_deactivatesActiveAndDefault() throws Exception {
            JCRNodeWrapper v = publishedAutoVanity();
            JCRNodeWrapper node = contentNodeWith(v);
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            try (MockedStatic<JCRSessionFactory> f = mockStatic(JCRSessionFactory.class)) {
                JCRSessionFactory factory = mock(JCRSessionFactory.class);
                JCRSessionWrapper live = mock(JCRSessionWrapper.class);
                f.when(JCRSessionFactory::getInstance).thenReturn(factory);
                when(factory.getCurrentSystemSession(anyString(), any(), any())).thenReturn(live);
                when(live.getNodeByIdentifier("v-1")).thenReturn(mock(JCRNodeWrapper.class)); // published

                Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                        "cleanAllAutoGeneratedVanities", JCRNodeWrapper.class, JCRSessionWrapper.class);
                m.setAccessible(true);
                m.invoke(service, node, session);
            }

            verify(v).setProperty("j:active", false);
            verify(v).setProperty("j:default", false);
        }

        @Test
        @DisplayName("MOVE (removeAutoGeneratedVanities): published vanity -> default=false ONLY, active untouched")
        void move_keepsActiveDemotesDefault() throws Exception {
            JCRNodeWrapper v = publishedAutoVanity();
            JCRNodeWrapper node = contentNodeWith(v);
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            try (MockedStatic<JCRSessionFactory> f = mockStatic(JCRSessionFactory.class)) {
                JCRSessionFactory factory = mock(JCRSessionFactory.class);
                JCRSessionWrapper live = mock(JCRSessionWrapper.class);
                f.when(JCRSessionFactory::getInstance).thenReturn(factory);
                when(factory.getCurrentSystemSession(anyString(), any(), any())).thenReturn(live);
                when(live.getNodeByIdentifier("v-1")).thenReturn(mock(JCRNodeWrapper.class)); // published

                Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                        "removeAutoGeneratedVanities", JCRNodeWrapper.class, String.class, JCRSessionWrapper.class);
                m.setAccessible(true);
                m.invoke(service, node, "en", session);
            }

            verify(v).setProperty("j:default", false);
            verify(v, never()).setProperty(eq("j:active"), anyBoolean());
        }
    }

    // =========================================================================
    // G8 / G13 / G7 — updateVanityForNode: force/FORCE overwrite, tokens, silent drop
    // =========================================================================

    @Nested
    @DisplayName("updateVanityForNode force/FORCE effect + tokens + silent drop")
    class UpdateVanityForNodeTest {

        /** Invoke the private 5-arg updateVanityForNode, opening all required static mocks. */
        private Object invoke(JCRNodeWrapper node, JCRSiteNode site, JCRSessionWrapper session, boolean force)
                throws Exception {
            try (MockedStatic<JCRContentUtils> cu = mockStatic(JCRContentUtils.class);
                 MockedStatic<JCRTagUtils> tags = mockStatic(JCRTagUtils.class);
                 MockedStatic<JCRSessionFactory> f = mockStatic(JCRSessionFactory.class);
                 MockedConstruction<RenderContext> rc = mockConstruction(RenderContext.class)) {
                cu.when(() -> JCRContentUtils.isADisplayableNode(any(), any())).thenReturn(true);
                tags.when(() -> JCRTagUtils.getParentsOfType(any(), anyString()))
                        .thenReturn(Collections.emptyList());
                JCRSessionFactory factory = mock(JCRSessionFactory.class);
                f.when(JCRSessionFactory::getInstance).thenReturn(factory);
                when(factory.getCurrentSystemSession(anyString(), any(), any()))
                        .thenReturn(mock(JCRSessionWrapper.class));

                Method m = PermalinkGeneratorService.class.getDeclaredMethod(
                        "updateVanityForNode", JCRNodeWrapper.class, String.class, JCRSiteNode.class,
                        JCRSessionWrapper.class, boolean.class);
                m.setAccessible(true);
                return m.invoke(service, node, "en", site, session, force);
            }
        }

        @Test
        @DisplayName("SMART + force=false + manual vanity present -> SKIP (guard, no write) [baseline]")
        void smartNoForce_manual_skips() throws Exception {
            JCRNodeWrapper node = processableNode("/custom-manual", VanityKind.MANUAL);
            JCRSiteNode site = site(null); // SMART default
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            Object op = invoke(node, site, session, false);

            assertThat(op).isNull();
            verify(vanityUrlManager, never()).saveVanityUrlMapping(any(), any(), any());
        }

        @Test
        @DisplayName("G8: force=true bypasses SMART manual guard -> writes a fresh vanity, reports old URL")
        void force_bypassesManualGuard_writes() throws Exception {
            JCRNodeWrapper node = processableNode("/custom-manual", VanityKind.MANUAL);
            JCRSiteNode site = site(null); // SMART
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            Object op = invoke(node, site, session, true);

            assertThat(op).isNotNull();
            assertThat(field(op, "action")).isEqualTo("created");
            assertThat(field(op, "url")).isEqualTo("/my-page");
            assertThat(field(op, "oldUrl")).isEqualTo("/custom-manual"); // demoted old URL reported
            verify(vanityUrlManager).saveVanityUrlMapping(any(), any(), any());
        }

        @Test
        @DisplayName("S12/G20: FORCE mode (force flag false) also bypasses the SMART guard -> writes")
        void forceMode_noFlag_bypassesGuard_writes() throws Exception {
            JCRNodeWrapper node = processableNode("/custom-manual", VanityKind.MANUAL);
            JCRSiteNode site = site("FORCE");
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            Object op = invoke(node, site, session, false);

            assertThat(op).isNotNull();
            assertThat(field(op, "action")).isEqualTo("created");
            assertThat(field(op, "oldUrl")).isEqualTo("/custom-manual");
            verify(vanityUrlManager).saveVanityUrlMapping(any(), any(), any());
        }

        @Test
        @DisplayName("G13: existing correct auto vanity -> 'already_correct' (idempotent, no write)")
        void alreadyCorrect_token() throws Exception {
            JCRNodeWrapper node = processableNode("/my-page", VanityKind.AUTO_CORRECT);
            JCRSiteNode site = site(null);
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            Object op = invoke(node, site, session, false);

            assertThat(op).isNotNull();
            assertThat(field(op, "action")).isEqualTo("already_correct");
            verify(vanityUrlManager, never()).saveVanityUrlMapping(any(), any(), any());
        }

        @Test
        @DisplayName("G7/U5: conflict-cap exhausted -> node silently dropped (null, no write, no error)")
        void conflictCapExhausted_silentDrop() throws Exception {
            JCRNodeWrapper node = processableNode(null, VanityKind.NONE);
            JCRSiteNode site = site(null);
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            // Every candidate URL conflicts with a different (null-id) node -> resolveUniqueUrl returns null.
            VanityUrl conflict = new VanityUrl("/my-page", "mySite", "en"); // identifier null
            when(vanityUrlManager.findExistingVanityUrls(anyString(), eq("mySite"), any()))
                    .thenReturn(List.of(conflict));

            Object op = invoke(node, site, session, false);

            assertThat(op).isNull(); // silently dropped
            verify(vanityUrlManager, never()).saveVanityUrlMapping(any(), any(), any());
        }
    }

    // =========================================================================
    // G2 — service performs NO per-node permission re-check (U1 root cause)
    // =========================================================================

    @Nested
    @DisplayName("generateVanityForNodeIds per-node authz (U1 characterization)")
    class PerNodeAuthzTest {

        @Test
        @DisplayName("only per-node gate is isSiteModuleEnabled; no JCRNodeWrapper.hasPermission call")
        void noPerNodePermissionCheck() throws Exception {
            String nodeId = "node-1";
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);

            // sortNodeIdsByDepth uses the passed-in session
            JCRNodeWrapper depthNode = mock(JCRNodeWrapper.class);
            when(depthNode.getPath()).thenReturn("/sites/mySite/home/p");
            when(session.getNodeByIdentifier(nodeId)).thenReturn(depthNode);

            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRSiteNode site = mock(JCRSiteNode.class);
            when(node.getResolveSite()).thenReturn(site);
            when(site.getInstalledModules()).thenReturn(List.of("permalink-generator"));
            // isNodeSkipped gates
            when(node.hasProperty("j:isHomePage")).thenReturn(false);
            when(node.isNodeType("jmix:permalinkExcluded")).thenReturn(false);
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(false);
            when(node.isNodeType("jnt:file")).thenReturn(false);
            when(node.getSession()).thenReturn(mock(JCRSessionWrapper.class));
            when(node.getPath()).thenReturn("/sites/mySite/home/p");
            // Null displayable name -> updateVanityForNode bails at computeVanityUrl (op null),
            // so the node is "processed" and gated ONLY by isSiteModuleEnabled.
            when(node.getDisplayableName()).thenReturn(null);

            try (MockedStatic<JCRContentUtils> cu = mockStatic(JCRContentUtils.class);
                 MockedStatic<JCRTagUtils> tags = mockStatic(JCRTagUtils.class);
                 MockedStatic<JCRSessionFactory> f = mockStatic(JCRSessionFactory.class);
                 MockedConstruction<RenderContext> rc = mockConstruction(RenderContext.class)) {
                cu.when(() -> JCRContentUtils.isADisplayableNode(any(), any())).thenReturn(true);
                tags.when(() -> JCRTagUtils.getParentsOfType(any(), anyString()))
                        .thenReturn(Collections.emptyList());
                JCRSessionFactory factory = mock(JCRSessionFactory.class);
                JCRSessionWrapper langSession = mock(JCRSessionWrapper.class);
                f.when(JCRSessionFactory::getInstance).thenReturn(factory);
                when(factory.getCurrentSystemSession(anyString(), any(), any())).thenReturn(langSession);
                when(langSession.getNodeByIdentifier(nodeId)).thenReturn(node);

                List<?> results = service.generateVanityForNodeIds(
                        List.of(nodeId), List.of("en"), session, false);

                assertThat(results).isEmpty();
            }

            // The site-module gate is the ONLY per-node authorization; no ACL/permission re-check.
            verify(site, atLeastOnce()).getInstalledModules();
            verify(node, never()).hasPermission(anyString());
        }
    }

    // =========================================================================
    // Shared fixtures
    // =========================================================================

    private enum VanityKind { NONE, MANUAL, AUTO_CORRECT }

    /**
     * A node that passes skip/displayable gates and slugifies to {@code /my-page}.
     * Its vanityUrlMapping holds a single vanity of the requested kind (or none).
     */
    private JCRNodeWrapper processableNode(String existingUrl, VanityKind kind) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getIdentifier()).thenReturn("content-1");
        when(node.getPath()).thenReturn("/sites/mySite/home/page");
        when(node.getDisplayableName()).thenReturn(kind == VanityKind.NONE ? "My Page" : "My Page");
        when(node.hasProperty("j:isHomePage")).thenReturn(false);
        when(node.isNodeType("jmix:permalinkExcluded")).thenReturn(false);
        when(node.isNodeType("jnt:file")).thenReturn(false);
        when(node.getSession()).thenReturn(mock(JCRSessionWrapper.class));

        if (kind == VanityKind.NONE) {
            when(node.hasNode("vanityUrlMapping")).thenReturn(false);
            return node;
        }

        JCRNodeWrapper mapping = mock(JCRNodeWrapper.class);
        when(node.hasNode("vanityUrlMapping")).thenReturn(true);
        when(node.getNode("vanityUrlMapping")).thenReturn(mapping);

        JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
        when(vanity.getIdentifier()).thenReturn("vanity-1");
        boolean auto = kind == VanityKind.AUTO_CORRECT;
        when(vanity.isNodeType("jmix:permalinkGenerated")).thenReturn(auto);
        when(vanity.isNodeType("jmix:markedForDeletion")).thenReturn(false);
        stubProp(vanity, "jcr:language", "en");
        stubBool(vanity, "j:active", true);
        stubBool(vanity, "j:default", true);
        stubProp(vanity, "j:url", existingUrl);
        when(mapping.getNodes()).thenAnswer(inv -> singleNodeIterator(vanity));
        return node;
    }

    private JCRSiteNode site(String mode) throws RepositoryException {
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mySite");
        when(site.getDefaultLanguage()).thenReturn("en");
        when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(true);
        when(site.hasProperty("j:excludedPaths")).thenReturn(false);
        if (mode != null) {
            when(site.hasProperty("j:permalinkGeneratorMode")).thenReturn(true);
            JCRPropertyWrapper p = mock(JCRPropertyWrapper.class);
            when(site.getProperty("j:permalinkGeneratorMode")).thenReturn(p);
            when(p.getString()).thenReturn(mode);
        } else {
            when(site.hasProperty("j:permalinkGeneratorMode")).thenReturn(false);
        }
        return site;
    }

    private void stubProp(JCRNodeWrapper node, String name, String value) throws RepositoryException {
        if (value == null) {
            when(node.hasProperty(name)).thenReturn(false);
            return;
        }
        when(node.hasProperty(name)).thenReturn(true);
        JCRPropertyWrapper p = mock(JCRPropertyWrapper.class);
        when(node.getProperty(name)).thenReturn(p);
        when(p.getString()).thenReturn(value);
    }

    private void stubBool(JCRNodeWrapper node, String name, boolean value) throws RepositoryException {
        when(node.hasProperty(name)).thenReturn(true);
        JCRPropertyWrapper p = mock(JCRPropertyWrapper.class);
        when(node.getProperty(name)).thenReturn(p);
        when(p.getBoolean()).thenReturn(value);
    }

    private String field(Object vanityOp, String name) throws Exception {
        return String.valueOf(vanityOp.getClass().getField(name).get(vanityOp));
    }

    private JCRNodeIteratorWrapper singleNodeIterator(JCRNodeWrapper node) {
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenReturn(true, false);
        when(it.nextNode()).thenReturn(node);
        return it;
    }
}
