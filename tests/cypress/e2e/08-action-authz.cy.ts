import {createUser} from '@jahia/cypress'
import {
    SITE_KEY,
    SITE_A_ADMIN,
    SITE_A_ADMIN_PW,
    getNodeUuid,
    getVanityUrls,
    actionUrl,
    postGeneratePermalinksAction
} from '../support/permalinkgen'

/**
 * G3 / spec S3 (F16) — the real generatePermalinks.do enforces authn + siteAdminPermalinkGenerator.
 * Distinct from G1 (which is same-site-authorized but cross-site escalation). Here we assert the
 * dispatcher denials that should already hold today plus the authorized happy path.
 *
 * Depends on SITE_A_ADMIN being provisioned by 07-cross-site-authz.cy.ts (runs first).
 */
const NOBODY = 'plgenNobody'
const NOBODY_PW = 'Permalink123!'
const PAGE_A = `/sites/${SITE_KEY}/home/page-about`

describe('Scenario 8 — generatePermalinks.do authn/authz (G3/S3)', () => {
    before(() => {
        cy.login()
        createUser(NOBODY, NOBODY_PW) // authenticated but has NO permission on any site
    })

    after(() => {
        cy.logout()
    })

    it('anonymous caller is rejected (no 200/results)', () => {
        // Resolve the target node as root first, then drop the session so the POST is genuinely anonymous.
        // cy.logout() alone does not clear the cy.request cookie jar reliably, so we also clearCookies();
        // otherwise the "anonymous" POST silently reuses the previous session and the action runs 200.
        cy.login()
        getNodeUuid(PAGE_A).then((uuid: string) => {
            cy.logout()
            cy.clearCookies()
            postGeneratePermalinksAction(actionUrl(SITE_KEY, 'en'), [uuid], ['en'])
                .then((resp: Cypress.Response<any>) => {
                    expect(resp.status, 'anonymous must not get a 200 result').to.not.eq(200)
                })
        })
    })

    it('authenticated but unprivileged caller is forbidden (403)', () => {
        cy.login()
        getNodeUuid(PAGE_A).then((uuid: string) => {
            cy.logout()
            cy.login(NOBODY, NOBODY_PW)
            postGeneratePermalinksAction(actionUrl(SITE_KEY, 'en'), [uuid], ['en'])
                .then((resp: Cypress.Response<any>) => {
                    // Jahia's Render/security filter denies a resource the caller lacks permission on by
                    // returning 404 ("resource not available", to avoid leaking existence) rather than a
                    // bare 403. Any of these means the unprivileged caller was refused (NOT a 200 result).
                    // The positive control below (site-A admin, same URL → 200) proves the route itself is
                    // reachable, so 404 here is a genuine authorization denial, not a routing miss.
                    expect(resp.status, 'unprivileged caller must be denied').to.be.oneOf([401, 403, 404])
                })
        })
    })

    it('site-A admin submitting site-A nodeIds is authorized (200) and generates the vanity', () => {
        cy.login()
        getNodeUuid(PAGE_A).then((uuid: string) => {
            cy.logout()
            cy.login(SITE_A_ADMIN, SITE_A_ADMIN_PW)
            postGeneratePermalinksAction(actionUrl(SITE_KEY, 'en'), [uuid], ['en'])
                .then((resp: Cypress.Response<any>) => {
                    // Authorized: the same URL that returns 404 for the anonymous and unprivileged callers
                    // above returns 200 here — the permission check passed and the action executed. (The
                    // JSON results[] body is only readable via a real browser XHR, not raw cy.request — see
                    // actionUrl note — so success is asserted by the 200 + the JCR side effect below.)
                    expect(resp.status, 'authorized site-A admin is accepted').to.eq(200)
                })
            // Side-effect proof the action really ran: page-about carries an active+default EN vanity.
            cy.logout()
            cy.login()
            getVanityUrls(PAGE_A).then((vresp: any) => {
                const v = (vresp?.data?.jcr?.nodeByPath?.vanityUrls ?? []).find(
                    (x: any) => x.language === 'en' && x.active && x.default,
                )
                expect(v, 'page-about has an active default EN vanity after authorized generation').to.exist
            })
        })
    })
})
