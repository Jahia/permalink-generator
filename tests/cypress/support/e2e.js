require('cypress-terminal-report/src/installLogsCollector')()
require('@jahia/cypress/dist/support/registerSupport').registerSupport()

Cypress.on('uncaught:exception', () => false)

