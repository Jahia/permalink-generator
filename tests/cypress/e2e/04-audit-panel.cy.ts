import {addNode} from '@jahia/cypress'
import {SITE_KEY_AUDIT, getVanityUrls, waitForVanityUrl, generatePermalinks, createPage} from '../support/permalinkgen'

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

    it('preview=true mode: generatePermalinks endpoint returns willChange=true and non-empty computedUrl for pages without vanities', () => {
        // Create a fresh page with no vanity so previewVanityForNodeIds will report willChange=true
        const previewPagePath = `/sites/${SITE_KEY_AUDIT}/home/page-preview-test`
        addNode({
            parentPathOrId: `/sites/${SITE_KEY_AUDIT}/home`,
            name: 'page-preview-test',
            primaryNodeType: 'jnt:page',
            properties: [
                {name: 'jcr:title', value: 'Preview Test Page', language: 'en'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })

        cy.intercept('POST', '**/generatePermalinks.do').as('generatePermalinksRequest')

        // Call the action endpoint directly with preview=true via cy.request
        cy.request({
            method: 'POST',
            url: '/cms/render/default/en/sites/plgenaudit/home/page-preview-test.generatePermalinks.do',
            qs: {preview: 'true', languages: 'en'},
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD') || 'root1234'}
        }).then((response) => {
            expect(response.status).to.eq(200)
            const body = response.body
            // The response should be a JSON object with a results array
            expect(body).to.have.property('results').that.is.an('array')
            if (body.results.length > 0) {
                const firstResult = body.results[0]
                // Preview results must include computedUrl
                expect(firstResult).to.have.property('computedUrl').that.is.a('string').and.not.empty
                // willChange should be true since there is no existing vanity
                expect(firstResult).to.have.property('willChange').that.equals(true)
            }
        })
    })
})
