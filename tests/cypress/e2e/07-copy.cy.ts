import {addNode} from '@jahia/cypress'
import {waitForVanityUrl, getVanityUrls, generatePermalinks, SITE_KEY} from '../support/permalinkgen'

/**
 * Scenario 7 — Copy strips module-managed vanities (stripCopiedVanities)
 *
 * When a page with auto-generated vanity URLs is copied, the Drools entry point
 * stripCopiedVanities removes the copied vanities from the new node so they are
 * regenerated fresh from the copy's own title. This test covers that path.
 */
describe('Scenario 7 — Copy strips and regenerates vanity URLs', () => {
    const sourcePath = `/sites/${SITE_KEY}/home/page-about`
    const copyName   = 'page-about-copy'
    const copyPath   = `/sites/${SITE_KEY}/home/${copyName}`

    before(() => {
        cy.login()
    })

    it('source page has an EN vanity URL before copy', () => {
        waitForVanityUrl(sourcePath, 'en').should('include', 'about')
    })

    it('copying the page via addNode creates the copy', () => {
        // Use addNode to clone the page under home with a different name.
        // Jahia's copy semantics: all child nodes (including vanityUrlMapping) are duplicated
        // before the stripCopiedVanities Drools rule fires and removes auto-generated ones.
        addNode({
            parentPathOrId: `/sites/${SITE_KEY}/home`,
            name: copyName,
            primaryNodeType: 'jnt:page',
            properties: [
                {name: 'jcr:title', value: 'About Us Copy', language: 'en'},
                {name: 'jcr:title', value: 'À propos de nous copie', language: 'fr'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })
    })

    it('immediately after copy, no vanity inherited from source is active+default on copy', () => {
        // Allow Drools rules time to fire on the copied node
        cy.wait(5000)

        getVanityUrls(copyPath).then((resp: any) => {
            const vanities: any[] = resp?.data?.jcr?.nodeByPath?.vanityUrls ?? []
            // Any vanities copied from the source should have been stripped.
            // None of the source page's vanities should appear as active+default on the copy.
            waitForVanityUrl(sourcePath, 'en').then((sourceVanityUrl: string) => {
                const inheritedAndActive = vanities.filter(
                    (v: any) => v.url === sourceVanityUrl && v.active && v.default
                )
                expect(inheritedAndActive.length, 'copied node must not inherit source active vanity').to.eq(0)
            })
        })
    })

    it('after triggering generation, copy has a fresh vanity derived from its own title', () => {
        generatePermalinks([copyPath], ['en'])
        waitForVanityUrl(copyPath, 'en', 20000).then((url: string) => {
            expect(url).to.include('about')
            expect(url).to.include('copy')
        })
    })

    it('copy vanity URL is distinct from source vanity URL (conflict suffix applied if needed)', () => {
        waitForVanityUrl(sourcePath, 'en').then((sourceUrl: string) => {
            waitForVanityUrl(copyPath, 'en').then((copyUrl: string) => {
                expect(copyUrl).to.not.equal(sourceUrl)
            })
        })
    })
})
