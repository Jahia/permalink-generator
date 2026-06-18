import {waitForVanityUrl, makeVanityManual, generatePermalinks, SITE_KEY} from '../support/permalinkgen'

const PAGE = `/sites/${SITE_KEY}/home/page-about`

describe('Scenario 5 — Force Regeneration (SMART vs FORCE mode)', () => {
    before(() => {
        cy.login()
        waitForVanityUrl(PAGE, 'en').then(() => {
            makeVanityManual(PAGE, 'en')
        })
    })

    it('page-about EN vanity is manual after makeVanityManual', () => {
        waitForVanityUrl(PAGE, 'en').should('include', 'about')
    })

    it('force=false does NOT regenerate a manual EN vanity (SMART behaviour)', () => {
        // Get current URL, call generatePermalinks with force=false, verify URL unchanged
        waitForVanityUrl(PAGE, 'en').then((urlBefore: string) => {
            generatePermalinks([PAGE], ['en'], false)
            cy.wait(3000)
            waitForVanityUrl(PAGE, 'en').then((urlAfter: string) => {
                expect(urlAfter, 'SMART mode should not change the manual vanity URL').to.equal(urlBefore)
            })
        })
    })

    it('force=true DOES regenerate the manual EN vanity', () => {
        generatePermalinks([PAGE], ['en'], true)
    })

    it('EN vanity is auto-generated again after force regen', () => {
        waitForVanityUrl(PAGE, 'en', 15000).should('include', 'about')
    })
})
