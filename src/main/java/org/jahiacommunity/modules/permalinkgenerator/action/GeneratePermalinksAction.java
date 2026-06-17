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
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public class GeneratePermalinksAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(GeneratePermalinksAction.class);

    @Autowired
    private PermalinkGeneratorService permalinkGeneratorService;

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
            List<Map<String, String>> results = permalinkGeneratorService.previewVanityForNodeIds(nodeIds, languages, session);
            JSONArray arr = new JSONArray();
            for (Map<String, String> r : results) {
                JSONObject obj = new JSONObject();
                obj.put("uuid",        r.get("uuid"));
                obj.put("language",    r.get("language"));
                obj.put("computedUrl", r.get("computedUrl"));
                obj.put("currentUrl",  r.get("currentUrl"));
                obj.put("willChange",  "true".equals(r.get("willChange")));
                arr.put(obj);
            }
            JSONObject body = new JSONObject();
            body.put("results", arr);
            return new ActionResult(HttpServletResponse.SC_OK, null, body);
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
