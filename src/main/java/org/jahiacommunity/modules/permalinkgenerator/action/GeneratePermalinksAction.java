package org.jahiacommunity.modules.permalinkgenerator.action;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahiacommunity.modules.permalinkgenerator.services.PermalinkGeneratorService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * Jahia action ({@code *.generatePermalinks.do}) that applies or previews vanity URL generation
 * for a set of JCR nodes.
 *
 * <p>Accepts POST requests with parameters:
 * <ul>
 *   <li>{@code nodeIds[]} — UUIDs of the nodes to process</li>
 *   <li>{@code languages[]} — language codes to process</li>
 *   <li>{@code preview=true} — if present, returns a preview without persisting (optional)</li>
 *   <li>{@code force=true} — if present with non-preview, overwrites manual vanities (optional)</li>
 *   <li>{@code bypassExcluded=true} — if present, ignores excluded-path settings (optional)</li>
 * </ul>
 *
 * <p>Returns JSON: {@code {"results": [...]}} on success, {@code {"error": "..."}} on failure.
 */
@Component(service = Action.class, immediate = true)
public class GeneratePermalinksAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(GeneratePermalinksAction.class);

    @Reference
    private PermalinkGeneratorService permalinkGeneratorService;

    @Activate
    public void activate() {
        setName("generatePermalinks");
        setRequiredMethods("POST");
    }

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext,
                                  Resource resource, JCRSessionWrapper session,
                                  Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
        List<String> nodeIds = parameters.get("nodeIds[]");
        List<String> languages = parameters.get("languages[]");

        if (nodeIds == null || nodeIds.isEmpty() || languages == null || languages.isEmpty()) {
            logger.warn("GeneratePermalinksAction: missing params — nodeIds={} languages={}", nodeIds, languages);
            return new ActionResult(HttpServletResponse.SC_BAD_REQUEST);
        }

        List<String> previewParam = parameters.get("preview");
        boolean preview = previewParam != null && !previewParam.isEmpty() && "true".equals(previewParam.get(0));

        if (preview) {
            try {
                List<String> bypassParam = parameters.get("bypassExcluded");
                boolean bypassExcluded = bypassParam != null && !bypassParam.isEmpty() && "true".equals(bypassParam.get(0));
                List<Map<String, String>> results = permalinkGeneratorService.previewVanityForNodeIds(nodeIds, languages, session, bypassExcluded);
                JSONArray arr = new JSONArray();
                for (Map<String, String> r : results) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid",        r.get("uuid"));
                    obj.put("language",    r.get("language"));
                    obj.put("computedUrl", r.get("computedUrl"));
                    obj.put("currentUrl",  r.get("currentUrl"));
                    obj.put("willChange",  "true".equals(r.get("willChange")));
                    obj.put("isManual",    "true".equals(r.get("isManual")));
                    arr.put(obj);
                }
                JSONObject body = new JSONObject();
                body.put("results", arr);
                return new ActionResult(HttpServletResponse.SC_OK, null, body);
            } catch (Exception e) {
                logger.error("GeneratePermalinksAction: preview failed — {}", e.getMessage(), e);
                JSONObject err = new JSONObject();
                err.put("error", e.getMessage());
                return new ActionResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, err);
            }
        }

        List<String> forceParam = parameters.get("force");
        boolean force = forceParam != null && !forceParam.isEmpty() && "true".equals(forceParam.get(0));

        logger.info("GeneratePermalinksAction: nodeIds={} languages={} force={}", nodeIds, languages, force);

        List<Map<String, String>> results = permalinkGeneratorService.generateVanityForNodeIds(nodeIds, languages, session, force);
        logger.info("GeneratePermalinksAction: {} operation(s) completed (force={})", results.size(), force);

        JSONArray arr = new JSONArray();
        for (Map<String, String> r : results) {
            JSONObject obj = new JSONObject();
            obj.put("uuid",     r.get("uuid"));
            obj.put("path",     r.get("path"));
            obj.put("language", r.get("language"));
            obj.put("action",   r.get("action"));
            obj.put("url",      r.get("url"));
            obj.put("oldUrl",   r.getOrDefault("oldUrl", ""));
            arr.put(obj);
        }
        JSONObject body = new JSONObject();
        body.put("results", arr);
        return new ActionResult(HttpServletResponse.SC_OK, null, body);
    }
}
