import org.jahia.services.content.*
import org.jahia.api.Constants

/**
 * Sets j:permalinkGeneratorMode (SMART|FORCE) on a site node, adding the
 * jmix:permalinkGeneratorSettings mixin if absent. Used by the real FORCE-mode e2e (G20/S12)
 * which exercises the module's actual FORCE branch (PGS:262 inert SMART guard -> overwrite +
 * demote-to-redirect), NOT the delete-then-recreate shortcut the old Groovy helper used.
 *
 * Params: SITE_KEY (site key), MODE (SMART or FORCE).
 */
def siteKey = 'SITE_KEY'
def mode = 'MODE'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def site = session.getNode('/sites/' + siteKey)
if (!site.isNodeType('jmix:permalinkGeneratorSettings')) {
    site.addMixin('jmix:permalinkGeneratorSettings')
}
site.setProperty('j:permalinkGeneratorMode', mode)
session.save()
println "mode-set:${siteKey}:${mode}"
