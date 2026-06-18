import {createTestSite, createAuditSite, createPage, SITE_KEY, SITE_KEY_AUDIT} from '../support/permalinkgen'
import {enableModule} from '@jahia/cypress'

describe('Setup', () => {
    before(() => {
        cy.login()
    })

    it('permalink-generator module is deployed and started', () => {
        cy.apollo({
            queryFile: 'graphql/jcr/query/getStartedModulesVersion.graphql'
        }).then((resp: any) => {
            const modules: any[] = resp?.data?.dashboard?.modules ?? []
            const mod = modules.find((m: any) => m.id === 'permalink-generator')
            expect(mod, 'permalink-generator module not found — deploy the OSGi bundle first').to.exist
            expect(mod.state, 'permalink-generator module must be in STARTED state').to.eq('STARTED')
        })
    })

    it('creates main test site plgentest with permalink-generator enabled', () => {
        createTestSite()
        // Create multilingual page tree for scenarios 1-3, 5-6
        createPage(`/sites/${SITE_KEY}/home`, 'page-about',    'About Us',   'À propos de nous')
        createPage(`/sites/${SITE_KEY}/home`, 'page-contact',  'Contact',    'Contactez-nous')
        createPage(`/sites/${SITE_KEY}/home`, 'page-products', 'Products',   'Produits')
        createPage(`/sites/${SITE_KEY}/home/page-products`, 'page-product-one', 'Product One', 'Produit Un')
    })

    it('creates audit test site plgenaudit WITHOUT permalink-generator initially', () => {
        createAuditSite()
        // Create pages BEFORE enabling the module → no auto-vanity generation
        createPage(`/sites/${SITE_KEY_AUDIT}/home`, 'page-alpha', 'Alpha Page',  'Page Alpha')
        createPage(`/sites/${SITE_KEY_AUDIT}/home`, 'page-beta',  'Beta Page',   'Page Bêta')
        // Now enable the module — existing pages have no vanity URLs
        enableModule('permalink-generator', SITE_KEY_AUDIT)
    })
})
