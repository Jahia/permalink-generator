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
import java.util.*;
import com.github.slugify.*;

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
            try {
                JCRSiteNode site = addedNodeFact.getNode().getResolveSite();
                if (site != null) {
                    // check if module is enabled on that site
                    boolean isInstalled = false;
                    try {
                        JCRPropertyWrapper installedModules = site.getProperty("j:installedModules");

                        for (JCRValueWrapper module : installedModules.getValues()) {
                            if ("permalink-generator".equals(module.getString())) {
                                isInstalled = true;
                                break;
                            }
                        }
                    } catch (javax.jcr.PathNotFoundException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Could not get node j:installedModules for path " + site.getPath() + " : " + e.getMessage());
                        }
                    }
                    if (isInstalled) {
                        JCRNodeWrapper node = null;
                        Locale locale = new Locale(language);
                        JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE,locale,null);
                        try {
                            node = session.getNode(addedNodeFact.getPath());
                        } catch (PathNotFoundException e) {
                            if (logger.isDebugEnabled()) {
                                logger.debug(e.getMessage());
                            }
                        }
                        if (node != null && !JCRTagUtils.isNodeType(node, "jnt:contentFolder,jnt:file,jnt:folder,jnt:globalSettings,jnt:module,jnt:nodeType,jnt:topic,jnt:user,jnt:vfsMountPointFactoryPage,jnt:virtualsite,wemnt:optimizationTest,wemnt:personalizedContent")
                        ) {
                            // check default language
                            String defaultLanguage = site.getDefaultLanguage();
                            String siteKey = site.getSiteKey();
                            RenderContext context = new org.jahia.services.render.RenderContext(null, null, null);
                            context.setSite(site);
                            if (JCRContentUtils.isADisplayableNode(node, context)) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Try to create a vanity for node " + node.getPath());
                                }
                                List<JCRNodeWrapper> parentNodes = JCRTagUtils.getParentsOfType(node, "jmix:navMenuItem");
                                String url = "/" + (new Slugify()).slugify(node.getDisplayableName());
                                Iterator<JCRNodeWrapper> parentNodesIterator = parentNodes.iterator();
                                while (parentNodesIterator.hasNext()) {
                                    JCRNodeWrapper parentPage = parentNodesIterator.next();
                                    // skip the home (the last one)
                                    if (parentNodesIterator.hasNext()) {
                                        String pageTitle = parentPage.getDisplayableName();
                                        String slugTitle = (new Slugify()).slugify(pageTitle);
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
                                                if (logger.isDebugEnabled())  {
                                                    logger.debug("addVanity " + url + " for page " + node.getPath());
                                                }
                                            } catch (RepositoryException e) {
                                                if (logger.isDebugEnabled()) {
                                                    logger.debug("could not add vanity " + url + " for page " + node.getPath() + " -> " + e.getMessage());
                                                }
                                            }
                                        } else {
                                            if (logger.isDebugEnabled())  {
                                                logger.debug("could not add vanity " + url + " for page " + node.getPath() + " -> already exist");
                                            }
                                        }
                                    } catch (NonUniqueUrlMappingException nonUniqueUrlMappingException) {
                                        logger.error(nonUniqueUrlMappingException.getMessage(), nonUniqueUrlMappingException);
                                    }
                                }

                            } else {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Could not create a vanity for node " + node.getPath() + " (not a displayableNode)");
                                }
                            }
                        }
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Module permalink-generator not installed on this website. Bypassing.");
                        }
                    }
                }
            } catch (RepositoryException re) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not resolve site for node " + addedNodeFact.getPath());
                }
            }

        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not create vanity URL; language is null");
            }
        }
    }
}
