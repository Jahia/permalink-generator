import { createSite as jahiaCreateSite, deleteSite as jahiaDeleteSite, enableModule, addNode } from '@jahia/cypress'

export const SITE_KEY = 'plgentest'
export const SITE_KEY_AUDIT = 'plgenaudit' // used for scenario 4 (pages created before module enabled)
export const SITE_KEY_XSITE = 'plgenxsite' // second site B for the cross-site authz test (G1/S1)

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
        languages: 'en,fr',
    })
    enableModule('permalink-generator', SITE_KEY)
}

// Create the audit test site (module added AFTER pages exist)
export const createAuditSite = () => {
    jahiaCreateSite(SITE_KEY_AUDIT, {
        templateSet: 'empty-templates',
        serverName: 'localhost',
        locale: 'en',
        languages: 'en,fr',
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
        languages: 'en',
    })
    enableModule('permalink-generator', SITE_KEY_XSITE)
}

export const deleteTestSite = () => {
    jahiaDeleteSite(SITE_KEY)
}
export const deleteAuditSite = () => {
    jahiaDeleteSite(SITE_KEY_AUDIT)
}
export const deleteXSite = () => {
    jahiaDeleteSite(SITE_KEY_XSITE)
}

// Grant the site-administrator role (carrying siteAdminPermalinkGenerator) to a user SCOPED to a
// single site only — the one missing provisioning piece for the cross-site authz test (G1/S1).
export const grantSitePermalinkAdmin = (user: string, siteKey: string) => {
    cy.executeGroovy('groovy/permalinkgen/grantSitePermalinkAdmin.groovy', {
        USER: user,
        SITE_KEY: siteKey,
    })
}

// Set j:permalinkGeneratorMode (SMART|FORCE) on a site node (real FORCE-mode e2e, G20/S12).
export const setSiteMode = (siteKey: string, mode: 'SMART' | 'FORCE') => {
    cy.executeGroovy('groovy/permalinkgen/setSiteMode.groovy', {
        SITE_KEY: siteKey,
        MODE: mode,
    })
}

// Action URL of generatePermalinks.do for a site. It dispatches through Jahia's Render engine +
// security filter, enforcing setRequireAuthenticatedUser + the siteAdminPermalinkGenerator permission
// against the /sites/<siteKey> node — exactly the authz path under test. We use /cms/render: it gives
// the correct authorization semantics for a programmatic caller — an authorized caller gets 200, while
// anonymous and unprivileged callers both get 404 ("resource not available", Jahia's permission denial).
// (The panel itself posts to the /cms/editframe variant of this same .do because it runs inside the
// edit iframe; editframe serves anonymous callers a 200 edit-shell, which would mask the anon-denial
// assertion here, so /cms/render is the faithful endpoint for authz testing.) NB: neither path serializes
// the action's JSON ActionResult back to a raw cy.request — only a real browser XHR (patched by
// CsrfGuardJavascriptFilter) reads the body — so tests assert the HTTP status + the JCR side effect,
// not the response body.
export const actionUrl = (siteKey: string = SITE_KEY, lang = 'en') =>
    `/cms/render/default/${lang}/sites/${siteKey}.generatePermalinks.do`

// Resolve a node UUID by path.
export const getNodeUuid = (path: string): Cypress.Chainable<string> =>
    cy
        .apollo({queryFile: 'graphql/jcr/query/getNodeUuid.graphql', variables: {path}})
        .then((resp: any) => resp?.data?.jcr?.nodeByPath?.uuid as string)

// Fetch a valid OWASP CSRFGuard token for the CURRENT (cy.request cookie-jar) session.
// Every state-changing .do POST is guarded by org.jahia.modules.jahiacsrfguard: without a token the
// request is rejected HTTP 400 ("Required Token is missing from the Request") BEFORE reaching the
// action, so authn/authz can never be observed. In the real admin panel the CsrfGuardJavascriptFilter
// injects a script (served at /modules/CsrfServlet) that monkey-patches XMLHttpRequest to add the
// `CSRFTOKEN` header on every .do call (see javascript/src/utils/api.js). A programmatic cy.request has
// no such patch, so we replicate it: GET that same servlet script and parse the session master token
// out of it (`var s='CSRFTOKEN',o='<TOKEN>'`), then send it as the CSRFTOKEN header — faithfully
// traversing the CSRF filter with a genuine token, exactly as the panel does.
export const getCsrfToken = (): Cypress.Chainable<{name: string; value: string}> =>
    cy.request({url: '/modules/CsrfServlet', log: false}).then((res: Cypress.Response<string>) => {
        const js = String(res.body)
        // Token value: OWASP dash-grouped master token, e.g. 3LOQ-PUZ8-...-22MX
        const value = (js.match(/'([A-Z0-9]{4}(?:-[A-Z0-9]{4}){4,})'/) || [])[1]
        // Header name assigned immediately before the token (e.g. s='CSRFTOKEN',o='<token>')
        const name = (js.match(/=(?:'|")([A-Za-z][A-Za-z0-9_-]+)(?:'|"),\w=(?:'|")[A-Z0-9]{4}-/) ||
            [])[1]
        return {name: name || 'CSRFTOKEN', value: value || ''}
    })

// POST the REAL generatePermalinks.do action through Jahia's action dispatcher + security filter.
// Does NOT use the Groovy shortcut — this traverses authn/authz (and CSRF) for real.
export const postGeneratePermalinksAction = (
    url: string,
    nodeIds: string[],
    languages: string[],
    opts: { force?: boolean; preview?: boolean; bypassExcluded?: boolean } = {},
): Cypress.Chainable<Cypress.Response<any>> => {
    // Build the body EXACTLY as the panel's URLSearchParams does (javascript/src/components/RegenPanel.jsx):
    // repeated `nodeIds[]=` / `languages[]=` pairs. Cypress's object form-serializer re-brackets a key
    // that already ends in `[]` (producing `nodeIds[][]=`), so the action reads parameters.get("nodeIds[]")
    // as null → 400. Encoding the string by hand preserves the literal `nodeIds[]` key the action expects.
    const pairs: string[] = []
    nodeIds.forEach((n) => pairs.push(`nodeIds[]=${encodeURIComponent(n)}`))
    languages.forEach((l) => pairs.push(`languages[]=${encodeURIComponent(l)}`))
    if (opts.force) pairs.push('force=true')
    if (opts.preview) pairs.push('preview=true')
    if (opts.bypassExcluded) pairs.push('bypassExcluded=true')
    const body = pairs.join('&')
    return getCsrfToken().then((token) =>
        cy.request({
            method: 'POST',
            url,
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded',
                [token.name]: token.value,
            },
            body,
            failOnStatusCode: false,
        }),
    )
}

// Create a jnt:page with multilingual titles
export const createPage = (parentPath: string, name: string, titleEn: string, titleFr: string) => {
    addNode({
        parentPathOrId: parentPath,
        name,
        primaryNodeType: 'jnt:page',
        properties: [
            { name: 'jcr:title', value: titleEn, language: 'en' },
            { name: 'jcr:title', value: titleFr, language: 'fr' },
            { name: 'j:templateName', value: 'empty' },
        ],
        children: [{ name: 'pagecontent', primaryNodeType: 'jnt:contentList' }],
    })
}

// Publish a node
export const publishNode = (pathOrId: string, waitMs: number = 3000) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/publishNode.graphql',
        variables: { pathOrId, languages: ['en', 'fr'], publishSubNodes: true, includeSubTree: true },
    })
    cy.wait(waitMs)
}

// Set a page title in a specific language (for rename scenario)
export const setPageTitle = (path: string, language: string, title: string) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/setPageTitle.graphql',
        variables: { path, language, title },
    })
}

// Move a page to a new parent
export const movePage = (pathOrId: string, destParentPathOrId: string) => {
    cy.apollo({
        mutationFile: 'graphql/jcr/mutation/movePage.graphql',
        variables: { pathOrId, destParentPathOrId },
    })
}

// Make an auto-generated vanity URL "manual" by removing jmix:permalinkGenerated mixin.
// The module uses this mixin to identify auto-generated vanities; without it the URL is treated as manual.
export const makeVanityManual = (contentPath: string, language: string) => {
    cy.executeGroovy('groovy/permalinkgen/makeVanityManual.groovy', {
        PATH: contentPath,
        LANG: language,
    })
}

// Restore jmix:permalinkGenerated mixin on an existing vanity (reverses makeVanityManual)
export const makeVanityAutoGenerated = (contentPath: string, language: string) => {
    cy.executeGroovy('groovy/permalinkgen/makeVanityAutoGenerated.groovy', {
        PATH: contentPath,
        LANG: language,
    })
}

// Create a manual vanity URL (without jmix:permalinkGenerated) via Groovy
export const createManualVanityUrl = (contentPath: string, language: string, url: string, siteKey: string) => {
    cy.executeGroovy('groovy/permalinkgen/createManualVanityUrl.groovy', {
        CONTENT_PATH: contentPath,
        LANG: language,
        VANITY_URL: url,
        SITE_KEY: siteKey,
    })
}

// Call PermalinkGeneratorService.generateVanityForNodeIds via Groovy (bypasses HTTP render context).
// paths: array of JCR paths; langs: array of language codes; force: whether to overwrite manual vanities.
export const generatePermalinks = (paths: string[], langs: string[], force = false) => {
    cy.executeGroovy('groovy/permalinkgen/generatePermalinks.groovy', {
        PATHS: paths.join(','),
        LANGS: langs.join(','),
        FORCE: String(force),
    })
}

/**
 * Call the *.generatePermalinks.do HTTP action endpoint directly with preview=true.
 * Asserts HTTP 200, validates the response shape, and yields the parsed body.
 * Use this in tests that need to verify the preview response (willChange, computedUrl, etc.)
 * rather than side-effecting the repository.
 *
 * The cy.intercept assertion ensures failures in the endpoint are surfaced immediately
 * rather than timing-out downstream in a waitForVanityUrl call.
 */
export const previewPermalinks = (nodePath: string, langs: string[]): Cypress.Chainable<any> => {
    const alias = 'previewPermalinksRequest'
    cy.intercept('POST', '**/generatePermalinks.do').as(alias)

    cy.request({
        method: 'POST',
        url: `${nodePath}.generatePermalinks.do`,
        qs: {preview: 'true', languages: langs.join(',')},
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD') || 'root1234'},
        failOnStatusCode: false
    }).then((response) => {
        // Surface HTTP-level failures immediately with a clear message
        expect(response.status, `generatePermalinks.do must return HTTP 200 (got ${response.status})`).to.eq(200)
    })

    // Also validate via the intercepted request so response shape errors are caught early
    return cy.wait(`@${alias}`).then((interception) => {
        expect(interception.response?.statusCode, 'intercepted generatePermalinks.do must be HTTP 200').to.eq(200)
        return interception.response?.body
    })
}

// Query vanity URLs for a node
export const getVanityUrls = (path: string): Cypress.Chainable<any> => {
    return cy.apollo({
        queryFile: 'graphql/jcr/query/getVanityUrls.graphql',
        variables: { path },
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
            if (Date.now() >= end)
                throw new Error(
                    `waitForVanityUrl: no active default vanity for [${language}] on ${path} after ${timeoutMs}ms`,
                )
            return cy.wait(interval).then(() => attempt()) as any
        })
    }
    return attempt()
}

// Poll until the active default vanity URL for a language CONTAINS the given substring.
// Use after a title rename to wait for the module to fire and update the URL.
export const waitForVanityUrlContaining = (
    path: string,
    language: string,
    substring: string,
    timeoutMs: number = 15000,
) => {
    const interval = 2000
    const end = Date.now() + timeoutMs
    const attempt = (): Cypress.Chainable<string> => {
        return getVanityUrls(path).then((resp: any) => {
            const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            const match = vanities.find(
                (v: any) => v.language === language && v.active && v.default && v.url.includes(substring),
            )
            if (match) return match.url as string
            if (Date.now() >= end)
                throw new Error(
                    `waitForVanityUrlContaining: no vanity for [${language}] on ${path} containing "${substring}" after ${timeoutMs}ms`,
                )
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
        expect(
            match.url,
            `Vanity URL should still be ${expectedUrl} (SMART mode should not overwrite manual)`,
        ).to.equal(expectedUrl)
    })
}
