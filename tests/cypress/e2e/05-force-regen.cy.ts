import {waitForVanityUrl, makeVanityManual, generatePermalinks, setPageTitle, SITE_KEY} from '../support/permalinkgen'

const PAGE = `/sites/${SITE_KEY}/home/page-about`

describe('Scenario 5 — Force Regeneration (SMART vs FORCE mode)', () => {
    before(() => { cy.login() })

    it('page-about has an auto-generated EN vanity URL', () => {
        waitForVanityUrl(PAGE, 'en').should('include', 'about')
    })

    it('SMART mode: does not update a manual vanity even when title changes', () => {
        // Mark existing vanity as manual (remove jmix:permalinkGenerated)
        makeVanityManual(PAGE, 'en')
        // Change the title — Drools fires but SMART mode skips because vanity is manual
        setPageTitle(PAGE, 'en', 'About Modified')
        cy.wait(2000)
        // generatePermalinks(force=false) also skips — manual vanity is preserved
        generatePermalinks([PAGE], ['en'], false)
        cy.wait(2000)
        // URL should still contain 'about' from the original slug, not 'modified'
        waitForVanityUrl(PAGE, 'en').then((url: string) => {
            expect(url).to.include('about')
            expect(url).not.to.include('modified')
        })
    })

    it('FORCE mode: regenerates the URL to match the current title', () => {
        // force=true deletes the manual vanity then re-triggers generation
        generatePermalinks([PAGE], ['en'], true)
        // URL should now reflect the current title 'About Modified'
        waitForVanityUrl(PAGE, 'en', 15000).then((url: string) => {
            expect(url).to.include('about')
            expect(url).to.include('modified')
        })
    })
})
