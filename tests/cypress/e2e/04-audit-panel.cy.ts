import {SITE_KEY_AUDIT, getVanityUrls, waitForVanityUrl} from '../support/permalinkgen'

// Action URL pattern: /cms/render/default/en/sites/{siteKey}/settings/site-settings-base/permalinkGeneratorSettings.generatePermalinks.do
const actionUrl = (siteKey: string) =>
    `/cms/render/default/en/sites/${siteKey}/settings/site-settings-base/permalinkGeneratorSettings.generatePermalinks.do`

const getPageUuid = (path: string): Cypress.Chainable<string> => {
    return cy.apollo({
        queryFile: 'graphql/jcr/query/getNodeUuid.graphql',
        variables: {path}
    }).then((resp: any) => resp?.data?.jcr?.nodeByPath?.uuid)
}

describe('Scenario 4 — Audit generates missing vanity URLs via action endpoint', () => {
    before(() => {
        cy.login()
    })

    it('pages in plgenaudit have no vanity URLs before generation', () => {
        getVanityUrls(`/sites/${SITE_KEY_AUDIT}/home/page-alpha`).then((resp: any) => {
            const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            const active = vanities.filter((v: any) => v.active)
            expect(active.length, 'page-alpha should have no active vanity URLs').to.eq(0)
        })
    })

    it('calling the action endpoint generates vanity URLs for pages', () => {
        // Collect UUIDs for both audit pages
        cy.apollo({
            queryFile: 'graphql/jcr/query/getNodeUuid.graphql',
            variables: {path: `/sites/${SITE_KEY_AUDIT}/home/page-alpha`}
        }).then((resp1: any) => {
            const uuid1 = resp1?.data?.jcr?.nodeByPath?.uuid
            cy.apollo({
                queryFile: 'graphql/jcr/query/getNodeUuid.graphql',
                variables: {path: `/sites/${SITE_KEY_AUDIT}/home/page-beta`}
            }).then((resp2: any) => {
                const uuid2 = resp2?.data?.jcr?.nodeByPath?.uuid
                const body = `nodeIds[]=${uuid1}&nodeIds[]=${uuid2}&languages[]=en&languages[]=fr`
                cy.request({
                    method: 'POST',
                    url: actionUrl(SITE_KEY_AUDIT),
                    body,
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest'},
                    failOnStatusCode: false
                }).then(res => {
                    expect(res.status).to.eq(200)
                    expect(res.body.results).to.have.length.greaterThan(0)
                })
            })
        })
    })

    it('page-alpha now has EN vanity URL after generation', () => {
        waitForVanityUrl(`/sites/${SITE_KEY_AUDIT}/home/page-alpha`, 'en', 15000)
            .then((url: string) => {
                expect(url).to.include('alpha')
            })
    })

    it('page-alpha now has FR vanity URL after generation', () => {
        waitForVanityUrl(`/sites/${SITE_KEY_AUDIT}/home/page-alpha`, 'fr', 15000)
            .then((url: string) => {
                expect(url).to.exist
            })
    })

    it('page-beta now has EN vanity URL after generation', () => {
        waitForVanityUrl(`/sites/${SITE_KEY_AUDIT}/home/page-beta`, 'en', 15000)
            .then((url: string) => {
                expect(url).to.include('beta')
            })
    })
})
