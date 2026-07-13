import {createUser} from '@jahia/cypress'
import {
    SITE_KEY,
    SITE_A_ADMIN,
    SITE_A_ADMIN_PW,
    getNodeUuid,
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
        cy.logout()
        getNodeUuid(PAGE_A).then((uuid: string) => {
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
                    expect(resp.status).to.be.oneOf([401, 403])
                })
        })
    })

    it('site-A admin submitting site-A nodeIds succeeds with a results[] body (200)', () => {
        cy.login()
        getNodeUuid(PAGE_A).then((uuid: string) => {
            cy.logout()
            cy.login(SITE_A_ADMIN, SITE_A_ADMIN_PW)
            postGeneratePermalinksAction(actionUrl(SITE_KEY, 'en'), [uuid], ['en'])
                .then((resp: Cypress.Response<any>) => {
                    expect(resp.status).to.eq(200)
                    expect(resp.body).to.have.property('results')
                })
        })
    })
})
