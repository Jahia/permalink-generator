package org.jahiacommunity.modules.permalinkgenerator.services;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.VanityUrlManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PermalinkGeneratorService}.
 *
 * <p>Strategy: private logic is exercised through package-private bridges
 * ({@code extractSlug}, {@code buildUrlFromParentTitlesForTest}) or through
 * public entry points. External JCR / OSGi collaborators are replaced with
 * Mockito mocks. Jahia's concrete return types ({@code JCRPropertyWrapper},
 * {@code JCRNodeIteratorWrapper}) are used wherever the interface requires them.</p>
 */
class PermalinkGeneratorServiceTest {

    private PermalinkGeneratorService service;
    private VanityUrlManager vanityUrlManager;

    @BeforeEach
    void setUp() {
        service = new PermalinkGeneratorService();
        vanityUrlManager = mock(VanityUrlManager.class);
        service.setVanityUrlManager(vanityUrlManager);
    }

    // =========================================================================
    // extractSlug — pure static helper
    // =========================================================================

    @Nested
    @DisplayName("extractSlug")
    class ExtractSlugTest {

        @Test
        @DisplayName("extracts last segment from a multi-segment vanity URL")
        void extractSlug_normalUrl_returnsLastSegment() {
            assertThat(PermalinkGeneratorService.extractSlug("/parent/child/my-page"))
                    .isEqualTo("my-page");
        }

        @Test
        @DisplayName("extracts segment from single-segment URL")
        void extractSlug_singleSegment_returnsSegment() {
            assertThat(PermalinkGeneratorService.extractSlug("/my-page")).isEqualTo("my-page");
        }

        @Test
        @DisplayName("returns null for null input")
        void extractSlug_null_returnsNull() {
            assertThat(PermalinkGeneratorService.extractSlug(null)).isNull();
        }

        @Test
        @DisplayName("returns null for empty string")
        void extractSlug_empty_returnsNull() {
            assertThat(PermalinkGeneratorService.extractSlug("")).isNull();
        }

        @Test
        @DisplayName("returns null for trailing-slash URL (last segment is empty)")
        void extractSlug_trailingSlash_returnsNull() {
            assertThat(PermalinkGeneratorService.extractSlug("/parent/")).isNull();
        }

        @Test
        @DisplayName("handles slugified segment with dashes correctly")
        void extractSlug_dashedSegment_returnsDashedSlug() {
            assertThat(PermalinkGeneratorService.extractSlug("/fr/parent/mon-article"))
                    .isEqualTo("mon-article");
        }
    }

    // =========================================================================
    // buildUrlFromParentTitles — URL construction logic
    // =========================================================================

    @Nested
    @DisplayName("buildUrlFromParentTitles")
    class BuildUrlFromParentTitlesTest {

        @Test
        @DisplayName("no parents, default language -> /slug")
        void noParents_defaultLanguage_returnsSlashSlug() throws RepositoryException {
            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", Collections.emptyList(), "en", "en");
            assertThat(url).isEqualTo("/my-page");
        }

        @Test
        @DisplayName("no parents, non-default language -> /lang/slug")
        void noParents_nonDefaultLanguage_prependsLang() throws RepositoryException {
            String url = service.buildUrlFromParentTitlesForTest(
                    "mon-article", Collections.emptyList(), "fr", "en");
            assertThat(url).isEqualTo("/fr/mon-article");
        }

        @Test
        @DisplayName("one parent that is the homepage -> homepage skipped, result is /slug")
        void oneParentHomepage_isSkipped() throws RepositoryException {
            JCRNodeWrapper homePage = mock(JCRNodeWrapper.class);
            when(homePage.hasProperty("j:isHomePage")).thenReturn(true);
            JCRPropertyWrapper homeProp = mock(JCRPropertyWrapper.class);
            when(homePage.getProperty("j:isHomePage")).thenReturn(homeProp);
            when(homeProp.getBoolean()).thenReturn(true);

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(homePage), "en", "en");
            assertThat(url).isEqualTo("/my-page");
        }

        @Test
        @DisplayName("one non-homepage parent -> /parent-slug/slug")
        void oneNonHomepageParent_prependsParentSlug() throws RepositoryException {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(false);
            when(parent.getDisplayableName()).thenReturn("My Section");

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(parent), "en", "en");
            assertThat(url).isEqualTo("/my-section/my-page");
        }

        @Test
        @DisplayName("two parents (nearest first) -> grandparent-slug/parent-slug/slug")
        void twoParents_buildsFullPath() throws RepositoryException {
            // getParentsOfType returns nearest-ancestor first: [parent, grandparent]
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(false);
            when(parent.getDisplayableName()).thenReturn("Sub Section");

            JCRNodeWrapper grandparent = mock(JCRNodeWrapper.class);
            when(grandparent.hasProperty("j:isHomePage")).thenReturn(false);
            when(grandparent.getDisplayableName()).thenReturn("Grand Section");

            String url = service.buildUrlFromParentTitlesForTest(
                    "leaf-page", List.of(parent, grandparent), "en", "en");
            assertThat(url).isEqualTo("/grand-section/sub-section/leaf-page");
        }

        @Test
        @DisplayName("non-default language with parent -> /lang/parent-slug/slug")
        void nonDefaultLanguage_withParent_prependsLangFirst() throws RepositoryException {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(false);
            when(parent.getDisplayableName()).thenReturn("Section");

            String url = service.buildUrlFromParentTitlesForTest(
                    "page", List.of(parent), "de", "en");
            assertThat(url).isEqualTo("/de/section/page");
        }

        @Test
        @DisplayName("parent with null displayable name -> segment skipped")
        void parentWithNullDisplayableName_skipped() throws RepositoryException {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(false);
            when(parent.getDisplayableName()).thenReturn(null);

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(parent), "en", "en");
            assertThat(url).isEqualTo("/my-page");
        }

        @Test
        @DisplayName("parent whose display name slugifies to empty -> segment skipped")
        void parentEmptySlug_segmentSkipped() throws RepositoryException {
            JCRNodeWrapper parent = mock(JCRNodeWrapper.class);
            when(parent.hasProperty("j:isHomePage")).thenReturn(false);
            // A string of only characters that slugify reduces to ""
            when(parent.getDisplayableName()).thenReturn("---");

            String url = service.buildUrlFromParentTitlesForTest(
                    "my-page", List.of(parent), "en", "en");
            assertThat(url).isEqualTo("/my-page");
        }
    }

    // =========================================================================
    // resolveUniqueUrl — conflict resolution
    // =========================================================================

    @Nested
    @DisplayName("resolveUniqueUrl")
    class ResolveUniqueUrlTest {

        private JCRNodeWrapper node;
        private JCRSessionWrapper session;

        @BeforeEach
        void setUp() throws RepositoryException {
            node = mock(JCRNodeWrapper.class);
            session = mock(JCRSessionWrapper.class);
            when(node.getIdentifier()).thenReturn("node-uuid-1");
        }

        @Test
        @DisplayName("no conflict -> returns base URL unchanged")
        void noConflict_returnsBaseUrl() throws Exception {
            when(vanityUrlManager.findExistingVanityUrls("/my-page", "siteKey", session))
                    .thenReturn(Collections.emptyList());

            String result = invokeResolveUniqueUrl("/my-page", node, "siteKey", session);
            assertThat(result).isEqualTo("/my-page");
        }

        @Test
        @DisplayName("conflict on different node at first slot -> returns base-2")
        void conflictOnDifferentNode_returnsSuffixed() throws Exception {
            VanityUrl conflicting = makeVanityUrl("/my-page", "other-uuid");

            when(vanityUrlManager.findExistingVanityUrls("/my-page", "siteKey", session))
                    .thenReturn(List.of(conflicting));
            when(vanityUrlManager.findExistingVanityUrls("/my-page-2", "siteKey", session))
                    .thenReturn(Collections.emptyList());

            // conflictIsOnSameNode: resolve "other-uuid" -> parent -> parent -> different content node
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityParent = mock(JCRNodeWrapper.class);
            JCRNodeWrapper contentNode = mock(JCRNodeWrapper.class);
            when(session.getNodeByIdentifier("other-uuid")).thenReturn(vanityNode);
            when(vanityNode.getParent()).thenReturn(vanityParent);
            when(vanityParent.getParent()).thenReturn(contentNode);
            when(contentNode.getIdentifier()).thenReturn("other-content-uuid");

            String result = invokeResolveUniqueUrl("/my-page", node, "siteKey", session);
            assertThat(result).isEqualTo("/my-page-2");
        }

        @Test
        @DisplayName("conflict on SAME node (idempotent) -> returns base URL")
        void conflictOnSameNode_idempotent_returnsBaseUrl() throws Exception {
            VanityUrl selfConflict = makeVanityUrl("/my-page", "vanity-node-uuid");

            when(vanityUrlManager.findExistingVanityUrls("/my-page", "siteKey", session))
                    .thenReturn(List.of(selfConflict));

            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityParent = mock(JCRNodeWrapper.class);
            JCRNodeWrapper contentNode = mock(JCRNodeWrapper.class);
            when(session.getNodeByIdentifier("vanity-node-uuid")).thenReturn(vanityNode);
            when(vanityNode.getParent()).thenReturn(vanityParent);
            when(vanityParent.getParent()).thenReturn(contentNode);
            when(contentNode.getIdentifier()).thenReturn("node-uuid-1"); // same node -> idempotent

            String result = invokeResolveUniqueUrl("/my-page", node, "siteKey", session);
            assertThat(result).isEqualTo("/my-page");
        }

        @Test
        @DisplayName("all 10 attempts conflict on different nodes -> returns null")
        void maxAttemptsExhausted_returnsNull() throws Exception {
            for (int i = 1; i <= 10; i++) {
                String url = (i == 1) ? "/my-page" : "/my-page-" + i;
                VanityUrl conflict = makeVanityUrl(url, "other-uuid-" + i);
                when(vanityUrlManager.findExistingVanityUrls(url, "siteKey", session))
                        .thenReturn(List.of(conflict));

                JCRNodeWrapper vn = mock(JCRNodeWrapper.class);
                JCRNodeWrapper vp = mock(JCRNodeWrapper.class);
                JCRNodeWrapper cn = mock(JCRNodeWrapper.class);
                when(session.getNodeByIdentifier("other-uuid-" + i)).thenReturn(vn);
                when(vn.getParent()).thenReturn(vp);
                when(vp.getParent()).thenReturn(cn);
                when(cn.getIdentifier()).thenReturn("unrelated-" + i);
            }

            String result = invokeResolveUniqueUrl("/my-page", node, "siteKey", session);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("conflict entry with null identifier -> treated as not-same-node, advances to -2")
        void conflictWithNullIdentifier_advancesToSuffix() throws Exception {
            VanityUrl nullIdConflict = new VanityUrl("/my-page", "siteKey", "en");
            // identifier is null by default

            when(vanityUrlManager.findExistingVanityUrls("/my-page", "siteKey", session))
                    .thenReturn(List.of(nullIdConflict));
            when(vanityUrlManager.findExistingVanityUrls("/my-page-2", "siteKey", session))
                    .thenReturn(Collections.emptyList());

            String result = invokeResolveUniqueUrl("/my-page", node, "siteKey", session);
            assertThat(result).isEqualTo("/my-page-2");
        }

        private String invokeResolveUniqueUrl(String baseUrl, JCRNodeWrapper node,
                                              String siteKey, JCRSessionWrapper session) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "resolveUniqueUrl", String.class, JCRNodeWrapper.class,
                    String.class, JCRSessionWrapper.class);
            m.setAccessible(true);
            return (String) m.invoke(service, baseUrl, node, siteKey, session);
        }

        private VanityUrl makeVanityUrl(String url, String identifier) {
            VanityUrl v = new VanityUrl(url, "siteKey", "en");
            v.setIdentifier(identifier);
            return v;
        }
    }

    // =========================================================================
    // removePermalinkMixin — re-entrancy guard (regression test A-ARCH1)
    // =========================================================================

    @Nested
    @DisplayName("removePermalinkMixin")
    class RemovePermalinkMixinTest {

        @Test
        @DisplayName("system session write -> no-op, mixin NOT stripped (re-entrancy guard)")
        void systemSession_isNoOp() throws Exception {
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
            AddedNodeFact nodeFact = mock(AddedNodeFact.class);

            when(nodeFact.getNode()).thenReturn(vanityNode);
            when(vanityNode.getSession()).thenReturn(systemSession);
            when(systemSession.isSystem()).thenReturn(true);

            service.removePermalinkMixin(nodeFact, null);

            verify(vanityNode, never()).removeMixin(anyString());
            verify(systemSession, never()).save();
        }

        @Test
        @DisplayName("non-system session, node has mixin -> mixin stripped and session saved")
        void nonSystemSession_hasMixin_stripsMixin() throws Exception {
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRSessionWrapper userSession = mock(JCRSessionWrapper.class);
            AddedNodeFact nodeFact = mock(AddedNodeFact.class);

            when(nodeFact.getNode()).thenReturn(vanityNode);
            when(vanityNode.getSession()).thenReturn(userSession);
            when(userSession.isSystem()).thenReturn(false);
            when(vanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(true);

            service.removePermalinkMixin(nodeFact, null);

            verify(vanityNode).removeMixin("jmix:permalinkGenerated");
            verify(userSession).save();
        }

        @Test
        @DisplayName("non-system session, node does NOT have mixin -> no-op")
        void nonSystemSession_noMixin_noOp() throws Exception {
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRSessionWrapper userSession = mock(JCRSessionWrapper.class);
            AddedNodeFact nodeFact = mock(AddedNodeFact.class);

            when(nodeFact.getNode()).thenReturn(vanityNode);
            when(vanityNode.getSession()).thenReturn(userSession);
            when(userSession.isSystem()).thenReturn(false);
            when(vanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(false);

            service.removePermalinkMixin(nodeFact, null);

            verify(vanityNode, never()).removeMixin(anyString());
            verify(userSession, never()).save();
        }

        @Test
        @DisplayName("null vanity node -> no exception thrown")
        void nullVanityNode_noException() {
            AddedNodeFact nodeFact = mock(AddedNodeFact.class);
            when(nodeFact.getNode()).thenReturn(null);

            assertThatCode(() -> service.removePermalinkMixin(nodeFact, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("regression A-ARCH1: system-session write keeps mixin; editor write on mixin-bearing node removes it")
        void reEntrancyGuard_bothBranches_inSequence() throws Exception {
            // --- branch A: system-session write (module itself) must NOT strip the mixin ---
            JCRNodeWrapper systemVanityNode = mock(JCRNodeWrapper.class);
            JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
            AddedNodeFact systemFact = mock(AddedNodeFact.class);

            when(systemFact.getNode()).thenReturn(systemVanityNode);
            when(systemVanityNode.getSession()).thenReturn(systemSession);
            when(systemSession.isSystem()).thenReturn(true);

            service.removePermalinkMixin(systemFact, null);

            verify(systemVanityNode, never()).removeMixin(anyString());
            verify(systemSession, never()).save();

            // --- branch B: editor (non-system) session on a mixin-bearing node MUST strip it ---
            JCRNodeWrapper editorVanityNode = mock(JCRNodeWrapper.class);
            JCRSessionWrapper editorSession = mock(JCRSessionWrapper.class);
            AddedNodeFact editorFact = mock(AddedNodeFact.class);

            when(editorFact.getNode()).thenReturn(editorVanityNode);
            when(editorVanityNode.getSession()).thenReturn(editorSession);
            when(editorSession.isSystem()).thenReturn(false);
            when(editorVanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(true);

            service.removePermalinkMixin(editorFact, null);

            verify(editorVanityNode).removeMixin("jmix:permalinkGenerated");
            verify(editorSession).save();
        }
    }

    // =========================================================================
    // isNodeSkipped — homepage / excluded mixin / excluded path
    // =========================================================================

    @Nested
    @DisplayName("isNodeSkipped")
    class IsNodeSkippedTest {

        @Test
        @DisplayName("homepage node -> skipped")
        void homepage_isSkipped() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRSiteNode site = mock(JCRSiteNode.class);

            JCRPropertyWrapper homeProp = mock(JCRPropertyWrapper.class);
            when(node.hasProperty("j:isHomePage")).thenReturn(true);
            when(node.getProperty("j:isHomePage")).thenReturn(homeProp);
            when(homeProp.getBoolean()).thenReturn(true);

            assertThat(invokeIsNodeSkipped(node, site)).isTrue();
        }

        @Test
        @DisplayName("node with jmix:permalinkExcluded mixin -> skipped")
        void excludedMixin_isSkipped() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRSiteNode site = mock(JCRSiteNode.class);

            when(node.hasProperty("j:isHomePage")).thenReturn(false);
            when(node.isNodeType("jmix:permalinkExcluded")).thenReturn(true);

            assertThat(invokeIsNodeSkipped(node, site)).isTrue();
        }

        @Test
        @DisplayName("ordinary non-excluded node -> not skipped")
        void ordinaryNode_notSkipped() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRSiteNode site = mock(JCRSiteNode.class);

            when(node.hasProperty("j:isHomePage")).thenReturn(false);
            when(node.isNodeType("jmix:permalinkExcluded")).thenReturn(false);
            when(site.isNodeType("jmix:permalinkGeneratorSettings")).thenReturn(false);

            assertThat(invokeIsNodeSkipped(node, site)).isFalse();
        }

        private boolean invokeIsNodeSkipped(JCRNodeWrapper node, JCRSiteNode site) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "isNodeSkipped", JCRNodeWrapper.class, JCRSiteNode.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, node, site);
        }
    }

    // =========================================================================
    // hasManualActiveDefaultVanity — SMART mode guard
    // =========================================================================

    @Nested
    @DisplayName("hasManualActiveDefaultVanity")
    class HasManualActiveDefaultVanityTest {

        @Test
        @DisplayName("no vanityUrlMapping child node -> false")
        void noMappingNode_returnsFalse() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.hasNode("vanityUrlMapping")).thenReturn(false);

            assertThat(invokeHasManual(node, "en")).isFalse();
        }

        @Test
        @DisplayName("manual active+default vanity for queried language -> true")
        void manualActiveDefault_returnsTrue() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = singleNodeIterator(vanityNode);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);

            when(vanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(false);
            stubLanguage(vanityNode, "en");
            stubBoolean(vanityNode, "j:active", true);
            stubBoolean(vanityNode, "j:default", true);

            assertThat(invokeHasManual(node, "en")).isTrue();
        }

        @Test
        @DisplayName("only auto-generated vanity present -> false (auto-generated is skipped)")
        void onlyAutoGenerated_returnsFalse() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = singleNodeIterator(vanityNode);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);

            when(vanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(true);

            assertThat(invokeHasManual(node, "en")).isFalse();
        }

        @Test
        @DisplayName("manual vanity for different language -> false")
        void differentLanguage_returnsFalse() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = singleNodeIterator(vanityNode);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);

            when(vanityNode.isNodeType("jmix:permalinkGenerated")).thenReturn(false);
            stubLanguage(vanityNode, "fr");    // different language
            stubBoolean(vanityNode, "j:active", true);
            stubBoolean(vanityNode, "j:default", true);

            assertThat(invokeHasManual(node, "en")).isFalse();
        }

        private boolean invokeHasManual(JCRNodeWrapper node, String language) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "hasManualActiveDefaultVanity", JCRNodeWrapper.class, String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, node, language);
        }
    }

    // =========================================================================
    // collectAutoGeneratedVanities — filter logic
    // =========================================================================

    @Nested
    @DisplayName("collectAutoGeneratedVanities")
    class CollectAutoGeneratedVanitiesTest {

        @Test
        @DisplayName("no vanityUrlMapping node -> returns empty list")
        void noMappingNode_emptyList() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.hasNode("vanityUrlMapping")).thenReturn(false);

            List<JCRNodeWrapper> result = invokeCollect(node, "en");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("auto-generated vanity matching queried language -> included")
        void autoGeneratedMatchingLang_included() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = singleNodeIterator(vanity);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);
            when(vanity.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            stubLanguage(vanity, "en");

            List<JCRNodeWrapper> result = invokeCollect(node, "en");
            assertThat(result).containsExactly(vanity);
        }

        @Test
        @DisplayName("auto-generated for different language -> excluded when language filter given")
        void autoGeneratedDifferentLang_excluded() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = singleNodeIterator(vanity);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);
            when(vanity.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            stubLanguage(vanity, "fr");

            List<JCRNodeWrapper> result = invokeCollect(node, "en");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null language filter -> collects ALL auto-generated vanities across languages")
        void nullLanguageFilter_collectsAllLanguages() throws Exception {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            JCRNodeWrapper mappingNode = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityEn = mock(JCRNodeWrapper.class);
            JCRNodeWrapper vanityFr = mock(JCRNodeWrapper.class);
            JCRNodeIteratorWrapper it = twoNodeIterator(vanityEn, vanityFr);

            when(node.hasNode("vanityUrlMapping")).thenReturn(true);
            when(node.getNode("vanityUrlMapping")).thenReturn(mappingNode);
            when(mappingNode.getNodes()).thenReturn(it);
            when(vanityEn.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            when(vanityFr.isNodeType("jmix:permalinkGenerated")).thenReturn(true);
            stubLanguage(vanityEn, "en");
            stubLanguage(vanityFr, "fr");

            List<JCRNodeWrapper> result = invokeCollect(node, null);
            assertThat(result).containsExactlyInAnyOrder(vanityEn, vanityFr);
        }

        @SuppressWarnings("unchecked")
        private List<JCRNodeWrapper> invokeCollect(JCRNodeWrapper node, String language) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "collectAutoGeneratedVanities", JCRNodeWrapper.class, String.class);
            m.setAccessible(true);
            return (List<JCRNodeWrapper>) m.invoke(service, node, language);
        }
    }

    // =========================================================================
    // isPublishedInLive — live-workspace lookup
    // =========================================================================

    @Nested
    @DisplayName("isPublishedInLive")
    class IsPublishedInLiveTest {

        @Test
        @DisplayName("node found in live session -> true")
        void foundInLive_returnsTrue() throws Exception {
            JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
            JCRSessionWrapper liveSession = mock(JCRSessionWrapper.class);
            when(vanity.getIdentifier()).thenReturn("vanity-uuid");
            when(liveSession.getNodeByIdentifier("vanity-uuid"))
                    .thenReturn(mock(JCRNodeWrapper.class));

            assertThat(invokeIsPublished(vanity, liveSession)).isTrue();
        }

        @Test
        @DisplayName("ItemNotFoundException from live session -> false (not published)")
        void notFoundInLive_returnsFalse() throws Exception {
            JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
            JCRSessionWrapper liveSession = mock(JCRSessionWrapper.class);
            when(vanity.getIdentifier()).thenReturn("vanity-uuid");
            when(liveSession.getNodeByIdentifier("vanity-uuid"))
                    .thenThrow(new javax.jcr.ItemNotFoundException("not found"));

            assertThat(invokeIsPublished(vanity, liveSession)).isFalse();
        }

        @Test
        @DisplayName("other RepositoryException -> true (preserve redirect on transient error)")
        void otherRepositoryException_preservesRedirect() throws Exception {
            JCRNodeWrapper vanity = mock(JCRNodeWrapper.class);
            JCRSessionWrapper liveSession = mock(JCRSessionWrapper.class);
            when(vanity.getIdentifier()).thenReturn("vanity-uuid");
            when(liveSession.getNodeByIdentifier("vanity-uuid"))
                    .thenThrow(new RepositoryException("transient error"));

            assertThat(invokeIsPublished(vanity, liveSession)).isTrue();
        }

        private boolean invokeIsPublished(JCRNodeWrapper vanity, JCRSessionWrapper liveSession) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "isPublishedInLive", JCRNodeWrapper.class, JCRSessionWrapper.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, vanity, liveSession);
        }
    }

    // =========================================================================
    // isActiveDefault — vanity property check
    // =========================================================================

    @Nested
    @DisplayName("isActiveDefault")
    class IsActiveDefaultTest {

        @Test
        @DisplayName("j:active=true AND j:default=true -> true")
        void bothTrue_returnsTrue() throws Exception {
            JCRNodeWrapper v = mock(JCRNodeWrapper.class);
            stubBoolean(v, "j:active", true);
            stubBoolean(v, "j:default", true);
            assertThat(invokeIsActiveDefault(v)).isTrue();
        }

        @Test
        @DisplayName("j:active=true, j:default=false -> false")
        void activeNotDefault_returnsFalse() throws Exception {
            JCRNodeWrapper v = mock(JCRNodeWrapper.class);
            stubBoolean(v, "j:active", true);
            stubBoolean(v, "j:default", false);
            assertThat(invokeIsActiveDefault(v)).isFalse();
        }

        @Test
        @DisplayName("j:active property missing -> false")
        void activeMissing_returnsFalse() throws Exception {
            JCRNodeWrapper v = mock(JCRNodeWrapper.class);
            when(v.hasProperty("j:active")).thenReturn(false);
            assertThat(invokeIsActiveDefault(v)).isFalse();
        }

        private boolean invokeIsActiveDefault(JCRNodeWrapper v) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "isActiveDefault", JCRNodeWrapper.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, v);
        }
    }

    // =========================================================================
    // isSiteModuleEnabled — path parsing + module check
    // =========================================================================

    @Nested
    @DisplayName("isSiteModuleEnabled")
    class IsSiteModuleEnabledTest {

        @Test
        @DisplayName("non-/sites/ path -> true (let caller decide)")
        void nonSitePath_returnsTrue() throws Exception {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            assertThat(invokeIsSiteModuleEnabled("/modules/permalink-generator", session))
                    .isTrue();
        }

        @Test
        @DisplayName("/sites/ path with module installed -> true")
        void sitePath_moduleInstalled_returnsTrue() throws Exception {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            JCRSiteNode siteNode = mock(JCRSiteNode.class);
            when(session.getNode("/sites/mySite")).thenReturn(siteNode);
            when(siteNode.getInstalledModules())
                    .thenReturn(List.of("permalink-generator", "default"));

            assertThat(invokeIsSiteModuleEnabled("/sites/mySite/home", session)).isTrue();
        }

        @Test
        @DisplayName("/sites/ path without module -> false")
        void sitePath_moduleNotInstalled_returnsFalse() throws Exception {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            JCRSiteNode siteNode = mock(JCRSiteNode.class);
            when(session.getNode("/sites/mySite")).thenReturn(siteNode);
            when(siteNode.getInstalledModules()).thenReturn(List.of("default"));

            assertThat(invokeIsSiteModuleEnabled("/sites/mySite/home", session)).isFalse();
        }

        @Test
        @DisplayName("RepositoryException accessing site node -> false (safe default)")
        void repositoryException_returnsFalse() throws Exception {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            when(session.getNode(anyString())).thenThrow(new RepositoryException("gone"));

            assertThat(invokeIsSiteModuleEnabled("/sites/brokenSite/page", session)).isFalse();
        }

        private boolean invokeIsSiteModuleEnabled(String path, JCRSessionWrapper session) throws Exception {
            var m = PermalinkGeneratorService.class.getDeclaredMethod(
                    "isSiteModuleEnabled", String.class, JCRSessionWrapper.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, path, session);
        }
    }

    // =========================================================================
    // Bulk API — empty-input guards
    // =========================================================================

    @Nested
    @DisplayName("bulk API — empty input fast-return")
    class BulkEmptyInputTest {

        @Test
        @DisplayName("previewVanityForNodeIds with empty nodeIds -> empty list")
        void preview_emptyNodeIds_returnsEmptyList() {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            assertThat(service.previewVanityForNodeIds(
                    Collections.emptyList(), List.of("en"), session, false)).isEmpty();
        }

        @Test
        @DisplayName("previewVanityForNodeIds with empty languages -> empty list")
        void preview_emptyLanguages_returnsEmptyList() {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            assertThat(service.previewVanityForNodeIds(
                    List.of("uuid1"), Collections.emptyList(), session, false)).isEmpty();
        }

        @Test
        @DisplayName("generateVanityForNodeIds with empty nodeIds -> empty list")
        void generate_emptyNodeIds_returnsEmptyList() {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            assertThat(service.generateVanityForNodeIds(
                    Collections.emptyList(), List.of("en"), session, false)).isEmpty();
        }

        @Test
        @DisplayName("generateVanityForNodeIds with empty languages -> empty list")
        void generate_emptyLanguages_returnsEmptyList() {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            assertThat(service.generateVanityForNodeIds(
                    List.of("uuid1"), Collections.emptyList(), session, false)).isEmpty();
        }
    }

    // =========================================================================
    // Shared test helpers
    // =========================================================================

    private JCRNodeIteratorWrapper singleNodeIterator(JCRNodeWrapper node) {
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenReturn(true, false);
        when(it.nextNode()).thenReturn(node);
        return it;
    }

    private JCRNodeIteratorWrapper twoNodeIterator(JCRNodeWrapper first, JCRNodeWrapper second) {
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenReturn(true, true, false);
        when(it.nextNode()).thenReturn(first, second);
        return it;
    }

    private void stubLanguage(JCRNodeWrapper vanity, String lang) throws RepositoryException {
        JCRPropertyWrapper langProp = mock(JCRPropertyWrapper.class);
        when(vanity.hasProperty("jcr:language")).thenReturn(true);
        when(vanity.getProperty("jcr:language")).thenReturn(langProp);
        when(langProp.getString()).thenReturn(lang);
    }

    private void stubBoolean(JCRNodeWrapper node, String propName, boolean value) throws RepositoryException {
        JCRPropertyWrapper prop = mock(JCRPropertyWrapper.class);
        when(node.hasProperty(propName)).thenReturn(true);
        when(node.getProperty(propName)).thenReturn(prop);
        when(prop.getBoolean()).thenReturn(value);
    }
}
