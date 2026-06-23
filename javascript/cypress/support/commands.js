/**
 * cy.interceptGraphql(alias, fixture)
 * Intercepts POST to the GraphQL endpoint and replies with the given fixture file.
 * Both parallel GQL calls in AuditPanel/RegenPanel will be matched independently.
 */
Cypress.Commands.add('interceptGraphql', (alias, fixture) => {
    cy.intercept('POST', '**/graphql', { fixture }).as(alias);
});

/**
 * cy.interceptAction(alias, fixture)
 * Intercepts POST to **generatePermalinks.do and replies with the given fixture file.
 */
Cypress.Commands.add('interceptAction', (alias, fixture) => {
    cy.intercept('POST', '**generatePermalinks.do', { fixture }).as(alias);
});

/**
 * cy.waitForApp()
 * Waits until the React app has rendered at least one panel heading.
 */
Cypress.Commands.add('waitForApp', () => {
    cy.get('#permalink-generator-root h3').should('be.visible');
});
