# permalink-generator
> Automatically generates and maintains permanent vanity URLs for displayable content in Jahia.

## Installation

Install from the Jahia store or follow the [module installation guide](https://academy.jahia.com/training-kb/tutorials/administrators/installing-a-module).

## How it works

The module listens to JCR events and manages vanity URLs automatically. All module-generated vanities are tagged with the `jmix:permalinkGenerated` mixin, distinguishing them from manually-created vanities (which are never touched).

### URL construction

1. Check the module is enabled on the site
2. Skip home pages and non-displayable nodes
3. If the direct parent has an active default vanity → use it as base path + slugified title of current node
4. If the parent has no vanity → rebuild full path by slugifying each ancestor's title
5. Prepend language code if not the default language (e.g. `/fr/...`)
6. On URL conflict → append `-2`, `-3`, … up to 10 attempts

### Example

Site structure: `Home` / `Section` / `Sub-page`

- `Section` vanity: `/section`
- `Sub-page` vanity: `/section/sub-page`
- French (non-default): `/fr/section/sub-page`

### Vanity lifecycle

| Event | Unpublished module-managed vanity | Published module-managed vanity | Manual vanity |
|---|---|---|---|
| **Title change** | Deleted, new one created | Kept as active redirect (`j:default=false`), new one created | Untouched |
| **Page move** | Deleted, new one created (slug preserved, prefix updated) | Kept as active redirect, new one created | Untouched |
| **Page copy** | Stripped from the copy | Stripped from the copy | Untouched |
| **Page delete** | Deleted | Deactivated (`j:active=false`) | Untouched |

Published vanities are never deleted — they stay active as redirects so old URLs never 404.

### Propagation on rename

When a page title changes, all descendant nav-menu pages have their vanities recomputed automatically. Processing is depth-first: each child sees its parent's updated vanity before computing its own.

### Propagation on move

On move, the slug of each page is preserved — only the prefix changes. Descendants are updated recursively in the same depth-first order.

## Dependencies

- Jahia 8.x
- [slugify](https://github.com/slugify/slugify) 2.4
- Jahia `seo` module

## Site settings & admin panel

An administration panel is available under **Site Settings → Permalink Generator**:

- **Mode** — `SMART` (default): respects manually-created vanities and never overwrites them. `FORCE`: always applies module rules even over manual vanities.
- **Excluded paths** — JCR paths (one per line) for which no vanity will ever be created or updated.
- **Missing Permalink Audit** — scans all `jnt:page` and `jmix:mainResource` nodes under a given path, lists nodes with missing vanities per language, and lets you generate them in bulk. Language pills appear in italic when a node has no `jcr:title` (node name used as slug). Home page nodes are shown but greyed out (backend skips them).
- **Force Regeneration** — scans all nodes from the site root and force-regenerates vanity URLs using the computed pattern, overwriting manual vanities. Scope is nodes with at least one missing vanity. A "bypass excluded paths" checkbox includes otherwise-excluded nodes. Uses the same table UI as the audit panel.

## Technical notes

- `PermalinkGeneratorService` is a Spring bean with `@Autowired VanityUrlManager`, registered as a Drools global via `ModuleGlobalObject`
- `GeneratePermalinksAction` — Jahia action (`POST *.generatePermalinks.do`) used by both the audit and force-regen panels; accepts `nodeIds[]`, `languages[]`, and `force` (boolean) POST parameters
- When `force=true`, the service bypasses the SMART-mode manual-vanity guard and the "already correct" idempotency check, always writing the newly computed URL
- `jmix:permalinkGenerated` mixin defined in `META-INF/definitions.cnd`
- Rules in `META-INF/rules.drl` / `META-INF/rules.dsl`
- Admin panels use two parallel GraphQL queries (`jnt:page` + `jmix:mainResource`) deduped by UUID, augmented with `j:isHomePage` property and `vanityUrls` to drive client-side filtering
