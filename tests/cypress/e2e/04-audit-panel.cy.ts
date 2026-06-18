import {SITE_KEY_AUDIT, getVanityUrls, waitForVanityUrl, generatePermalinks} from '../support/permalinkgen'

const ALPHA = `/sites/${SITE_KEY_AUDIT}/home/page-alpha`
const BETA  = `/sites/${SITE_KEY_AUDIT}/home/page-beta`

describe('Scenario 4 — Audit generates missing vanity URLs', () => {
    before(() => {
        cy.login()
    })

    it('pages in plgenaudit have no vanity URLs before generation', () => {
        getVanityUrls(ALPHA).then((resp: any) => {
            const active = (resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []).filter((v: any) => v.active)
            expect(active.length, 'page-alpha should have no active vanity URLs').to.eq(0)
        })
    })

    it('calling generatePermalinks generates vanity URLs for pages', () => {
        generatePermalinks([ALPHA, BETA], ['en', 'fr'])
    })

    it('page-alpha now has EN vanity URL after generation', () => {
        waitForVanityUrl(ALPHA, 'en', 15000)
            .then((url: string) => { expect(url).to.include('alpha') })
    })

    it('page-alpha now has FR vanity URL after generation', () => {
        waitForVanityUrl(ALPHA, 'fr', 15000)
            .then((url: string) => { expect(url).to.exist })
    })

    it('page-beta now has EN vanity URL after generation', () => {
        waitForVanityUrl(BETA, 'en', 15000)
            .then((url: string) => { expect(url).to.include('beta') })
    })
})
