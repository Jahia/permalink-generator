import {adminUrl, waitForVanityUrl, makeVanityManual, SITE_KEY} from '../support/permalinkgen'

describe('Scenario 5 — Force Regeneration', () => {
    before(() => {
        cy.login()
        waitForVanityUrl(`/sites/${SITE_KEY}/home/page-about`, 'en').then(() => {
            makeVanityManual(`/sites/${SITE_KEY}/home/page-about`, 'en')
        })
    })

    beforeEach(() => {
        cy.login()
        cy.visit(adminUrl())
        cy.get('#permalink-generator-root', {timeout: 30000}).should('exist')
    })

    it('Force Regeneration panel is visible', () => {
        cy.contains('Force Regeneration').should('be.visible')
    })

    it('Scan All Pages finds pages with stale or manual vanities', () => {
        cy.contains('button', 'Scan All Pages').click()
        cy.get('.pl-regen .pl-audit-table tbody tr', {timeout: 30000}).should('have.length.greaterThan', 0)
    })

    it('page-about EN pill is classified as manual after scan', () => {
        cy.contains('button', 'Scan All Pages').click()
        cy.get('.pl-regen .pl-audit-table tbody', {timeout: 30000})
        cy.contains('td', `/sites/${SITE_KEY}/home/page-about`)
            .closest('tr')
            .find('.pl-pill')
            .first()
            .should('satisfy', ($el: JQuery) => {
                return $el.hasClass('pl-pill-manual') || $el.hasClass('pl-pill-sel')
            })
    })

    it('confirm modal appears before regeneration', () => {
        cy.contains('button', 'Scan All Pages').click()
        cy.get('.pl-regen .pl-audit-table tbody tr', {timeout: 30000}).should('have.length.greaterThan', 0)
        cy.contains('button', /Regenerate selected/).click()
        cy.contains('Confirm regeneration').should('be.visible')
    })

    it('force regeneration creates URLs and shows report table', () => {
        cy.contains('button', 'Scan All Pages').click()
        cy.get('.pl-regen .pl-audit-table tbody tr', {timeout: 30000}).should('have.length.greaterThan', 0)
        cy.contains('button', /Regenerate selected/).click()
        cy.get('.modal-footer').contains('button', 'Regenerate').click()
        cy.contains('Regeneration report', {timeout: 30000}).should('be.visible')
        cy.contains('✓').should('be.visible')
    })
})
