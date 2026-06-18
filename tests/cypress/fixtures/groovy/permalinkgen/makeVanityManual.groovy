import org.jahia.services.content.*
import org.jahia.api.Constants

def path = 'PATH'
def lang = 'LANG'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def node = session.getNode(path)
if (node.hasNode("vanityUrlMapping")) {
    def it = node.getNode("vanityUrlMapping").getNodes()
    while (it.hasNext()) {
        def v = (JCRNodeWrapper) it.nextNode()
        if (v.isNodeType("jmix:permalinkGenerated")) {
            def langProp = v.hasProperty("jcr:language") ? v.getProperty("jcr:language").getString() : null
            if (lang == langProp) {
                v.removeMixin("jmix:permalinkGenerated")
            }
        }
    }
    session.save()
}
