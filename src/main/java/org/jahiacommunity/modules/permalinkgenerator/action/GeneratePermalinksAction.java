package org.jahiacommunity.modules.permalinkgenerator.action;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahiacommunity.modules.permalinkgenerator.services.PermalinkGeneratorService;
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
        List<String> forceParam = parameters.get("force");
        boolean force = forceParam != null && !forceParam.isEmpty() && "true".equals(forceParam.get(0));

        logger.info("GeneratePermalinksAction: nodeIds={} languages={} force={}", nodeIds, languages, force);

        if (nodeIds == null || nodeIds.isEmpty() || languages == null || languages.isEmpty()) {
            logger.warn("GeneratePermalinksAction: missing params — nodeIds={} languages={}", nodeIds, languages);
            return new ActionResult(HttpServletResponse.SC_BAD_REQUEST);
        }

        int count = permalinkGeneratorService.generateVanityForNodeIds(nodeIds, languages, session, force);
        logger.info("GeneratePermalinksAction: {} permalink(s) created (force={})", count, force);
        return new ActionResult(HttpServletResponse.SC_OK);
    }
}
