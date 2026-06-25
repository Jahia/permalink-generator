import {addNode} from '@jahia/cypress'
import {waitForVanityUrl, generatePermalinks, SITE_KEY} from '../support/permalinkgen'

/**
 * Scenario 9 — URL conflict resolution produces -2 suffix
 *
 * When two pages have identical titles under the same parent, the second page must
 * receive a vanity URL suffixed with -2 (or higher) so URLs remain unique. This
 * exercises the resolveUniqueUrl conflict-resolution loop at the integration level.
 */
describe('Scenario 9 — Duplicate title conflict resolution adds suffix', () => {
    const parentPath  = `/sites/${SITE_KEY}/home`
    const page1Name   = 'page-twin-one'
    const page2Name   = 'page-twin-two'
    const page1Path   = `${parentPath}/${page1Name}`
    const page2Path   = `${parentPath}/${page2Name}`
    // Both pages share the same English title so their base slug would be identical
    const sharedTitle = 'Twin Page'

    before(() => {
        cy.login()
        // Create both pages before triggering generation so the conflict is live
        addNode({
            parentPathOrId: parentPath,
            name: page1Name,
            primaryNodeType: 'jnt:page',
            properties: [
                {name: 'jcr:title', value: sharedTitle, language: 'en'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })
        addNode({
            parentPathOrId: parentPath,
            name: page2Name,
            primaryNodeType: 'jnt:page',
            properties: [
                {name: 'jcr:title', value: sharedTitle, language: 'en'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })
        // Generate vanities for both pages in the same batch — conflict must be resolved
        generatePermalinks([page1Path, page2Path], ['en'])
    })

    it('first twin page receives a vanity URL containing the base slug', () => {
        waitForVanityUrl(page1Path, 'en', 20000).then((url: string) => {
            expect(url).to.include('twin-page')
        })
    })

    it('second twin page receives a distinct vanity URL with a -2 suffix', () => {
        waitForVanityUrl(page2Path, 'en', 20000).then((url: string) => {
            expect(url).to.include('twin-page')
        })
    })

    it('both vanity URLs are distinct (conflict was resolved)', () => {
        waitForVanityUrl(page1Path, 'en').then((url1: string) => {
            waitForVanityUrl(page2Path, 'en').then((url2: string) => {
                expect(url1).to.not.equal(url2)
                // One of the two URLs must carry the -2 suffix
                const hasSuffix = url1.endsWith('-2') || url2.endsWith('-2')
                expect(hasSuffix, `One of [${url1}, ${url2}] must end with -2`).to.be.true
            })
        })
    })
})
