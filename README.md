# permalink-generator
> Automatically generates and maintains permanent vanity URLs for displayable content in Jahia.

## Installation

Install from the Jahia store or follow the [module installation guide](https://academy.jahia.com/training-kb/tutorials/administrators/installing-a-module).

## How it works

The module listens to JCR events and manages vanity URLs automatically. All module-generated vanities are tagged with the `jmix:permalinkGenerated` mixin, distinguishing them from manually-created vanities.

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

| Event | Unpublished module-managed vanity | Published module-managed vanity | Manual vanity (SMART mode) |
|---|---|---|---|
| **Title change** | Deleted, new one created | Kept as active redirect (`j:default=false`), new one created | Prefix updated, slug preserved |
| **Page move** | Deleted, new one created (slug preserved, prefix updated) | Kept as active redirect, new one created | Prefix updated, slug preserved |
| **Page copy** | Stripped from the copy | Stripped from the copy | Untouched |
| **Page delete** | Deleted | Deactivated (`j:active=false`) | Untouched |

Published vanities are never deleted — they stay active as redirects so old URLs never 404.

Manual vanities (no `jmix:permalinkGenerated` mixin) are detected by checking for an active+default vanity that the module did not create. In SMART mode, when a parent page is renamed or moved, the module updates the URL prefix of child manual vanities while preserving the slug the editor chose. In FORCE mode, manual vanities are fully replaced.

### Propagation on rename

When a page title changes, all descendant nav-menu pages have their vanities recomputed automatically. Processing is depth-first: each child sees its parent's updated vanity before computing its own. In SMART mode, manual vanities in the descendant chain receive a prefix-only update (slug preserved).

### Propagation on move

On move, the slug of each page is preserved — only the prefix changes. Descendants are updated recursively in the same depth-first order.

### Restoring pending-deletion vanities

If a vanity node is marked for deletion (`jmix:markedForDeletion`) and the module later computes the same URL for that node, it removes the deletion markers instead of creating a duplicate — restoring the existing vanity rather than fighting it.

## Dependencies

- Jahia 8.x
- [slugify](https://github.com/slugify/slugify) 2.4
- Jahia `seo` module

## Site settings & admin panel

An administration panel is available under **Site Settings → Permalink Generator**:

### Mode

- **SMART** (default) — the module creates and updates module-managed vanities but never fully overwrites a manually-set vanity. On title/move events it updates the URL prefix of manual vanities while keeping the editor's chosen slug.
- **FORCE** — the module always applies its computed URL, replacing manual vanities (which are demoted to redirect-only). Use on new sites or after a planned SEO migration.

### Excluded paths

JCR paths (one per line) for which no vanity will ever be created or updated. An "Include excluded paths" checkbox in the Force Regeneration panel can bypass this per-operation.

### Missing Permalink Audit

Scans `jnt:page` and `jmix:mainResource` nodes under a given path and lists nodes with missing vanities per language. Language pills appear in italic when a node has no `jcr:title` (node name used as slug). Lets you select and generate missing vanities in bulk. Home page nodes are shown but greyed out (backend skips them).

### Force Regeneration

Scans all nodes from the site root and lists pages with **stale** (URL would change), **manual** (manually-set vanity present), or **missing** vanities. Auto-selected rows have a URL that would change — deselect any you want to keep.

A preview POST is made before generating: the panel shows exactly which URLs will change per language before you confirm. Replaced vanities are kept as redirect entries so existing links continue to resolve.

## Technical notes

- `PermalinkGeneratorService` — Spring bean (`@Autowired VanityUrlManager`), registered as a Drools global via `ModuleGlobalObject`
- `GeneratePermalinksAction` — Jahia action (`POST *.generatePermalinks.do`) used by both panels; POST parameters:
  - `nodeIds[]` — UUIDs to process
  - `languages[]` — language codes to process
  - `preview=true` — returns a JSON preview of what would change without writing (used by Force Regen panel before confirm)
  - `bypassExcluded=true` — ignore excluded-path config for this operation
  - `force=true` — bypass SMART-mode manual-vanity guard and "already correct" idempotency check
- `jmix:permalinkGenerated` mixin defined in `META-INF/definitions.cnd`; its absence on an active+default vanity = manually set
- Rules in `META-INF/rules.drl` / `META-INF/rules.dsl`
- Admin panels use two parallel GraphQL queries (`jnt:page` + `jmix:mainResource`) deduped by UUID, augmented with `j:isHomePage` and `vanityUrls`
