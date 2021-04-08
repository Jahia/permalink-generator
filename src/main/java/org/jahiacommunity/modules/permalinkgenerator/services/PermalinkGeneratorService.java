package org.jahiacommunity.modules.permalinkgenerator.services;

import org.drools.core.spi.KnowledgeHelper;
import org.jahia.api.Constants;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.rules.AddedNodeFact;

import org.jahia.services.render.RenderContext;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.NonUniqueUrlMappingException;
import org.jahia.services.seo.jcr.VanityUrlManager;
import org.jahia.taglibs.jcr.node.JCRTagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.text.Normalizer;
import java.util.*;

/**
 * Service allowing to create a VanityUrl on page creation page
 */
public class PermalinkGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(PermalinkGeneratorService.class);

    /**
     * Will create a SEO for the giving path
     *
     * @param addedNodeFact the AddedNodeFact which called the rule
     * @param language  the language of the node
     * @param drools drools helper
     * @throws Exception
     */
    public void addVanity(final AddedNodeFact addedNodeFact, final String language, KnowledgeHelper drools) throws
            Exception {
        if (language != null) {
            JCRNodeWrapper node = null;
            Locale locale = new Locale(language);
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE,locale,null);
            try {
                node = session.getNode(addedNodeFact.getPath());
            } catch (PathNotFoundException e) {
                logger.debug(e.getMessage());
            }

            if (node != null && !JCRTagUtils.isNodeType(node, "jnt:content,jnt:contentFolder,,jnt:file,jnt:folder,jnt:globalSettings,jnt:module,jnt:nodeType,jnt:topic,jnt:user,jnt:vfsMountPointFactoryPage,jnt:virtualsite,wemnt:optimizationTest,wemnt:personalizedContent")
            ) {
                    JCRSiteNode site = node.getResolveSite();
                // check default language
                String defaultLanguage = site.getDefaultLanguage();
                logger.debug("Defaul language for site " + site.getSiteKey() + " is " + defaultLanguage + ". Current language is " + language);
                String siteKey = site.getSiteKey();
                RenderContext context = new org.jahia.services.render.RenderContext(null, null, null);
                context.setSite(site);
                if (JCRContentUtils.isADisplayableNode(node, context)) {
                    logger.debug("Try to create a vanity for node " + node.getPath());
                    List<JCRNodeWrapper> parentNodes = JCRTagUtils.getParentsOfType(node, "jmix:navMenuItem");
                    String url = "/" + slug(node.getDisplayableName());
                    Iterator<JCRNodeWrapper> parentNodesIterator = parentNodes.iterator();

                    logger.debug("Iterate using session locale " + session.getLocale().toString());

                    while (parentNodesIterator.hasNext()) {
                        JCRNodeWrapper parentPage = parentNodesIterator.next();
                        // skip the home (the last one)
                        if (parentNodesIterator.hasNext()) {
                            String pageTitle = parentPage.getDisplayableName();
                            String slugTitle = slug(pageTitle);
                            url = "/" + slugTitle + url;
                        }
                    }
                    if (! language.equals(defaultLanguage)) {
                        url = "/" + language + url;
                    }
                    if (!url.startsWith("/null")) {
                        try {
                            VanityUrlManager urlMgr = SpringContextSingleton.getInstance().getContext().getBean(VanityUrlManager.class);
                            if (urlMgr.findExistingVanityUrls(url, siteKey, session) == null || urlMgr.findExistingVanityUrls(url, siteKey, session).isEmpty()) {
                                try {
                                    VanityUrl vanityUrl = new VanityUrl(url, siteKey, language, true, true);
                                    urlMgr.saveVanityUrlMapping(node, vanityUrl, session);
                                    logger.debug("addVanity " + url + " for page " + node.getPath());
                                } catch (RepositoryException e) {
                                    logger.debug("could not add vanity " + url + " for page " + node.getPath() + " -> " + e.getMessage());
                                }
                            } else {
                                logger.debug("could not add vanity " + url + " for page " + node.getPath() + " -> already exist");
                            }
                        } catch (NonUniqueUrlMappingException nonUniqueUrlMappingException) {
                            logger.error(nonUniqueUrlMappingException.getMessage(), nonUniqueUrlMappingException);
                        }
                    }

                } else {
                    logger.debug("Could not create a vanity for node " + node.getPath() + " (not a displayableNode)");
                }




            }
        } else {
            logger.debug("Could not create vanity URL; language is null");
        }
    }

     public static boolean isNullOrEmpty(final Collection<?> c) {
        return c == null || c.isEmpty();
    }


    private String slug(final String str) {
        if (str == null) {
            return "";
        }
        return Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("[^ \\w|.]", "").trim().replaceAll("\\s+", "-").toLowerCase(Locale.ENGLISH);
    }
}
