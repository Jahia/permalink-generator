package org.jahiacommunity.modules.permalinkgenerator.services;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.VanityUrlManager;
import org.jahia.taglibs.jcr.node.JCRTagUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression tests for audit finding PLG-SEC-002: when a page with a manual vanity URL moves (or an
 * ancestor title changes in SMART mode), the module rewrites the URL to {@code <parent vanity>/<slug>}.
 * That rewrite must not take a URL that another page of the site already uses: it goes through the
 * same uniqueness resolution as the automatic branch ({@code resolveUniqueUrl}).
 */
class ManualVanityUniquenessTest {

    private static final String SITE_KEY = "siteKey";
    private static final String LANG = "en";

    private PermalinkGeneratorService service;
    private VanityUrlManager vanityUrlManager;
    private JCRSessionWrapper session;
    private JCRSiteNode site;
    private JCRNodeWrapper movedPage;
    private JCRNodeWrapper manualVanity;
    private JCRNodeWrapper newParent;

    @BeforeEach
    void setUp() throws RepositoryException {
        service = new PermalinkGeneratorService();
        vanityUrlManager = mock(VanityUrlManager.class);
        service.setVanityUrlManager(vanityUrlManager);
        session = mock(JCRSessionWrapper.class);

        site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn(SITE_KEY);
        when(site.getDefaultLanguage()).thenReturn(LANG);

        // The editor's page Q carries the manual vanity /drafts/pricing; it is moved under a parent
        // whose vanity is /products, where page P already owns /products/pricing.
        manualVanity = vanity("/drafts/pricing", true);
        movedPage = contentNode("page-q", manualVanity);
        newParent = contentNode("parent", vanity("/products", false));
    }

    @Test
    @DisplayName("a rewritten manual URL that another page owns gets the next free suffix, not the other page's URL")
    void rewrittenUrlTakenByAnotherPage_getsUniqueSuffix() throws Exception {
        conflictOnOtherPage("/products/pricing");
        when(vanityUrlManager.findExistingVanityUrls("/products/pricing-2", SITE_KEY, session))
                .thenReturn(Collections.emptyList());

        updateManualVanityPrefix();

        verify(manualVanity, never()).setProperty("j:url", "/products/pricing");
        verify(manualVanity).setProperty("j:url", "/products/pricing-2");
    }

    @Test
    @DisplayName("a free rewritten URL keeps the editor's slug under the new prefix")
    void freeRewrittenUrl_keepsSlug() throws Exception {
        when(vanityUrlManager.findExistingVanityUrls("/products/pricing", SITE_KEY, session))
                .thenReturn(Collections.emptyList());

        updateManualVanityPrefix();

        verify(manualVanity).setProperty("j:url", "/products/pricing");
    }

    @Test
    @DisplayName("when no free URL exists, the manual vanity keeps its current URL")
    void noFreeUrl_keepsCurrentUrl() throws Exception {
        VanityUrl taken = otherPageVanity("/products/pricing");
        when(vanityUrlManager.findExistingVanityUrls(anyString(), eq(SITE_KEY), eq(session)))
                .thenReturn(List.of(taken));
        resolvesToOtherPage(taken);

        updateManualVanityPrefix();

        verify(manualVanity, never()).setProperty(eq("j:url"), anyString());
    }

    @Test
    @DisplayName("a URL held by this page AND by another page is not free: the other page keeps it")
    void urlHeldByThisPageAndAnotherPage_isNotFree() throws Exception {
        VanityUrl own = new VanityUrl("/products/pricing", SITE_KEY, LANG);
        own.setIdentifier("vanity-of-page-q");
        JCRNodeWrapper ownVanityNode = mock(JCRNodeWrapper.class);
        JCRNodeWrapper ownMapping = mock(JCRNodeWrapper.class);
        when(session.getNodeByIdentifier("vanity-of-page-q")).thenReturn(ownVanityNode);
        when(ownVanityNode.getParent()).thenReturn(ownMapping);
        when(ownMapping.getParent()).thenReturn(movedPage);
        VanityUrl taken = otherPageVanity("/products/pricing");
        resolvesToOtherPage(taken);
        when(vanityUrlManager.findExistingVanityUrls("/products/pricing", SITE_KEY, session))
                .thenReturn(List.of(own, taken));
        when(vanityUrlManager.findExistingVanityUrls("/products/pricing-2", SITE_KEY, session))
                .thenReturn(Collections.emptyList());

        updateManualVanityPrefix();

        verify(manualVanity, never()).setProperty("j:url", "/products/pricing");
        verify(manualVanity).setProperty("j:url", "/products/pricing-2");
    }

    @Test
    @DisplayName("a URL held only by this page is kept as is, with no suffix")
    void urlHeldOnlyByThisPage_isKept() throws Exception {
        VanityUrl own = new VanityUrl("/products/pricing", SITE_KEY, LANG);
        own.setIdentifier("vanity-of-page-q");
        JCRNodeWrapper ownVanityNode = mock(JCRNodeWrapper.class);
        JCRNodeWrapper ownMapping = mock(JCRNodeWrapper.class);
        when(session.getNodeByIdentifier("vanity-of-page-q")).thenReturn(ownVanityNode);
        when(ownVanityNode.getParent()).thenReturn(ownMapping);
        when(ownMapping.getParent()).thenReturn(movedPage);
        when(vanityUrlManager.findExistingVanityUrls("/products/pricing", SITE_KEY, session))
                .thenReturn(List.of(own));

        updateManualVanityPrefix();

        verify(manualVanity).setProperty("j:url", "/products/pricing");
    }

    // -------------------------------------------------------------------------

    private void updateManualVanityPrefix() throws Exception {
        try (MockedStatic<JCRTagUtils> tags = mockStatic(JCRTagUtils.class)) {
            tags.when(() -> JCRTagUtils.getParentsOfType(any(), anyString())).thenReturn(List.of(newParent));
            Method m = PermalinkGeneratorService.class.getDeclaredMethod("updateManualVanityPrefix",
                    JCRNodeWrapper.class, String.class, JCRSiteNode.class, JCRSessionWrapper.class);
            m.setAccessible(true);
            m.invoke(service, movedPage, LANG, site, session);
        }
    }

    private void conflictOnOtherPage(String url) throws RepositoryException {
        VanityUrl taken = otherPageVanity(url);
        when(vanityUrlManager.findExistingVanityUrls(url, SITE_KEY, session)).thenReturn(List.of(taken));
        resolvesToOtherPage(taken);
    }

    private VanityUrl otherPageVanity(String url) {
        VanityUrl v = new VanityUrl(url, SITE_KEY, LANG);
        v.setIdentifier("vanity-of-page-p");
        return v;
    }

    /** The conflicting mapping belongs to page P (vanity node -> vanityUrlMapping -> page P). */
    private void resolvesToOtherPage(VanityUrl taken) throws RepositoryException {
        JCRNodeWrapper vanityNode = mock(JCRNodeWrapper.class);
        JCRNodeWrapper mapping = mock(JCRNodeWrapper.class);
        JCRNodeWrapper pageP = mock(JCRNodeWrapper.class);
        when(session.getNodeByIdentifier(taken.getIdentifier())).thenReturn(vanityNode);
        when(vanityNode.getParent()).thenReturn(mapping);
        when(mapping.getParent()).thenReturn(pageP);
        when(pageP.getIdentifier()).thenReturn("page-p");
    }

    private JCRNodeWrapper vanity(String url, boolean manual) throws RepositoryException {
        JCRNodeWrapper v = mock(JCRNodeWrapper.class);
        when(v.isNodeType("jmix:permalinkGenerated")).thenReturn(!manual);
        for (String p : List.of("jcr:language", "j:active", "j:default", "j:url")) {
            when(v.hasProperty(p)).thenReturn(true);
        }
        JCRPropertyWrapper lang = mock(JCRPropertyWrapper.class);
        when(lang.getString()).thenReturn(LANG);
        when(v.getProperty("jcr:language")).thenReturn(lang);
        JCRPropertyWrapper yes = mock(JCRPropertyWrapper.class);
        when(yes.getBoolean()).thenReturn(true);
        when(v.getProperty("j:active")).thenReturn(yes);
        when(v.getProperty("j:default")).thenReturn(yes);
        JCRPropertyWrapper urlProp = mock(JCRPropertyWrapper.class);
        when(urlProp.getString()).thenReturn(url);
        when(v.getProperty("j:url")).thenReturn(urlProp);
        return v;
    }

    private JCRNodeWrapper contentNode(String id, JCRNodeWrapper vanity) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRNodeWrapper mapping = mock(JCRNodeWrapper.class);
        when(node.getIdentifier()).thenReturn(id);
        when(node.hasNode("vanityUrlMapping")).thenReturn(true);
        when(node.getNode("vanityUrlMapping")).thenReturn(mapping);
        when(mapping.getNodes()).thenAnswer(inv -> singleNodeIterator(vanity));
        return node;
    }

    private static JCRNodeIteratorWrapper singleNodeIterator(JCRNodeWrapper node) {
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenReturn(true, false);
        when(it.nextNode()).thenReturn(node);
        return it;
    }
}
