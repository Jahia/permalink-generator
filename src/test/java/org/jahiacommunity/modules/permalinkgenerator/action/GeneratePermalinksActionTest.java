package org.jahiacommunity.modules.permalinkgenerator.action;

import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.URLResolver;
import org.jahiacommunity.modules.permalinkgenerator.services.PermalinkGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GeneratePermalinksAction}.
 *
 * <p>Tests cover: SC_BAD_REQUEST on missing parameters, preview/generate routing,
 * force and bypassExcluded flag propagation, and 500 error body sanitisation
 * (A-SEC3: no raw exception message leaked to client).</p>
 *
 * <p>The {@link PermalinkGeneratorService} is injected via reflection so no
 * OSGi container is required.</p>
 */
class GeneratePermalinksActionTest {

    private GeneratePermalinksAction action;
    private PermalinkGeneratorService serviceMock;

    private HttpServletRequest request;
    private RenderContext renderContext;
    private JCRSessionWrapper session;
    private URLResolver urlResolver;

    @BeforeEach
    void setUp() throws Exception {
        action = new GeneratePermalinksAction();
        serviceMock = mock(PermalinkGeneratorService.class);
        injectService(action, serviceMock);

        request = mock(HttpServletRequest.class);
        renderContext = mock(RenderContext.class);
        session = mock(JCRSessionWrapper.class);
        urlResolver = mock(URLResolver.class);
    }

    // =========================================================================
    // SC_BAD_REQUEST — missing or empty required parameters
    // =========================================================================

    @Nested
    @DisplayName("SC_BAD_REQUEST — missing required parameters")
    class BadRequestTest {

        @Test
        @DisplayName("nodeIds[] absent -> 400")
        void missingNodeIds_returns400() throws Exception {
            Map<String, List<String>> params = new HashMap<>();
            params.put("languages[]", List.of("en"));

            ActionResult result = execute(params);
            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("nodeIds[] present but empty -> 400")
        void emptyNodeIds_returns400() throws Exception {
            Map<String, List<String>> params = new HashMap<>();
            params.put("nodeIds[]", Collections.emptyList());
            params.put("languages[]", List.of("en"));

            ActionResult result = execute(params);
            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("languages[] absent -> 400")
        void missingLanguages_returns400() throws Exception {
            Map<String, List<String>> params = new HashMap<>();
            params.put("nodeIds[]", List.of("uuid-1"));

            ActionResult result = execute(params);
            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("languages[] present but empty -> 400")
        void emptyLanguages_returns400() throws Exception {
            Map<String, List<String>> params = new HashMap<>();
            params.put("nodeIds[]", List.of("uuid-1"));
            params.put("languages[]", Collections.emptyList());

            ActionResult result = execute(params);
            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }

        @Test
        @DisplayName("both nodeIds[] and languages[] absent -> 400")
        void bothAbsent_returns400() throws Exception {
            ActionResult result = execute(new HashMap<>());
            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    // =========================================================================
    // Preview routing
    // =========================================================================

    @Nested
    @DisplayName("preview=true routes to previewVanityForNodeIds")
    class PreviewRoutingTest {

        @Test
        @DisplayName("preview=true -> calls previewVanityForNodeIds, NOT generate")
        void previewTrue_callsPreview() throws Exception {
            when(serviceMock.previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("preview", List.of("true"));

            ActionResult result = execute(params);

            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_OK);
            verify(serviceMock).previewVanityForNodeIds(anyList(), anyList(), eq(session), eq(false));
            verify(serviceMock, never()).generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean());
        }

        @Test
        @DisplayName("preview=true + bypassExcluded=true -> bypassExcluded forwarded as true")
        void previewWithBypassExcluded_forwarded() throws Exception {
            when(serviceMock.previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("preview", List.of("true"));
            params.put("bypassExcluded", List.of("true"));

            execute(params);

            verify(serviceMock).previewVanityForNodeIds(anyList(), anyList(), eq(session), eq(true));
        }

        @Test
        @DisplayName("preview=false -> calls generateVanityForNodeIds, NOT preview")
        void previewFalse_callsGenerate() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("preview", List.of("false"));

            execute(params);

            verify(serviceMock, never()).previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean());
            verify(serviceMock).generateVanityForNodeIds(anyList(), anyList(), eq(session), eq(false));
        }

        @Test
        @DisplayName("no preview param -> defaults to generate branch")
        void noPreviewParam_callsGenerate() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            execute(validParams());

            verify(serviceMock, never()).previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean());
            verify(serviceMock).generateVanityForNodeIds(anyList(), anyList(), eq(session), anyBoolean());
        }
    }

    // =========================================================================
    // force flag propagation
    // =========================================================================

    @Nested
    @DisplayName("force flag forwarded to generateVanityForNodeIds")
    class ForceFlagTest {

        @Test
        @DisplayName("force=true -> service called with force=true")
        void forceTrue_propagated() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("force", List.of("true"));

            execute(params);

            verify(serviceMock).generateVanityForNodeIds(anyList(), anyList(), eq(session), eq(true));
        }

        @Test
        @DisplayName("force=false -> service called with force=false")
        void forceFalse_propagated() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("force", List.of("false"));

            execute(params);

            verify(serviceMock).generateVanityForNodeIds(anyList(), anyList(), eq(session), eq(false));
        }

        @Test
        @DisplayName("no force param -> service called with force=false (default)")
        void noForceParam_defaultsFalse() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            execute(validParams());

            verify(serviceMock).generateVanityForNodeIds(anyList(), anyList(), eq(session), eq(false));
        }
    }

    // =========================================================================
    // Response shape and error sanitisation (A-SEC3)
    // =========================================================================

    @Nested
    @DisplayName("response shape and error sanitisation")
    class ResponseShapeTest {

        @Test
        @DisplayName("generate success -> 200 with 'results' key in JSON body")
        void generateSuccess_returns200WithResultsKey() throws Exception {
            Map<String, String> entry = new HashMap<>();
            entry.put("uuid", "uuid-1");
            entry.put("path", "/sites/mySite/home/page");
            entry.put("language", "en");
            entry.put("action", "created");
            entry.put("url", "/my-page");
            entry.put("oldUrl", "");
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(List.of(entry));

            ActionResult result = execute(validParams());

            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(result.getJson()).isNotNull();
            assertThat(result.getJson().has("results")).isTrue();
        }

        @Test
        @DisplayName("preview success -> 200 with JSON body")
        void previewSuccess_returns200() throws Exception {
            when(serviceMock.previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, List<String>> params = validParams();
            params.put("preview", List.of("true"));

            ActionResult result = execute(params);

            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(result.getJson()).isNotNull();
        }

        @Test
        @DisplayName("generate throws RuntimeException -> 500, raw message NOT in response (A-SEC3)")
        void generateThrows_returns500WithoutLeakingMessage() throws Exception {
            when(serviceMock.generateVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("DB connection lost: password=secret"));

            ActionResult result = execute(validParams());

            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(result.getJson()).isNotNull();
            assertThat(result.getJson().toString())
                    .doesNotContain("DB connection lost")
                    .doesNotContain("password=secret")
                    .contains("error");
        }

        @Test
        @DisplayName("preview throws RuntimeException -> 500, raw message NOT in response (A-SEC3)")
        void previewThrows_returns500WithoutLeakingMessage() throws Exception {
            when(serviceMock.previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("internal detail that must not leak"));

            Map<String, List<String>> params = validParams();
            params.put("preview", List.of("true"));

            ActionResult result = execute(params);

            assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(result.getJson().toString())
                    .doesNotContain("internal detail that must not leak")
                    .contains("error");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ActionResult execute(Map<String, List<String>> params) throws Exception {
        return action.doExecute(request, renderContext, null, session, params, urlResolver);
    }

    /** Minimal valid parameter map with one nodeId and one language. */
    private Map<String, List<String>> validParams() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("nodeIds[]", List.of("uuid-1"));
        params.put("languages[]", List.of("en"));
        return params;
    }

    /**
     * Injects mock service into the private {@code permalinkGeneratorService} field,
     * replacing the OSGi {@code @Reference} that is unavailable in unit tests.
     */
    private static void injectService(GeneratePermalinksAction action,
                                      PermalinkGeneratorService service) throws Exception {
        Field field = GeneratePermalinksAction.class.getDeclaredField("permalinkGeneratorService");
        field.setAccessible(true);
        field.set(action, service);
    }
}
