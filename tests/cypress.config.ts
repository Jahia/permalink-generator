import {defineConfig} from 'cypress'

export default defineConfig({
    screenshotsFolder: './results/screenshots',
    video: true,
    videosFolder: './results/videos',
    viewportWidth: 1366,
    viewportHeight: 768,
    watchForFileChanges: false,
    e2e: {
        setupNodeEvents(on, config) {
            require('./cypress/plugins/index.js')(on, config)
            return config
        },
        baseUrl: 'http://localhost:8080'
    },
    env: {}
})
