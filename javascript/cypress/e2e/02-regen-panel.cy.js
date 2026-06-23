/**
 * RegenPanel tests.
 *
 * action-preview.json classifies:
 *   uuid-page-1 / en  → stale  (willChange=true)
 *   uuid-page-1 / fr  → manual (isManual=true, willChange=false)
 *   uuid-page-2 / fr  → stale  (willChange=true)
 *
 * Nodes from graphql-nodes.json:
 *   uuid-page-1  /sites/testsite/home/about   — vanityUrls=[]       → EN+FR missing
 *   uuid-page-2  /sites/testsite/home/contact — vanityUrls=[en]     → FR missing
 *   uuid-home    /sites/testsite/home         — isHomePage=true
 *
 * After preview classification the component merges preview data with missing
 * status.  uuid-page-1/en is in missingLangs so preview stale is irrelevant (it
 * just stays missing→auto-selected).  uuid-page-1/fr is also in missingLangs.
 * uuid-page-2/fr is missing, stale per preview.
 *
 * All non-homepage rows are auto-selected → all pills start as pl-pill-sel.
 */
describe('RegenPanel', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.waitForApp();
    });

    // ── Header & legend ──────────────────────────────────────────────────────

    it('shows the regen panel heading', () => {
        cy.contains('Force Regeneration').should('be.visible');
    });

    it('help button toggles legend open/closed', () => {
        cy.get('.pl-regen .pl-help-btn').click();
        cy.get('.pl-regen .pl-legend-wrap.open').should('exist');

        cy.get('.pl-regen .pl-help-btn').click();
        cy.get('.pl-regen .pl-legend-wrap.open').should('not.exist');
    });

    it('legend contains exactly 6 colour pills', () => {
        cy.get('.pl-regen .pl-help-btn').click();
        cy.get('.pl-regen .pl-legend-wrap .pl-pill').should('have.length', 6);
    });

    // ── Scan controls ────────────────────────────────────────────────────────

    it('bypass excluded checkbox is unchecked by default', () => {
        cy.get('.pl-regen input[type="checkbox"]').first().should('not.be.checked');
    });

    it('Scan All Pages button is present and enabled', () => {
        cy.contains('button', 'Scan All Pages').should('be.visible').and('not.be.disabled');
    });

    // ── Scan calls both GQL and preview action ────────────────────────────────

    it('scan calls GraphQL and the preview action', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.get('.pl-regen .pl-audit-table tbody tr').should('have.length.greaterThan', 0);
    });

    it('preview request body contains preview=true', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview').its('request.body').should('include', 'preview=true');
    });

    it('nodes are listed sorted A-Z by path', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.get('.pl-regen .pl-audit-table tbody tr td:nth-child(2)').then(($cells) => {
            const paths = [...$cells].map(el => el.textContent.trim());
            const sorted = [...paths].sort();
            expect(paths).to.deep.equal(sorted);
        });
    });

    // ── Auto-selection after scan ────────────────────────────────────────────

    it('all missing/stale/manual pills are auto-selected after scan', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        // Unselected missing/stale/manual pills should not exist — they're all auto-selected.
        // (pl-pill-has is fine to remain for languages that already have a correct vanity.)
        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-miss').should('not.exist');
        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-stale').should('not.exist');
        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-manual').should('not.exist');
        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-sel').should('have.length.greaterThan', 0);
    });

    // ── Pill toggle ──────────────────────────────────────────────────────────

    it('clicking a selected pill deselects it', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-sel').first().click();
        // That pill should no longer be selected
        cy.get('.pl-regen .pl-audit-row:not(.pl-row-ignored) .pl-pill-sel')
            .should('have.length.lessThan', 5);
    });

    // ── Confirm modal ────────────────────────────────────────────────────────

    it('clicking Regenerate opens the confirm modal', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.contains('button', 'Regenerate selected').click();
        cy.contains('Confirm regeneration').should('be.visible');
    });

    it('modal Cancel button closes the modal without generating', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.contains('button', 'Regenerate selected').click();
        cy.contains('Confirm regeneration').should('be.visible');

        // Click the Cancel button inside the modal footer
        cy.get('.modal-footer').contains('button', 'Cancel').click();
        cy.contains('Confirm regeneration').should('not.exist');
    });

    it('modal Regenerate button triggers the generate action with force=true', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        // Now intercept the actual generate call (non-preview)
        cy.interceptAction('actionRegen', 'action-regen.json');

        cy.contains('button', 'Regenerate selected').click();
        cy.get('.modal-footer').contains('button', 'Regenerate').click();

        cy.wait('@actionRegen').its('request.body').should('include', 'force=true');
    });

    // ── Report table ─────────────────────────────────────────────────────────

    it('report table appears after successful generation', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.interceptAction('actionRegen', 'action-regen.json');

        cy.contains('button', 'Regenerate selected').click();
        cy.get('.modal-footer').contains('button', 'Regenerate').click();
        cy.wait('@actionRegen');

        cy.contains('Regeneration report').should('be.visible');
        // Generation issues one POST per language chunk; each returns the
        // 2-entry action-regen.json fixture (EN + FR chunks → 4 report rows).
        cy.get('.pl-regen table').last().find('tbody tr').should('have.length', 4);
    });

    it('report table rows show "Created" action label', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.interceptAction('actionRegen', 'action-regen.json');

        cy.contains('button', 'Regenerate selected').click();
        cy.get('.modal-footer').contains('button', 'Regenerate').click();
        cy.wait('@actionRegen');

        cy.contains('Created').should('be.visible');
    });

    it('success status message appears after generation', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview');

        cy.interceptAction('actionRegen', 'action-regen.json');

        cy.contains('button', 'Regenerate selected').click();
        cy.get('.modal-footer').contains('button', 'Regenerate').click();
        cy.wait('@actionRegen');

        cy.contains('✓').should('be.visible');
    });

    // ── Bypass excluded ───────────────────────────────────────────────────────

    it('checking bypass excluded includes bypassExcluded=true in preview POST', () => {
        cy.interceptGraphql('gqlRegen', 'graphql-nodes.json');
        cy.interceptAction('actionPreview', 'action-preview.json');

        // Check the bypass checkbox before scanning
        cy.get('.pl-regen input[type="checkbox"]').first().check();

        cy.contains('button', 'Scan All Pages').click();
        cy.wait('@gqlRegen');
        cy.wait('@actionPreview').its('request.body').should('include', 'bypassExcluded=true');
    });
});
