/**
 * AuditPanel tests.
 *
 * Fixture data (graphql-nodes.json) produces 3 nodes:
 *   uuid-page-1  /sites/testsite/home/about   — EN missing, FR missing
 *   uuid-page-2  /sites/testsite/home/contact — EN exists, FR missing
 *   uuid-home    /sites/testsite/home         — isHomePage=true, EN+FR missing
 *
 * The component deduplicates across the two parallel GQL calls (jnt:page +
 * jmix:mainResource), so each node appears exactly once.
 * Rows shown = 3 (all have missing.size > 0).  uuid-home gets pl-row-ignored.
 */
describe('AuditPanel', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.waitForApp();
    });

    // ── Header & legend ──────────────────────────────────────────────────────

    it('shows the audit panel heading', () => {
        cy.contains('Missing Permalink Audit').should('be.visible');
    });

    it('help button toggles legend open/closed', () => {
        cy.get('.pl-audit .pl-help-btn').click();
        cy.get('.pl-audit .pl-legend-wrap.open').should('exist');

        cy.get('.pl-audit .pl-help-btn').click();
        cy.get('.pl-audit .pl-legend-wrap.open').should('not.exist');
    });

    it('legend contains exactly 4 colour pills', () => {
        cy.get('.pl-audit .pl-help-btn').click();
        cy.get('.pl-audit .pl-legend-wrap .pl-pill').should('have.length', 4);
    });

    // ── Path validation ──────────────────────────────────────────────────────

    it('shows validation error when path does not start with /sites/', () => {
        cy.get('#plAuditPath').clear().type('invalid-path');
        cy.contains('button', 'Scan').first().click();
        cy.contains('Path must start with /sites/').should('be.visible');
    });

    // ── Scan results ─────────────────────────────────────────────────────────

    it('scan with results shows 3 table rows', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');
        cy.get('.pl-audit-table tbody tr').should('have.length', 3);
    });

    it('homepage row carries pl-row-ignored class', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');
        // Match the homepage row exactly (avoid matching /home/about etc.)
        cy.get('.pl-audit-table tbody td[title="/sites/testsite/home"]')
            .closest('tr')
            .should('have.class', 'pl-row-ignored');
    });

    it('missing pills carry pl-pill-miss class after scan', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');
        cy.get('.pl-audit-table .pl-pill-miss').should('have.length.greaterThan', 0);
    });

    // ── Cell / row / column selection ────────────────────────────────────────

    it('clicking a missing pill selects it (miss → sel)', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table .pl-pill-miss').first().click();
        cy.get('.pl-audit-table .pl-pill-sel').should('have.length.greaterThan', 0);
    });

    it('clicking a selected pill deselects it (sel → miss)', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table .pl-pill-miss').first().as('pill');
        cy.get('@pill').click();
        cy.get('.pl-audit-table .pl-pill-sel').first().click();
        cy.get('.pl-audit-table .pl-pill-miss').should('have.length.greaterThan', 0);
    });

    it('select-all checkbox selects all missing non-homepage pills', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table thead input[type="checkbox"]').first().check();
        // uuid-home is homepage so its pills remain miss/unclickable;
        // about: 2 sel, contact FR: 1 sel → at least 3 selected pills
        cy.get('.pl-audit-table .pl-pill-sel').should('have.length.greaterThan', 2);
    });

    it('generate button label includes the selection count', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table thead input[type="checkbox"]').first().check();
        cy.contains('button', 'Generate permalinks').should('contain.text', '(');
    });

    it('FR column header checkbox selects all FR missing pills', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        // The FR column header checkbox is in th[data-lang="fr"]
        cy.get('.pl-audit-table th[data-lang="fr"] input[type="checkbox"]').check();
        cy.get('.pl-audit-table .pl-pill-sel').each(($pill) => {
            expect($pill.text().trim()).to.equal('fr');
        });
    });

    // ── Generate action ───────────────────────────────────────────────────────

    it('generate calls action endpoint and shows success status', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.interceptAction('actionGen', 'action-generate.json');

        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table thead input[type="checkbox"]').first().check();
        cy.contains('button', 'Generate permalinks').click();
        cy.wait('@actionGen');
        cy.contains('✓').should('be.visible');
    });

    it('generated pills change to pl-pill-gen after generation', () => {
        cy.interceptGraphql('gqlNodes', 'graphql-nodes.json');
        cy.interceptAction('actionGen', 'action-generate.json');

        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlNodes');

        cy.get('.pl-audit-table thead input[type="checkbox"]').first().check();
        cy.contains('button', 'Generate permalinks').click();
        cy.wait('@actionGen');
        cy.get('.pl-audit-table .pl-pill-gen').should('have.length.greaterThan', 0);
    });

    // ── All-good state ────────────────────────────────────────────────────────

    it('shows all-good message when scan returns zero missing nodes', () => {
        cy.interceptGraphql('gqlEmpty', 'graphql-empty.json');
        cy.get('#plAuditPath').clear().type('/sites/testsite');
        cy.contains('button', 'Scan').first().click();
        cy.wait('@gqlEmpty');
        cy.contains('all permalinks up to date').should('be.visible');
    });
});
