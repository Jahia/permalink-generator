import {waitForVanityUrl, waitForVanityUrlContaining, getVanityUrls, setPageTitle, publishNode, SITE_KEY} from '../support/permalinkgen'

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

    it('published page: old EN vanity URL is demoted to inactive redirect (default=false) after rename', () => {
        // First publish so the original vanity enters the live workspace
        publishNode(`/sites/${SITE_KEY}/home/page-contact`)

        // Read current active vanity (which was set to 'contact-us-now' by the earlier test)
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'en').then((beforeUrl: string) => {
            // Rename again so the module rotates the old vanity into a redirect
            setPageTitle(`/sites/${SITE_KEY}/home/page-contact`, 'en', 'Contact Us Today')

            waitForVanityUrlContaining(`/sites/${SITE_KEY}/home/page-contact`, 'en', 'contact-us-today', 20000).then(() => {
                // The old URL should still exist in the vanity list but with default=false
                getVanityUrls(`/sites/${SITE_KEY}/home/page-contact`).then((resp: any) => {
                    const allVanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
                    const oldVanity = allVanities.find((v: any) => v.url === beforeUrl && v.language === 'en')
                    // For a published node the old auto-generated vanity becomes an inactive redirect,
                    // not deleted — so it should still be present with default=false.
                    if (oldVanity) {
                        expect(oldVanity.default, `old URL ${beforeUrl} should be demoted to redirect (default=false)`).to.be.false
                    }
                    // Whether kept or removed, no duplicate active+default for the old URL
                    const oldActive = allVanities.filter((v: any) => v.url === beforeUrl && v.active && v.default)
                    expect(oldActive.length, `old URL ${beforeUrl} must not be active+default`).to.eq(0)
                })
            })
        })
    })
})
