package org.jahiacommunity.modules.permalinkgenerator.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for audit finding PLG-SEC-001: the Site Settings view must not interpolate site
 * data into an inline script. {@code fn:escapeXml} is an HTML encoder: inside a JavaScript string it
 * leaves the backslash as is, so a stored {@code j:excludedPaths} value ending with {@code \} broke
 * out of its string literal. Site data is now written into HTML attributes and elements, where
 * {@code fn:escapeXml} is the right encoder, and the script reads it from the DOM.
 */
class SiteSettingsViewEncodingTest {

    private static final Path VIEW = Path.of(
            "src/main/resources/jnt_permalinkGeneratorSiteSettings/html/permalinkGeneratorSiteSettings.jsp");

    /** An inline script: a script element with no src attribute, and its body. */
    private static final Pattern INLINE_SCRIPT = Pattern.compile(
            "<script(?![^>]*\\bsrc\\s*=)[^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static List<String> inlineScripts() throws IOException {
        String jsp = Files.readString(VIEW, StandardCharsets.UTF_8);
        List<String> bodies = new ArrayList<>();
        Matcher m = INLINE_SCRIPT.matcher(jsp);
        while (m.find()) {
            bodies.add(m.group(1));
        }
        return bodies;
    }

    @Test
    @DisplayName("the view still has its inline configuration script")
    void viewHasInlineScript() throws IOException {
        assertThat(inlineScripts()).isNotEmpty();
    }

    @Test
    @DisplayName("no EL expression is interpolated inside an inline script")
    void noExpressionInsideInlineScript() throws IOException {
        for (String body : inlineScripts()) {
            assertThat(body).doesNotContain("${").doesNotContain("#{");
        }
    }

    @Test
    @DisplayName("every expression written into the page outside a script goes through fn:escapeXml")
    void everyOutputExpressionIsEscaped() throws IOException {
        String jsp = Files.readString(VIEW, StandardCharsets.UTF_8);
        assertThat(jsp).as("scriptlet output bypasses every encoder").doesNotContain("<%=");
        // Drop the inline scripts (checked above), JSP comments and directives, and the attributes of
        // JSTL / Jahia tags (items, test, value...), which are evaluated and never printed as is.
        String output = INLINE_SCRIPT.matcher(jsp).replaceAll("")
                .replaceAll("(?s)<%--.*?--%>", "")
                .replaceAll("(?s)<%@.*?%>", "")
                .replaceAll("(?s)</?(c|fmt|template):[^>]*>", "");
        Matcher expr = Pattern.compile("\\$\\{").matcher(output);
        while (expr.find()) {
            assertThat(output.substring(expr.start()))
                    .as("expression at offset %d is printed without fn:escapeXml", expr.start())
                    .startsWith("${fn:escapeXml(");
        }
    }

    @Test
    @DisplayName("j:excludedPaths values are rendered with fn:escapeXml outside any script")
    void excludedPathsRenderedInHtmlContext() throws IOException {
        String jsp = Files.readString(VIEW, StandardCharsets.UTF_8);
        String outsideScripts = INLINE_SCRIPT.matcher(jsp).replaceAll("");
        assertThat(outsideScripts).contains("j:excludedPaths").contains("fn:escapeXml(_ep.string)");
    }
}
