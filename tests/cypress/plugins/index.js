module.exports = (on, config) => {
    require('cypress-terminal-report/src/installLogsPrinter')(on)
    require('@jahia/cypress/dist/plugins/registerPlugins').registerPlugins(on, config)
    return config
}
