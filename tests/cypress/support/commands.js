/**
 * Navigate to the Permalink Generator settings panel for a site.
 * @param {string} siteKey - Jahia site key (default: JAHIA_SITE_KEY env)
 */
Cypress.Commands.add('visitPermalinkSettings', (siteKey) => {
    const key = siteKey || Cypress.env('JAHIA_SITE_KEY');
    cy.visit(`/jahia/administration/${key}/permalinkGeneratorSettings`);
    // Wait for the panel to be fully rendered
    cy.get('#permalinkMode').should('be.visible');
});
