import org.jahia.api.Constants
import org.jahia.services.content.JCRSessionFactory

// Trigger vanity URL generation by re-setting jcr:title, which fires the Drools rule.
// PATHS: comma-separated JCR paths; LANGS: comma-separated language codes; FORCE: ignored here

def paths = 'PATHS'.split(',')*.trim()
def langs = 'LANGS'.split(',')*.trim()

langs.each { lang ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, new java.util.Locale(lang), null)
    session.refresh(false)
    def changed = 0
    paths.each { path ->
        try {
            def node = session.getNode(path)
            if (node.hasProperty('jcr:title')) {
                def title = node.getProperty('jcr:title').getString()
                node.setProperty('jcr:title', title)
                changed++
            }
        } catch (Exception e) {
            println "error:${path}:${lang}:${e.getMessage()}"
        }
    }
    if (changed > 0) session.save()
    println "done:${lang}:touched=${changed}"
}
