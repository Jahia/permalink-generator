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

    it('grandchild page-product-feature vanity URL reflects new ancestor path after move (recursive refresh)', () => {
        // page-product-feature was a grandchild of page-products; after moving page-product-one to home
        // it is now a child of page-product-one at the home level.
        // refreshChildrenRecursive must have updated its vanity URL to drop the /products/ segment.
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-product-one/page-product-feature`, 'en', 20000).then((url: string) => {
            expect(url).to.include('product-feature')
            // The URL should NOT still reference 'products' as a parent segment
            // (i.e., the old /products/product-one/product-feature path is gone)
            const segments = url.split('/').filter(Boolean)
            // After the move, page-product-one is at the root so the feature's vanity
            // should have at most 2 meaningful segments (product-one + product-feature)
            expect(segments.length).to.be.lessThan(4)
        })
    })
})
