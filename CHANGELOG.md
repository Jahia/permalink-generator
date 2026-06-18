# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] — 2026-06-18

### Added

- **Force Regeneration panel** — bulk preview and apply vanity URL regeneration across the entire site. Displays stale, manual, and missing vanities before writing anything. Confirm modal and per-page result report with previous/new URL columns. Smart scan filter: only nodes where the computed URL actually differs are listed.
- **Cascading prefix update for manual vanities** — when a parent page is renamed or moved, manual vanities on child pages receive a prefix-only update; the editor's slug after the last `/` is preserved.
- **Vanity undelete** — when the module recomputes the same URL as an existing inactive auto-generated vanity, it re-promotes that vanity instead of creating a duplicate.
- **`jmix:permalinkExcluded` mixin** — editors can exclude a single page from automatic vanity generation directly in Content Editor without touching site settings.
- **Excluded paths — bypass option** — Force Regen panel exposes an *Include excluded paths* toggle to override the site's excluded-paths list for a single operation.
- **Pill color legend** — collapsible help overlay in both Audit and Regen panels explaining each pill state.
- **Translations** — DE, ES, IT, PT added (EN and FR were already present).
- **Cypress E2E test suite** — 6 scenarios, 30 tests: setup/teardown, SMART mode, title rename, audit panel, force-regen (SMART vs FORCE), manual vanity preservation.

---

## [1.0.4] — 2022-11-18

### Fixed

- Vanity URL generation is now skipped for file nodes (`jnt:file` and subtypes). Previously the module attempted to generate vanities for binary content, which produced incorrect URLs.

