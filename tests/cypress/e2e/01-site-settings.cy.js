describe('Permalink Generator — Site settings', () => {
    const siteKey = Cypress.env('JAHIA_SITE_KEY');

    before(() => {
        cy.login(Cypress.env('JAHIA_USERNAME'), Cypress.env('JAHIA_PASSWORD'));
    });

    beforeEach(() => {
        cy.visitPermalinkSettings(siteKey);
    });

    it('loads the settings panel', () => {
        cy.get('h2').should('contain.text', 'Permalink Generator');
        cy.get('#permalinkMode').should('be.visible');
        cy.get('#excludedPaths').should('be.visible');
        cy.get('#btnSave').should('be.visible');
    });

    it('shows mode description panel when SMART is selected', () => {
        cy.get('#permalinkMode').select('SMART');
        cy.get('#modePanel').should('have.class', 'smart');
        cy.get('#modeTitle').should('not.be.empty');
        cy.get('#modeHelp').should('not.be.empty');
    });

    it('shows mode description panel when FORCE is selected', () => {
        cy.get('#permalinkMode').select('FORCE');
        cy.get('#modePanel').should('have.class', 'force');
        cy.get('#modeTitle').should('not.be.empty');
    });

    it('saves mode and excluded paths', () => {
        cy.get('#permalinkMode').select('SMART');
        cy.get('#excludedPaths').clear();
        cy.get('#btnSave').click();
        cy.get('#saveStatus').should('contain.text', '✓');
    });

    it('cancel reloads the page', () => {
        cy.get('#permalinkMode').select('FORCE');
        cy.get('#btnCancel').click();
        // After reload, the mode should be back to what was saved
        cy.get('#permalinkMode').should('be.visible');
    });
});
