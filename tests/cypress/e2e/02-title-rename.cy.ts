import {waitForVanityUrl, setPageTitle, SITE_KEY} from '../support/permalinkgen'

describe('Scenario 2 — Title rename updates vanity URL', () => {
    before(() => {
        cy.login()
    })

    it('changing EN title of page-contact updates the EN vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'en').then((originalUrl: string) => {
            expect(originalUrl).to.include('contact')

            setPageTitle(`/sites/${SITE_KEY}/home/page-contact`, 'en', 'Contact Us Now')

            waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'en').then((newUrl: string) => {
                expect(newUrl).to.include('contact-us-now')
                expect(newUrl).to.not.equal(originalUrl)
            })
        })
    })

    it('changing FR title updates the FR vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'fr').then((originalUrl: string) => {
            setPageTitle(`/sites/${SITE_KEY}/home/page-contact`, 'fr', 'Contactez-nous Maintenant')
            waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'fr').then((newUrl: string) => {
                expect(newUrl).to.include('contactez-nous-maintenant')
                expect(newUrl).to.not.equal(originalUrl)
            })
        })
    })
})
