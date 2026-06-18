# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] — 2026-06-18

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

**Excluded paths**

Enter JCR paths (one per line) to suppress automatic vanity generation for entire content subtrees. Individual pages can also be excluded directly in Content Editor via the `jmix:permalinkExcluded` mixin.

**Missing Permalink Audit**

Find all pages on the site that have no vanity URL, broken down by language. Select the ones you want to fix and generate their vanities in a single click. Useful when first enabling the module on an existing site.

**Force Regeneration**

Scan the entire site and preview exactly which vanity URLs would change — stale (slug differs from title), manual (set by an editor), or missing — before writing anything. Each row shows the current URL and what it would become. Deselect rows you want to keep, confirm, and the module applies only the selected changes. Old vanities are always kept as redirects.

---

### Extended background behaviour

The following rules are new in 2.0.0. They fire automatically with no configuration required.

- **Rename** — when a page title changes, the vanity URL is updated. The old URL is kept as a redirect.
- **Move** — when a page moves in the tree, its vanity URL (and all descendant vanities) are updated to reflect the new path. Old URLs are kept as redirects.
- **Delete** — when a page is deleted, its vanity URLs are deactivated so existing links continue to redirect rather than 404.
- **Copy** — copied pages start with no auto-generated vanity; fresh ones are computed independently from the copy's own title.
- **Manual vanity detection** — when an editor writes a vanity URL directly in the SEO tab, the module marks it as manual and will not overwrite it in SMART mode.
- **Vanity undelete** — if the module computes a URL that matches an existing deactivated vanity, it reactivates that node instead of creating a duplicate.

---

### Removed

- **Spring framework dependency** — the service is now a pure OSGi Declarative Services component. The `META-INF/spring/` wiring present in 1.0.x has been removed. Third-party bundles that imported the Spring bean must rebind to the OSGi service.

---

### Testing

- Cypress E2E test suite added — 6 scenarios, 30 tests covering setup/teardown, SMART mode, title rename, audit panel, force-regen (SMART vs FORCE), and manual vanity preservation.

---

## [1.0.4] — 2022-11-18

### Fixed

- Vanity URL generation is now skipped for file nodes (`jnt:file` and subtypes). Previously the module attempted to generate vanities for binary content, which produced incorrect URLs.
