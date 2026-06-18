import {waitForVanityUrl, SITE_KEY} from '../support/permalinkgen'

describe('Scenario 1 — Auto-generation on new page tree', () => {
    before(() => {
        cy.login()
    })

    it('page-about has an EN vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en').should('include', 'about')
    })

    it('page-about has a FR vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'fr').should('include', 'fr')
    })

    it('page-contact has an EN vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'en').should('include', 'contact')
    })

    it('page-contact has a FR vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-contact`, 'fr').should('include', 'fr')
    })

    it('page-products has an EN vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-products`, 'en').should('include', 'product')
    })

    it('page-products has a FR vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-products`, 'fr').should('include', 'fr')
    })

    it('nested page-product-one has an EN vanity URL containing the parent slug', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-products/page-product-one`, 'en').then((url: string) => {
            expect(url).to.include('product')
            // URL should be nested (contains more than one slug segment)
            expect(url.split('/').length).to.be.greaterThan(2)
        })
    })

    it('nested page-product-one has a FR vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-products/page-product-one`, 'fr').should('include', 'fr')
    })

    it('vanity URLs are different for each language', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en').then((enUrl: string) => {
            waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'fr').then((frUrl: string) => {
                expect(enUrl).to.not.equal(frUrl)
            })
        })
    })
})
