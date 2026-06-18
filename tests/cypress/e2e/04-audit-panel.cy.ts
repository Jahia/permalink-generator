import {adminUrl, SITE_KEY_AUDIT} from '../support/permalinkgen'

describe('Scenario 4 — Audit panel generates missing vanity URLs', () => {
    before(() => {
        cy.login()
        cy.visit(adminUrl(SITE_KEY_AUDIT))
    })

    it('loads the admin page with all panel headings', () => {
        cy.get('#permalink-generator-root h3').should('have.length', 2)
        cy.contains('Missing Permalink Audit').should('be.visible')
        cy.contains('Force Regeneration').should('be.visible')
    })

    it('audit scan finds pages with missing vanity URLs', () => {
        cy.get('#plAuditPath').clear().type(`/sites/${SITE_KEY_AUDIT}`)
        cy.contains('button', 'Scan').first().click()
        cy.get('.pl-audit-table tbody tr', {timeout: 20000}).should('have.length.greaterThan', 0)
    })

    it('status shows pages with missing permalinks', () => {
        cy.contains(/missing permalink|pages scanned/).should('be.visible')
    })

    it('select-all then generate creates vanity URLs', () => {
        cy.get('.pl-audit-table thead input[type="checkbox"]').first().check()
        cy.contains('button', /Generate permalinks \(\d+\)/).should('not.be.disabled')
        cy.contains('button', /Generate permalinks/).click()
        cy.contains('✓', {timeout: 30000}).should('be.visible')
    })

    it('rescan shows all-good after generation', () => {
        cy.get('#plAuditPath').clear().type(`/sites/${SITE_KEY_AUDIT}`)
        cy.contains('button', 'Scan').first().click()
        cy.contains(/all permalinks up to date|0.*missing/, {timeout: 20000}).should('be.visible')
    })
})
