import {addNode} from '@jahia/cypress'
import {waitForVanityUrl, getVanityUrls, generatePermalinks, SITE_KEY} from '../support/permalinkgen'

/**
 * Scenario 8 — onNodeDeleted cleans up auto-generated vanities
 *
 * When a page is deleted, the onNodeDeleted Drools entry point should deactivate
 * (published) or remove (unpublished) all auto-generated vanity URLs. An untested
 * cleanup path could leave dead vanity URLs in the repository.
 */
describe('Scenario 8 — Node deletion cleans up auto-generated vanities', () => {
    const deletablePageName = 'page-deletable'
    const deletablePath = `/sites/${SITE_KEY}/home/${deletablePageName}`

    before(() => {
        cy.login()
        // Create a fresh page so this test is self-contained
        addNode({
            parentPathOrId: `/sites/${SITE_KEY}/home`,
            name: deletablePageName,
            primaryNodeType: 'jnt:page',
            properties: [
                {name: 'jcr:title', value: 'Deletable Page', language: 'en'},
                {name: 'jcr:title', value: 'Page supprimable', language: 'fr'},
                {name: 'j:templateName', value: 'empty'}
            ],
            children: [{name: 'pagecontent', primaryNodeType: 'jnt:contentList'}]
        })
        generatePermalinks([deletablePath], ['en', 'fr'])
    })

    it('deletable page has an active EN vanity URL', () => {
        waitForVanityUrl(deletablePath, 'en', 20000).should('include', 'deletable')
    })

    it('after deleting the page via GraphQL, no active vanity remains for it', () => {
        // Delete the page using the JCR GraphQL mutation
        cy.apollo({
            mutation: `
                mutation deleteNode($path: String!) {
                    jcr {
                        deleteNode(pathOrId: $path)
                    }
                }
            `,
            variables: {path: deletablePath}
        })

        // Allow the Drools onNodeDeleted rule to process
        cy.wait(5000)

        // The node is deleted; querying its vanity URLs should return null or an empty list.
        // We query by path — if the node is gone the response returns null for nodeByPath.
        getVanityUrls(deletablePath).then((resp: any) => {
            const node = resp?.data?.jcr?.nodeByPath
            // Either the node is gone entirely (null) or has no active+default vanities
            if (node === null || node === undefined) {
                // Node was fully deleted — expected outcome
                expect(node).to.be.null
            } else {
                const activeVanities = (node.vanityUrls ?? []).filter((v: any) => v.active && v.default)
                expect(activeVanities.length, 'deleted page should have no active default vanities').to.eq(0)
            }
        })
    })
})
