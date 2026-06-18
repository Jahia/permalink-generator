import org.jahia.api.Constants
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.content.JCRNodeWrapper

// Trigger vanity URL generation by re-setting jcr:title, which fires the Drools rule.
// force=true: delete existing vanity nodes for the language first, so Drools creates a fresh auto-generated one
// regardless of whether the existing vanity was manual.
// PATHS: comma-separated JCR paths; LANGS: comma-separated language codes; FORCE: true/false

def paths = 'PATHS'.split(',')*.trim()
def langs = 'LANGS'.split(',')*.trim()
boolean force = 'FORCE' == 'true'

langs.each { lang ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, new java.util.Locale(lang), null)
    session.refresh(false)
    def changed = 0
    paths.each { path ->
        try {
            def node = session.getNode(path)
            if (force && node.hasNode('vanityUrlMapping')) {
                def toRemove = []
                def it = node.getNode('vanityUrlMapping').getNodes()
                while (it.hasNext()) {
                    def v = (JCRNodeWrapper) it.nextNode()
                    def langProp = v.hasProperty('jcr:language') ? v.getProperty('jcr:language').getString() : null
                    if (lang == langProp) toRemove << v
                }
                toRemove.each { v -> v.remove() }
                if (!toRemove.isEmpty()) {
                    session.save()
                    session.refresh(false)
                }
            }
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
    println "done:${lang}:touched=${changed}:force=${force}"
}
