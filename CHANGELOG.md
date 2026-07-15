# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.2] — 2026-07-14

### Security

- **Cross-site authorization escalation fixed (CRITICAL)** — `GeneratePermalinksAction` now re-checks, on the caller's own session, that the caller holds the `siteAdminPermalinkGenerator` permission at the site of **every** submitted node before any mutation. Previously the permission was evaluated only against the URL-resolved node while the service mutated each node through an ACL-bypassing system session, letting a site-scoped admin overwrite/demote another site's editorial vanities (e.g. with `force=true`). The request is now denied with HTTP 403 (fail-closed) if any node is outside the caller's authority.

### Fixed

- **Excluded-path over-match** — excluded paths now match on a path-segment boundary instead of a raw string prefix, so configuring `/home/blog` no longer wrongly excludes siblings such as `/home/blog-archive` or `/home/blogging`.
- **Ancestor home-page detection** — the parent-title URL fallback now reads the `j:isHomePage` boolean value instead of only testing for the property's presence, so a normal page carrying an autocreated `j:isHomePage=false` no longer loses its title segment.

### Documentation

- Clarified the vanity lifecycle: rename/move keep old vanities as active redirects, whereas **delete** fully deactivates the vanity (`j:active=false`, `j:default=false`) and the old URL returns 404 — a redirect to a removed page would be a dangling mapping.

## [2.0.1] — 2026-06-23

### Security

- **Action endpoint authentication** — `GeneratePermalinksAction` now explicitly requires an authenticated user and the `siteAdminPermalinkGenerator` permission. Previously these checks were not enforced at the action level.
- **Exception details no longer leaked** — internal error messages are no longer returned to the client; a generic message is returned and the detail is logged server-side.

### Fixed

- **Drools re-entrancy guard** — `removePermalinkMixin()` now skips writes when called from a system session, preventing an infinite loop when Drools processes module-initiated saves.
- **`isPublishedInLive()` error handling** — unexpected `RepositoryException` (other than `ItemNotFoundException`) now preserves the vanity (returns `true`) instead of incorrectly treating it as unpublished.
- **CSS — help button size** — `.pl-help-btn` was rendered at 44×44 px (touch-target overshoot for a mouse-driven admin panel); restored to 20×20 px.
- **CSS — legend pills** — decorative `aria-hidden` pills in the legend were inheriting the 44 px `min-width` from `.pl-pill`; overridden to compact size.

### Changed

- `saveVanityWithMixin()` split into `addMixinToSavedVanity()` — single save per operation closes a create/save re-entrancy window.
- Many magic strings extracted to named constants in `PermalinkGeneratorService`.

---

## [2.0.0] — 2026-06-18

The goal of this module has always been that editors should never have to think about vanity URLs — they should just exist and stay correct as the site evolves. Version 1.0.x achieved this for the creation case only. The gap was everything that happens *after* a page is created: renames, moves, restructures. Every one of those operations left a stale URL behind. Version 2.0.0 closes that gap entirely, and adds a full admin panel so site administrators can audit and control vanity URL state across the site at any time.

### Upgrading from 1.0.x

Version 1.0.x ran entirely in the background: it generated a vanity URL when a page title was set, and nothing else. There was no admin panel and no configuration.

Version 2.0.0 is a complete rewrite. The background behaviour is preserved and significantly extended; a full admin panel is added; and the Spring service layer is replaced with OSGi Declarative Services.

**Existing auto-generated vanities are unaffected.** The module upgrades cleanly.

---

### New admin panel — Site Settings → Permalink Generator

The module now has a dedicated administration panel, accessible from **Site Settings → Permalink Generator**. It is the single place to configure the module and operate on vanity URLs in bulk.

**Mode selection (SMART or FORCE)**

Choose how the module handles pages that already have a vanity URL:

| Mode | Behaviour |
|------|-----------|
| **SMART** *(default)* | Never overwrites a vanity URL that an editor set manually. On rename or move, only the path prefix is updated — the editor's slug is preserved. |
| **FORCE** | Always applies the computed URL. Manual vanities are replaced and kept as redirects. |

**Excluded paths** — Enter JCR paths (one per line) to suppress automatic generation for entire content subtrees. Individual pages can also be excluded in Content Editor via the `jmix:permalinkExcluded` mixin.

**Missing Permalink Audit** — Find all pages with no vanity URL, by language. Select and generate in one click. Useful when first enabling the module on an existing site.

**Force Regeneration** — Preview exactly which vanity URLs would change (stale, manual, or missing) before writing anything. Each row shows current URL and what it would become. Deselect rows to keep, confirm, done. Old vanities are always kept as redirects.

---

### Extended background behaviour

The following rules are new in 2.0.0. They fire automatically with no configuration required.

- **Rename** — when a page title changes, the vanity URL is updated. The old URL is kept as a redirect.
- **Move** — when a page moves in the tree, its vanity URL (and all descendant vanities) are updated to reflect the new path. Old URLs are kept as redirects.
- **Delete** — when a page is deleted, its vanity URLs are deactivated so existing links continue to redirect rather than 404.
- **Copy** — copied pages start with no auto-generated vanity; fresh ones are computed independently from the copy's own title.
- **Manual vanity detection** — when an editor writes a vanity URL directly in the SEO tab, the module marks it as manual and will not overwrite it in SMART mode.
- **Vanity undelete** — if the module computes a URL that matches an existing deactivated vanity, it reactivates that node instead of creating a duplicate.

### Removed

- **Spring framework dependency** — the service is now a pure OSGi Declarative Services component. The `META-INF/spring/` wiring present in 1.0.x has been removed. Third-party bundles that imported the Spring bean must rebind to the OSGi service.

### Testing

- Cypress E2E test suite added — 8 scenarios, 29 tests covering setup/teardown, auto-generation, title rename, branch move, audit panel, force-regen (SMART vs FORCE), manual vanity preservation, and cleanup.

---

## [1.0.4] — 2022-11-18

### Fixed

- Vanity URL generation is now skipped for file nodes (`jnt:file` and subtypes). Previously the module attempted to generate vanities for binary content, which produced incorrect URLs.

---

## [1.0.3] — 2022-01-12

### Fixed

- Vanity generation is now skipped during JCR import operations to avoid conflicts with imported content.
- Incorrect vanity URL for level-1 pages (pages directly under the site home) — path computation was off by one level.
- Vanity URLs were incorrectly propagated to all languages on copy; generation now correctly targets the page's own languages only.
- Unregistered namespace prefix error when processing nodes in certain workspaces.
- `jnt:content` removed from the bypass type list ([#1](https://github.com/Jahia/permalink-generator/issues/1)) — content nodes now correctly receive vanity URLs.

---

## [1.0.2] — 2021-06-09

### Fixed

- Stability fixes in the service layer to prevent errors on certain site configurations.

---

## [1.0.1] — 2021-05-27

### Fixed

- Drools rule renamed to avoid conflicts with rules defined by other modules.

---

## [1.0.0] — 2021-04-23

### Added

- Initial release.
- Automatic vanity URL generation from `jcr:title` via a Drools rule. Fires on every title change, excluding import operations.
- SEO-friendly slug computed with the [Slugify](https://github.com/slugify/slugify) library (lowercase, accents stripped, spaces to hyphens).
- Generation is skipped when the module is not enabled on the site.
