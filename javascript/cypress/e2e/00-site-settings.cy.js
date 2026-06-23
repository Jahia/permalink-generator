/**
 * SiteSettings panel tests.
 *
 * The mode selector is a <select> with values SMART / FORCE.
 * The excluded-paths textarea is empty in the default template.
 * Save triggers a GraphQL mutation — we intercept it to avoid real network calls.
 */
describe('SiteSettings panel', () => {
    beforeEach(() => {
        // Intercept the GraphQL mutation fired on Save
        cy.interceptGraphql('gqlSave', 'graphql-empty.json');
        cy.visit('/');
        cy.waitForApp();
    });

    it('renders all three panel headers', () => {
        cy.contains('Permalink Generator').should('be.visible');
        cy.contains('Missing Permalink Audit').should('be.visible');
        cy.contains('Force Regeneration').should('be.visible');
    });

    it('shows SMART mode selected by default', () => {
        cy.get('#permalinkMode').should('have.value', 'SMART');
    });

    it('shows SMART help text when SMART is selected', () => {
        cy.get('#permalinkMode').should('have.value', 'SMART');
        cy.get('.permalink-mode-panel.smart').should('be.visible');
        cy.contains('The module creates vanity URLs for new and renamed pages').should('be.visible');
    });

    it('switches to FORCE mode and shows force help text', () => {
        cy.get('#permalinkMode').select('FORCE');
        cy.get('.permalink-mode-panel.force').should('be.visible');
        cy.contains('The module always applies its rules').should('be.visible');
        // Smart help panel should no longer carry the smart class
        cy.get('.permalink-mode-panel.smart').should('not.exist');
    });

    it('excluded paths textarea is empty by default', () => {
        cy.get('#excludedPaths').should('have.value', '');
    });

    it('Save button is labeled "Save" and is enabled', () => {
        cy.contains('button', 'Save').should('be.visible').and('not.be.disabled');
    });

    it('Cancel button is present', () => {
        cy.contains('button', 'Cancel').should('be.visible');
    });

    it('Save button fires GraphQL mutation and shows success status', () => {
        // Intercept the mutation call
        cy.intercept('POST', '**/graphql', (req) => {
            req.reply({ data: { jcr: { mutateNode: {} } } });
        }).as('gqlMutation');

        cy.contains('button', 'Save').click();
        cy.wait('@gqlMutation');
        // After a successful save the component sets status to i18n.saveSuccess
        cy.contains('✓ Settings saved').should('be.visible');
    });

    it('excluded paths textarea accepts input', () => {
        cy.get('#excludedPaths').type('/sites/testsite/excluded-section');
        cy.get('#excludedPaths').should('have.value', '/sites/testsite/excluded-section');
    });
});
