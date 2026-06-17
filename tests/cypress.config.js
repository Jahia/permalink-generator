const { defineConfig } = require('cypress');

module.exports = defineConfig({
    e2e: {
        baseUrl: process.env.CYPRESS_baseUrl || 'http://localhost:8080',
        specPattern: 'cypress/e2e/**/*.cy.js',
        supportFile: 'cypress/support/e2e.js',
        viewportWidth: 1440,
        viewportHeight: 900,
        defaultCommandTimeout: 15000,
        requestTimeout: 15000,
        env: {
            JAHIA_USERNAME: 'root',
            JAHIA_PASSWORD: 'root1234',
            JAHIA_SITE_KEY: 'digitall',
        },
    },
});
