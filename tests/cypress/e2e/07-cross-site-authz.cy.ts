import {createUser} from '@jahia/cypress'
import {
    SITE_KEY,
    SITE_KEY_XSITE,
    SITE_A_ADMIN,
    SITE_A_ADMIN_PW,
    createXSite,
    createPage,
    createManualVanityUrl,
    grantSitePermalinkAdmin,
    getVanityUrls,
    getNodeUuid,
    publishNode,
    actionUrl,
    postGeneratePermalinksAction
} from '../support/permalinkgen'

/**
 * G1 / spec S1 — CRITICAL cross-site authorization escalation.
 *
 * A site-A-scoped admin (NO grant on site B) POSTs the REAL generatePermalinks.do at site A's URL,
 * but passes site-B node UUIDs with force=true. The action's permission is evaluated only against
 * the site-A resource node (GPA:57-58), and the service then loads every nodeId with a SYSTEM
 * session (PGS:1036-1039) gated solely by "module installed on that site" (PGS:1006) — so site B's
 * MANUAL editorial vanities get overwritten/demoted.
 *
 * This spec asserts the TARGET (secure) contract and is therefore EXPECTED TO FAIL against current
 * code (returns 200 and overwrites B). It is the red test the Stage-7 per-node/per-site re-check
 * must turn green. The request traverses the real dispatcher + security filter — nothing is faked.
 */
const PAGE_B = `/sites/${SITE_KEY_XSITE}/home/page-secret`
const MANUAL_URL_B = '/editorial-secret'

describe('Scenario 7 — cross-site authorization escalation is denied (G1/S1, CRITICAL)', () => {
    before(() => {
        cy.login() // root: provision only
        // Site B with a MANUAL editorial vanity that must survive.
        createXSite()
        createPage(`/sites/${SITE_KEY_XSITE}/home`, 'page-secret', 'Secret Page', 'Secret Page')
        createManualVanityUrl(PAGE_B, 'en', MANUAL_URL_B, SITE_KEY_XSITE)
        publishNode(PAGE_B)

        // A legitimate site-A administrator (not root, no grant on site B).
        createUser(SITE_A_ADMIN, SITE_A_ADMIN_PW)
        grantSitePermalinkAdmin(SITE_A_ADMIN, SITE_KEY)
    })

    after(() => {
        cy.logout()
    })

    it('site B manual vanity is active+default before the attack', () => {
        getVanityUrls(PAGE_B).then((resp: any) => {
            const v = (resp?.data?.jcr?.nodeByPath?.vanityUrls ?? [])
                .find((x: any) => x.language === 'en' && x.url === MANUAL_URL_B)
            expect(v, 'seeded manual vanity should exist').to.exist
            expect(v.active).to.eq(true)
            expect(v.default).to.eq(true)
        })
    })

    it('site-A admin POSTing site-B nodeIds with force=true is DENIED and site B is untouched', () => {
        getNodeUuid(PAGE_B).then((uuidB: string) => {
            cy.logout()
            cy.login(SITE_A_ADMIN, SITE_A_ADMIN_PW)

            postGeneratePermalinksAction(actionUrl(SITE_KEY, 'en'), [uuidB], ['en'], {force: true})
                .then((resp: Cypress.Response<any>) => {
                    // TARGET contract (post Stage-7): the cross-site mutation is refused.
                    expect(resp.status, 'cross-site mutation must be forbidden').to.be.oneOf([401, 403])
                })

            // And site B's manual vanity must remain active+default, NOT demoted to a redirect.
            cy.logout()
            cy.login()
            getVanityUrls(PAGE_B).then((resp: any) => {
                const v = (resp?.data?.jcr?.nodeByPath?.vanityUrls ?? [])
                    .find((x: any) => x.language === 'en' && x.url === MANUAL_URL_B)
                expect(v, 'site B manual vanity must still exist').to.exist
                expect(v.active, 'site B manual vanity must stay active').to.eq(true)
                expect(v.default, 'site B manual vanity must stay default (not demoted)').to.eq(true)
            })
        })
    })
})
