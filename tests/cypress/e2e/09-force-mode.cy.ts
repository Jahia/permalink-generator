import {
    SITE_KEY,
    createPage,
    setSiteMode,
    setPageTitle,
    makeVanityManual,
    getVanityUrls,
    waitForVanityUrl,
    waitForVanityUrlContaining
} from '../support/permalinkgen'

/**
 * G20 / spec S12 (downgraded) — REAL FORCE mode overwrite of a MANUAL vanity.
 *
 * The old scenario-5 "FORCE" test never set j:permalinkGeneratorMode=FORCE; it deleted-then-recreated
 * vanities through Groovy. This spec sets the site to FORCE mode and drives the module's actual FORCE
 * branch (PGS:262 inert SMART guard): a title rename must OVERWRITE the manual vanity in place and keep
 * the old URL as a node (redirect), rather than being skipped (SMART) or deleted-and-recreated.
 */
const PAGE = `/sites/${SITE_KEY}/home/page-force`

describe('Scenario 9 — real FORCE mode overwrites a manual vanity (G20/S12)', () => {
    before(() => {
        cy.login()
        setSiteMode(SITE_KEY, 'FORCE')
        createPage(`/sites/${SITE_KEY}/home`, 'page-force', 'Force Original', 'Force Original')
    })

    after(() => {
        setSiteMode(SITE_KEY, 'SMART') // restore default so other specs are unaffected
        cy.logout()
    })

    it('page gets an initial auto vanity, then it is made manual', () => {
        waitForVanityUrl(PAGE, 'en').should('include', 'force-original')
        makeVanityManual(PAGE, 'en') // strip jmix:permalinkGenerated -> now a MANUAL vanity
    })

    it('FORCE mode: renaming the title OVERWRITES the manual vanity with the new slug', () => {
        setPageTitle(PAGE, 'en', 'Force Renamed')
        // FORCE mode must regenerate despite the vanity being manual (SMART would have skipped).
        waitForVanityUrlContaining(PAGE, 'en', 'force-renamed').then((url: string) => {
            expect(url).to.include('force-renamed')
        })
    })

    it('old manual URL is kept as a node (demoted redirect), NOT deleted', () => {
        getVanityUrls(PAGE).then((resp: any) => {
            const vanities = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            const oldOne = vanities.find((v: any) => v.language === 'en' && v.url.includes('force-original'))
            // Contract check for Stage 7: overwrite-in-place should demote, not remove, the old URL.
            expect(oldOne, 'old URL should survive as a redirect node').to.exist
            expect(oldOne.default, 'old URL must no longer be the default').to.eq(false)
        })
    })
})
