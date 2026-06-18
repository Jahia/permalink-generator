import {waitForVanityUrl, waitForVanityUrlContaining, makeVanityManual, setPageTitle, assertNoVanityUrlChange, SITE_KEY} from '../support/permalinkgen'

describe('Scenario 6 — SMART mode respects manual vanity URLs', () => {
    const pagePath = `/sites/${SITE_KEY}/home/page-about`

    before(() => {
        cy.login()
        waitForVanityUrl(pagePath, 'fr').then(() => {
            makeVanityManual(pagePath, 'fr')
        })
    })

    it('after making FR vanity manual, it still exists', () => {
        waitForVanityUrl(pagePath, 'fr').should('exist')
    })

    it('SMART mode: renaming the FR title does NOT change the manual FR vanity', () => {
        waitForVanityUrl(pagePath, 'fr').then((manualUrl: string) => {
            setPageTitle(pagePath, 'fr', 'Un Nouveau Titre En Français')
            cy.wait(5000)
            assertNoVanityUrlChange(pagePath, 'fr', manualUrl)
        })
    })

    it('SMART mode: renaming EN title DOES update the EN auto-generated vanity', () => {
        setPageTitle(pagePath, 'en', 'About Our Company')
        waitForVanityUrlContaining(pagePath, 'en', 'about-our-company', 30000).then((newUrl: string) => {
            expect(newUrl).to.include('about-our-company')
        })
    })
})
