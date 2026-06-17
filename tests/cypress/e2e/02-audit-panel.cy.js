describe('Permalink Generator — Missing Permalink Audit', () => {
    const siteKey = Cypress.env('JAHIA_SITE_KEY');
    const sitePath = `/sites/${siteKey}`;

    before(() => {
        cy.login(Cypress.env('JAHIA_USERNAME'), Cypress.env('JAHIA_PASSWORD'));
    });

    beforeEach(() => {
        cy.visitPermalinkSettings(siteKey);
    });

    it('renders the audit panel with scan button', () => {
        cy.get('.pl-audit h3').should('be.visible');
        cy.get('#plAuditPath').should('have.value', sitePath);
        cy.get('#plBtnScan').should('be.visible').and('not.be.disabled');
        cy.get('#plAuditResults').should('not.be.visible');
    });

    it('requires path to start with /sites/', () => {
        cy.get('#plAuditPath').clear().type('/invalid/path');
        cy.get('#plBtnScan').click();
        cy.get('#plScanStatus').should('not.be.empty');
        cy.get('#plAuditResults').should('not.be.visible');
    });

    it('scans and shows results or all-good message', () => {
        cy.get('#plAuditPath').should('have.value', sitePath);
        cy.get('#plBtnScan').click();
        // Scan status updates during scan
        cy.get('#plScanStatus', { timeout: 30000 }).should('not.be.empty');
        // Either results table or "all good" in status
        cy.get('#plScanStatus', { timeout: 30000 }).then(($el) => {
            const text = $el.text();
            expect(text).to.match(/scanned|up to date/i);
        });
    });

    it('shows results table after scan with missing nodes', () => {
        cy.get('#plBtnScan').click();
        cy.get('#plScanStatus', { timeout: 30000 }).should('match', /scanned/i);

        // If there are missing nodes, the table is visible
        cy.get('body').then(($body) => {
            if ($body.find('#plAuditResults').css('display') !== 'none') {
                cy.get('#plAuditTable').should('be.visible');
                cy.get('#plAuditTbody tr').should('have.length.greaterThan', 0);
                cy.get('#plAuditSummary').should('not.be.empty');
                cy.get('#plBtnGenerate').should('be.visible');
            }
        });
    });

    it('select-all checkbox toggles all missing pills', () => {
        cy.get('#plBtnScan').click();
        cy.get('#plScanStatus', { timeout: 30000 }).should('match', /scanned/i);

        cy.get('body').then(($body) => {
            if ($body.find('#plAuditResults').is(':visible')) {
                cy.get('#plSelectAll').check();
                cy.get('#plSelCount').invoke('text').then((count) => {
                    expect(parseInt(count, 10)).to.be.greaterThan(0);
                });
                cy.get('#plBtnGenerate').should('not.be.disabled');

                cy.get('#plSelectAll').uncheck();
                cy.get('#plSelCount').should('have.text', '0');
                cy.get('#plBtnGenerate').should('be.disabled');
            }
        });
    });
});
