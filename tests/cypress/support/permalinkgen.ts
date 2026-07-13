import {createSite as jahiaCreateSite, deleteSite as jahiaDeleteSite, enableModule, addNode} from '@jahia/cypress'

export const SITE_KEY = 'plgentest'
export const SITE_KEY_AUDIT = 'plgenaudit'  // used for scenario 4 (pages created before module enabled)
export const SITE_KEY_XSITE = 'plgenxsite'  // second site B for the cross-site authz test (G1/S1)

// Site-A-scoped administrator (NOT root) used by the cross-site authorization escalation test.
export const SITE_A_ADMIN = 'plgenSiteaAdmin'
export const SITE_A_ADMIN_PW = 'Permalink123!'

// Admin settings URL for a site
export const adminUrl = (siteKey: string = SITE_KEY) =>
    `/jahia/administration/${siteKey}/permalinkGeneratorSiteSettings`

// Create the main test site with EN + FR, module enabled from the start
export const createTestSite = () => {
    jahiaCreateSite(SITE_KEY, {
        templateSet: 'empty-templates',
        serverName: 'localhost',
        locale: 'en',
        languages: 'en,fr'
    })
    enableModule('permalink-generator', SITE_KEY)
}

// Create the audit test site (module added AFTER pages exist)
export const createAuditSite = () => {
    jahiaCreateSite(SITE_KEY_AUDIT, {
        templateSet: 'empty-templates',
        serverName: 'localhost',
        locale: 'en',
        languages: 'en,fr'
    })
    // Do NOT enable permalink-generator yet — pages are created first in the test
}

// Create the second site B (module enabled) that holds MANUAL editorial vanities a site-A admin
// must NOT be able to overwrite. Pages/vanities are seeded by the calling spec.
export const createXSite = () => {
    jahiaCreateSite(SITE_KEY_XSITE, {
        templateSet: 'empty-templates',
        serverName: 'localhost',
        locale: 'en',
        languages: 'en'
    })
    enableModule('permalink-generator', SITE_KEY_XSITE)
}

export const deleteTestSite = () => { jahiaDeleteSite(SITE_KEY) }
export const deleteAuditSite = () => { jahiaDeleteSite(SITE_KEY_AUDIT) }
export const deleteXSite = () => { jahiaDeleteSite(SITE_KEY_XSITE) }

// Grant the site-administrator role (carrying siteAdminPermalinkGenerator) to a user SCOPED to a
// single site only — the one missing provisioning piece for the cross-site authz test (G1/S1).
export const grantSitePermalinkAdmin = (user: string, siteKey: string) => {
    cy.executeGroovy('groovy/permalinkgen/grantSitePermalinkAdmin.groovy', {
        USER: user,
        SITE_KEY: siteKey
    })
}

// Set j:permalinkGeneratorMode (SMART|FORCE) on a site node (real FORCE-mode e2e, G20/S12).
export const setSiteMode = (siteKey: string, mode: 'SMART' | 'FORCE') => {
    cy.executeGroovy('groovy/permalinkgen/setSiteMode.groovy', {
        SITE_KEY: siteKey,
        MODE: mode
    })
}

// Real render-servlet URL of the generatePermalinks.do action for a site (matches the URL the admin
// panel posts to: window.location.pathname without the trailing '.<template>.html' + '.generatePermalinks.do').
export const actionUrl = (siteKey: string = SITE_KEY, lang = 'en') =>
    `/cms/editframe/default/${lang}/sites/${siteKey}.generatePermalinks.do`

// Resolve a node UUID by path.
export const getNodeUuid = (path: string): Cypress.Chainable<string> =>
    cy.apollo({queryFile: 'graphql/jcr/query/getNodeUuid.graphql', variables: {path}})
        .then((resp: any) => resp?.data?.jcr?.nodeByPath?.uuid as string)

// POST the REAL generatePermalinks.do action through Jahia's action dispatcher + security filter.
// Does NOT use the Groovy shortcut — this traverses authn/authz for real.
export const postGeneratePermalinksAction = (
    url: string,
    nodeIds: string[],
    languages: string[],
    opts: {force?: boolean; preview?: boolean; bypassExcluded?: boolean} = {}
): Cypress.Chainable<Cypress.Response<any>> => {
    const form: Record<string, string | string[]> = {
        'nodeIds[]': nodeIds,
        'languages[]': languages
    }
    if (opts.force) form.force = 'true'
    if (opts.preview) form.preview = 'true'
    if (opts.bypassExcluded) form.bypassExcluded = 'true'
    return cy.request({
        method: 'POST',
        url,
        form: true,
        headers: {'X-Requested-With': 'XMLHttpRequest'},
        body: form,
        failOnStatusCode: false
    })
}

// Create a jnt:page with multilingual titles
export const createPage = (parentPath: string, name: string, titleEn: string, titleFr: string) => {
    addNode({
        parentPathOrId: parentPath,
        name,
        primaryNodeType: 'jnt:page',
        properties: [
            {name: 'jcr:title', value: titleEn, language: 'en'},
            {name: 'jcr:title', value: titleFr, language: 'fr'},
            {name: 'j:templateName', value: 'empty'}
        ],
        children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
    })
}

// Publish a node
export const publishNode = (pathOrId: string, waitMs: number = 3000) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/publishNode.graphql',
        variables: {pathOrId, languages: ['en', 'fr'], publishSubNodes: true, includeSubTree: true}
    })
    cy.wait(waitMs)
}

// Set a page title in a specific language (for rename scenario)
export const setPageTitle = (path: string, language: string, title: string) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/setPageTitle.graphql',
        variables: {path, language, title}
    })
}

// Move a page to a new parent
export const movePage = (pathOrId: string, destParentPathOrId: string) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/movePage.graphql',
        variables: {pathOrId, destParentPathOrId}
    })
}

// Make an auto-generated vanity URL "manual" by removing jmix:permalinkGenerated mixin.
// The module uses this mixin to identify auto-generated vanities; without it the URL is treated as manual.
export const makeVanityManual = (contentPath: string, language: string) => {
    cy.executeGroovy('groovy/permalinkgen/makeVanityManual.groovy', {
        PATH: contentPath,
        LANG: language
    })
}

// Restore jmix:permalinkGenerated mixin on an existing vanity (reverses makeVanityManual)
export const makeVanityAutoGenerated = (contentPath: string, language: string) => {
    cy.executeGroovy('groovy/permalinkgen/makeVanityAutoGenerated.groovy', {
        PATH: contentPath,
        LANG: language
    })
}

// Create a manual vanity URL (without jmix:permalinkGenerated) via Groovy
export const createManualVanityUrl = (contentPath: string, language: string, url: string, siteKey: string) => {
    cy.executeGroovy('groovy/permalinkgen/createManualVanityUrl.groovy', {
        CONTENT_PATH: contentPath,
        LANG: language,
        VANITY_URL: url,
        SITE_KEY: siteKey
    })
}

// Call PermalinkGeneratorService.generateVanityForNodeIds via Groovy (bypasses HTTP render context).
// paths: array of JCR paths; langs: array of language codes; force: whether to overwrite manual vanities.
export const generatePermalinks = (paths: string[], langs: string[], force = false) => {
    cy.executeGroovy('groovy/permalinkgen/generatePermalinks.groovy', {
        PATHS: paths.join(','),
        LANGS: langs.join(','),
        FORCE: String(force)
    })
}

// Query vanity URLs for a node
export const getVanityUrls = (path: string): Cypress.Chainable<any> => {
    return cy.apollo({
        queryFile: 'graphql/jcr/query/getVanityUrls.graphql',
        variables: {path}
    })
}

// Poll until the node has an active default vanity URL for the given language.
// Returns the URL string via the .then() chain.
export const waitForVanityUrl = (path: string, language: string, timeoutMs: number = 15000) => {
    const interval = 2000
    const end = Date.now() + timeoutMs
    const attempt = (): Cypress.Chainable<string | null> => {
        return getVanityUrls(path).then((resp: any) => {
            const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            const match = vanities.find((v: any) => v.language === language && v.active && v.default)
            if (match) return match.url as string
            if (Date.now() >= end) throw new Error(`waitForVanityUrl: no active default vanity for [${language}] on ${path} after ${timeoutMs}ms`)
            return cy.wait(interval).then(() => attempt()) as any
        })
    }
    return attempt()
}

// Poll until the active default vanity URL for a language CONTAINS the given substring.
// Use after a title rename to wait for the module to fire and update the URL.
export const waitForVanityUrlContaining = (path: string, language: string, substring: string, timeoutMs: number = 15000) => {
    const interval = 2000
    const end = Date.now() + timeoutMs
    const attempt = (): Cypress.Chainable<string> => {
        return getVanityUrls(path).then((resp: any) => {
            const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            const match = vanities.find((v: any) => v.language === language && v.active && v.default && v.url.includes(substring))
            if (match) return match.url as string
            if (Date.now() >= end) throw new Error(`waitForVanityUrlContaining: no vanity for [${language}] on ${path} containing "${substring}" after ${timeoutMs}ms`)
            return cy.wait(interval).then(() => attempt()) as any
        })
    }
    return attempt()
}

// Assert NO active default vanity for a language (used for SMART mode checks)
export const assertNoVanityUrlChange = (path: string, language: string, expectedUrl: string) => {
    getVanityUrls(path).then((resp: any) => {
        const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
        const match = vanities.find((v: any) => v.language === language && v.active && v.default)
        expect(match, `Expected a vanity URL for [${language}] on ${path}`).to.exist
        expect(match.url, `Vanity URL should still be ${expectedUrl} (SMART mode should not overwrite manual)`).to.equal(expectedUrl)
    })
}
