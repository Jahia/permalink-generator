import org.jahia.services.content.*
import org.jahia.services.seo.VanityUrl
import org.jahia.services.seo.jcr.VanityUrlManager
import org.jahia.services.SpringContextSingleton
import org.jahia.api.Constants

def path = 'CONTENT_PATH'
def lang = 'LANG'
def vanityUrl = 'VANITY_URL'
def sk = 'SITE_KEY'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, new Locale(lang), null)
session.refresh(false)
def node = session.getNode(path)
def manager = SpringContextSingleton.getInstance().getContext().getBean(VanityUrlManager.class)
def vu = new VanityUrl(vanityUrl, sk, lang, true, true)
manager.saveVanityUrlMapping(node, vu, session)
