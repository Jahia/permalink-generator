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

## Technical notes

- `PermalinkGeneratorService` is a Spring bean with `@Autowired VanityUrlManager`, registered as a Drools global via `ModuleGlobalObject`
- `jmix:permalinkGenerated` mixin defined in `META-INF/definitions.cnd`
- Rules in `META-INF/rules.drl` / `META-INF/rules.dsl`
