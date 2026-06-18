import {waitForVanityUrl, movePage, SITE_KEY} from '../support/permalinkgen'

describe('Scenario 3 — Branch move recalculates vanity URLs', () => {
    before(() => {
        cy.login()
    })

    it('moving page-product-one to home root updates its EN vanity URL', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-products/page-product-one`, 'en').then((beforeUrl: string) => {
            expect(beforeUrl.split('/').length).to.be.greaterThan(2) // nested

            movePage(
                `/sites/${SITE_KEY}/home/page-products/page-product-one`,
                `/sites/${SITE_KEY}/home`
            )

            waitForVanityUrl(`/sites/${SITE_KEY}/home/page-product-one`, 'en').then((afterUrl: string) => {
                expect(afterUrl).to.include('product-one')
                expect(afterUrl).to.not.equal(beforeUrl)
            })
        })
    })

    it('FR vanity URL of moved page also updated', () => {
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-product-one`, 'fr').then((url: string) => {
            expect(url).to.include('fr')
            expect(url).to.include('produit')
        })
    })
})
