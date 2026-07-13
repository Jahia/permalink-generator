import org.jahia.services.content.*
import org.jahia.api.Constants

/**
 * Grants the site-administrator role (which carries the module's siteAdminPermalinkGenerator
 * permission, declared under the site-admin permission group in src/main/import/permissions.xml)
 * to a user, SCOPED TO A SINGLE SITE NODE ONLY (/sites/SITE_KEY).
 *
 * This is the one missing provisioning piece for the G1 cross-site-authorization test (spec S1):
 * it produces a legitimate site-A administrator who is NOT a global/root admin and has NO grant
 * on any other site. The security escalation under test is that this correctly-scoped admin can
 * still mutate a DIFFERENT site's vanities through generatePermalinks.do because the service loads
 * each nodeId with a system session and never re-checks the permission per node/site.
 *
 * Params: USER (username, no prefix), SITE_KEY (target site key).
 */
def user = 'USER'
def siteKey = 'SITE_KEY'
def roleName = 'site-administrator'
def permName = 'siteAdminPermalinkGenerator'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)

// Best-effort: make sure the module permission is attached to the site-administrator role so the
// scoped grant carries it. Declaring the permission under /permissions/site-admin usually attaches
// it to site-administrator automatically; this guard covers deployments where it does not.
try {
    def role = session.getNode('/roles/' + roleName)
    def current = role.hasProperty('j:permissionNames') ?
            role.getProperty('j:permissionNames').getValues().collect { it.getString() } : []
    if (!current.contains(permName)) {
        def vf = session.getValueFactory()
        def vals = (current + permName).collect { vf.createValue(it) } as javax.jcr.Value[]
        role.setProperty('j:permissionNames', vals)
        session.save()
        println "attached-permission:${permName}:to:${roleName}"
    }
} catch (Exception e) {
    println "warn:attach-permission:${e.getMessage()}"
}

// Site-scoped grant: an ACL entry on the site node only. The role does NOT propagate to /sites/<other>.
def siteNode = session.getNode('/sites/' + siteKey)
siteNode.grantRoles('u:' + user, [roleName] as Set)
session.save()
println "granted:${roleName}:to:${user}:scoped-to:/sites/${siteKey}"
