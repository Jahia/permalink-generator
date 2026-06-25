import {addNode} from '@jahia/cypress'
import {waitForVanityUrl, getVanityUrls, SITE_KEY} from '../support/permalinkgen'

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

    it('page with jmix:permalinkExcluded mixin receives no auto-generated vanity URL', () => {
        // Create a page that explicitly opts out of vanity URL generation
        addNode({
            parentPathOrId: `/sites/${SITE_KEY}/home`,
            name: 'page-excluded',
            primaryNodeType: 'jnt:page',
            mixins: ['jmix:permalinkExcluded'],
            properties: [
                {name: 'jcr:title', value: 'Excluded Page', language: 'en'},
                {name: 'jcr:title', value: 'Page exclue', language: 'fr'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })

        // Allow time for any Drools rules that might fire
        cy.wait(8000)

        // Assert no active vanity URL was generated for EN
        getVanityUrls(`/sites/${SITE_KEY}/home/page-excluded`).then((resp: any) => {
            const activeVanities = (resp?.data?.jcr?.nodeByPath?.vanityUrls ?? [])
                .filter((v: any) => v.active && v.default)
            expect(activeVanities.length, 'excluded page should have no active default vanity URLs').to.eq(0)
        })
    })
})
