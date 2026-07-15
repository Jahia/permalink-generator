package org.jahiacommunity.modules.permalinkgenerator.action;

import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.URLResolver;
import org.jahiacommunity.modules.permalinkgenerator.services.PermalinkGeneratorService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins the preview JSON response shape (gap G12 / spec S30, U10/D8): each entry exposes exactly
 * {@code uuid, language, computedUrl, currentUrl, willChange, isManual}; the internal {@code path}
 * key is dropped from the preview JSON, and {@code willChange}/{@code isManual} are booleans.
 */
class GeneratePermalinksActionPreviewShapeTest {

    private GeneratePermalinksAction action;
    private PermalinkGeneratorService serviceMock;
    private JCRSessionWrapper session;

    @BeforeEach
    void setUp() throws Exception {
        action = new GeneratePermalinksAction();
        serviceMock = mock(PermalinkGeneratorService.class);
        Field f = GeneratePermalinksAction.class.getDeclaredField("permalinkGeneratorService");
        f.setAccessible(true);
        f.set(action, serviceMock);
        session = mock(JCRSessionWrapper.class);
    }

    @Test
    @DisplayName("preview entry has the fixed 6-key shape and no 'path' key")
    void previewShape_dropsPathAndTypesBooleans() throws Exception {
        Map<String, String> row = new HashMap<>();
        row.put("uuid", "uuid-1");
        row.put("path", "/sites/mySite/home/page"); // internal only — must NOT leak into preview JSON
        row.put("language", "en");
        row.put("computedUrl", "/my-page");
        row.put("currentUrl", "/old-page");
        row.put("willChange", "true");
        row.put("isManual", "false");
        when(serviceMock.previewVanityForNodeIds(anyList(), anyList(), any(), anyBoolean()))
                .thenReturn(List.of(row));

        Map<String, List<String>> params = new HashMap<>();
        params.put("nodeIds[]", List.of("uuid-1"));
        params.put("languages[]", List.of("en"));
        params.put("preview", List.of("true"));

        ActionResult result = action.doExecute(mock(HttpServletRequest.class),
                mock(RenderContext.class), null, session, params, mock(URLResolver.class));

        JSONObject entry = result.getJson().getJSONArray("results").getJSONObject(0);
        assertThat(entry.keySet())
                .containsExactlyInAnyOrder("uuid", "language", "computedUrl", "currentUrl", "willChange", "isManual");
        assertThat(entry.has("path")).isFalse();
        assertThat(entry.get("willChange")).isInstanceOf(Boolean.class);
        assertThat(entry.get("isManual")).isInstanceOf(Boolean.class);
        assertThat(entry.getBoolean("willChange")).isTrue();
        assertThat(entry.getBoolean("isManual")).isFalse();
    }
}
