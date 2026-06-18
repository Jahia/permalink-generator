# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] — 2026-06-18

> **Complete rewrite.** Version 1.0.x was a background-only module with a single Drools rule and no admin interface. Every feature listed below is new.

### Added

**Core behaviour**

- **SMART mode** — module never overwrites a manually-set vanity URL. When a page is renamed or moved, only the path prefix is updated; the editor's slug is preserved.
- **FORCE mode** — module always applies the computed URL, replacing manual vanities (which are kept as redirects).
- **`jmix:permalinkGenerated` mixin** — auto-generated vanity nodes are tagged to distinguish them from manually-created ones. Absence of the mixin means "manual".
- **Rename and move rules** — vanities are recomputed when `jcr:title` changes or a page is moved; all descendant vanities are updated in cascade.
- **Delete rule** — vanities are deactivated (not deleted) when their page is deleted; existing links keep working.
- **Copy rule** — `jmix:permalinkGenerated` is stripped from copied pages so fresh vanities are generated independently.
- **Manual-edit detection** — when an editor writes `j:url` directly on a vanity node, `jmix:permalinkGenerated` is removed so the vanity is treated as manual from that point on.
- **Vanity undelete** — when the module recomputes the same URL as an existing inactive auto-generated vanity, it re-promotes that vanity instead of creating a duplicate.
- **`jmix:permalinkExcluded` mixin** — editors can exclude a single page from automatic vanity generation directly in Content Editor.
- **Excluded paths** — site-level JCR path list; the module skips all nodes under these subtrees.

**Admin panel** *(did not exist in 1.0.x — accessible via Site Settings → Permalink Generator)*

The module now ships a full administration UI reachable from **Site Settings → Permalink Generator**. From this single panel, site administrators can:

- **Configure the generation mode** (SMART or FORCE) — stored per site, applies to all automatic and bulk operations.
- **Define excluded paths** — JCR paths (one per line) whose subtrees are entirely skipped.
- **Run the Missing Permalink Audit** — lists all pages that have no vanity URL per language; supports bulk selection and one-click generation.
- **Run Force Regeneration** — scans the entire site and shows a live preview of exactly which URLs would change (stale, manual, or missing) before writing anything. Each row shows the previous and new URL. Confirm modal before any write; smart filter excludes nodes where nothing would change.

Additional UI details:
- **Pill color legend** — collapsible help overlay in both panels explaining each pill state.
- **Translations** — full UI in EN, FR, DE, ES, IT, PT.

### Removed

- **Spring framework dependency** — the service layer is now pure OSGi Declarative Services (`@Component` / `@Reference`). The `META-INF/spring/` wiring that was present in 1.0.x has been removed.

**Testing**

- **Cypress E2E test suite** — 6 scenarios, 30 tests: setup/teardown, SMART mode, title rename, audit panel, force-regen (SMART vs FORCE), manual vanity preservation.

---

## [1.0.4] — 2022-11-18

### Fixed

- Vanity URL generation is now skipped for file nodes (`jnt:file` and subtypes). Previously the module attempted to generate vanities for binary content, which produced incorrect URLs.
