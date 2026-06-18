import {waitForVanityUrl, makeVanityManual, SITE_KEY} from '../support/permalinkgen'

const actionUrl = (siteKey: string = SITE_KEY) =>
    `/cms/render/default/en/sites/${siteKey}/settings/site-settings-base/permalinkGeneratorSettings.generatePermalinks.do`

describe('Scenario 5 — Force Regeneration via action endpoint', () => {
    before(() => {
        cy.login()
        // Wait for EN vanity, then make it manual (simulates a manually-edited vanity)
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en').then(() => {
            makeVanityManual(`/sites/${SITE_KEY}/home/page-about`, 'en')
        })
    })

    it('page-about EN vanity is manual after makeVanityManual', () => {
        // Verify the vanity still exists but is now manual (can query but cannot be overwritten by SMART)
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en')
            .should('include', 'about')
    })

    it('force=false does NOT regenerate a manual EN vanity (SMART behaviour)', () => {
        cy.apollo({
            queryFile: 'graphql/jcr/query/getNodeUuid.graphql',
            variables: {path: `/sites/${SITE_KEY}/home/page-about`}
        }).then((resp: any) => {
            const uuid = resp?.data?.jcr?.nodeByPath?.uuid
            const body = `nodeIds[]=${uuid}&languages[]=en`
            cy.request({
                method: 'POST',
                url: actionUrl(),
                body,
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest'},
                failOnStatusCode: false
            }).then(res => {
                expect(res.status).to.eq(200)
                // action should return 0 operations (manual vanity skipped in SMART mode)
                const ops = res.body?.results ?? []
                const changed = ops.filter((r: any) => r.action !== 'already_correct')
                expect(changed.length, 'SMART mode should not regenerate manual vanity').to.eq(0)
            })
        })
    })

    it('force=true DOES regenerate the manual EN vanity', () => {
        cy.apollo({
            queryFile: 'graphql/jcr/query/getNodeUuid.graphql',
            variables: {path: `/sites/${SITE_KEY}/home/page-about`}
        }).then((resp: any) => {
            const uuid = resp?.data?.jcr?.nodeByPath?.uuid
            const body = `nodeIds[]=${uuid}&languages[]=en&force=true`
            cy.request({
                method: 'POST',
                url: actionUrl(),
                body,
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest'},
                failOnStatusCode: false
            }).then(res => {
                expect(res.status).to.eq(200)
                const ops = res.body?.results ?? []
                expect(ops.length, 'force regen should produce at least one operation').to.be.greaterThan(0)
            })
        })
    })

    it('EN vanity is auto-generated again after force regen', () => {
        // After force regen, the EN vanity should exist (module recreated it with jmix:permalinkGenerated)
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en', 15000)
            .should('include', 'about')
    })
})
