describe('Permalink Generator — Force Regeneration', () => {
    const siteKey = Cypress.env('JAHIA_SITE_KEY');

    before(() => {
        cy.login(Cypress.env('JAHIA_USERNAME'), Cypress.env('JAHIA_PASSWORD'));
    });

    beforeEach(() => {
        cy.visitPermalinkSettings(siteKey);
    });

    it('renders the force regen panel with scan button', () => {
        cy.get('.pl-regen h3').should('be.visible');
        cy.get('#plRBtnScan').should('be.visible').and('not.be.disabled');
        cy.get('#plRBypass').should('exist');
        cy.get('#plRResults').should('not.be.visible');
    });

    it('scans all pages and shows summary', () => {
        cy.get('#plRBtnScan').click();
        cy.get('#plRBtnScan').should('be.disabled');
        cy.get('#plRScanStatus', { timeout: 60000 }).should('match', /scanned/i);
    });

    it('scan with bypass excluded shows results', () => {
        cy.get('#plRBypass').check();
        cy.get('#plRBtnScan').click();
        cy.get('#plRScanStatus', { timeout: 60000 }).should('match', /scanned/i);
    });

    it('shows regen results table when there are stale/manual/missing vanities', () => {
        cy.get('#plRBtnScan').click();
        cy.get('#plRScanStatus', { timeout: 60000 }).should('match', /scanned/i);

        cy.get('body').then(($body) => {
            if ($body.find('#plRResults').is(':visible')) {
                cy.get('#plRSummary').should('not.be.empty');
                cy.get('#plRBtnGenerate').should('be.visible');
            }
        });
    });

    it('confirm modal appears and can be dismissed', () => {
        cy.get('#plRBtnScan').click();
        cy.get('#plRScanStatus', { timeout: 60000 }).should('match', /scanned/i);

        cy.get('body').then(($body) => {
            if ($body.find('#plRResults').is(':visible')) {
                // Auto-selected rows (stale) should have at least one selected
                cy.get('#plRSelCount').invoke('text').then((count) => {
                    if (parseInt(count, 10) > 0) {
                        cy.get('#plRBtnGenerate').should('not.be.disabled').click();
                        // Confirm modal should appear
                        cy.get('#plRConfirmModal').should('be.visible');
                        cy.get('#plRConfirmModal .modal-header h3').should('not.be.empty');
                        cy.get('#plRConfirmModal .modal-body p').should('not.be.empty');
                        // Dismiss via cancel button
                        cy.get('#plRConfirmModal [data-dismiss="modal"]').first().click();
                        cy.get('#plRConfirmModal').should('not.be.visible');
                    }
                });
            }
        });
    });

    it('regenerates vanities and shows report', () => {
        cy.get('#plRBtnScan').click();
        cy.get('#plRScanStatus', { timeout: 60000 }).should('match', /scanned/i);

        cy.get('body').then(($body) => {
            if ($body.find('#plRResults').is(':visible')) {
                cy.get('#plRSelCount').invoke('text').then((count) => {
                    if (parseInt(count, 10) > 0) {
                        cy.get('#plRBtnGenerate').click();
                        cy.get('#plRConfirmModal').should('be.visible');
                        cy.get('#plRConfirmOk').click();
                        // Wait for generation to complete
                        cy.get('#plRGenStatus', { timeout: 30000 }).should('match', /regenerated|URLs/i);
                    }
                });
            }
        });
    });
});
