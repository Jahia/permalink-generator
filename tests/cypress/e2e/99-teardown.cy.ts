import {deleteTestSite, deleteAuditSite, deleteXSite} from '../support/permalinkgen'

describe('Teardown', () => {
    before(() => {
        cy.login()
    })

    it('deletes test site plgentest', () => {
        deleteTestSite()
    })

    it('deletes audit test site plgenaudit', () => {
        deleteAuditSite()
    })

    it('deletes cross-site authz test site plgenxsite', () => {
        deleteXSite()
    })
})
