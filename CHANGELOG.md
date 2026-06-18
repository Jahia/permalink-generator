# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] — 2026-06-18

### Breaking changes

- **Admin UI completely rewritten in React.** The Spring Webflow admin page (`*.do` form) has been removed and replaced with a React bundle served from the same JSP entry point. Any customisation of the old Webflow view must be redone against the new JSX components.
- **OSGi Declarative Services.** Service wiring migrated from Spring `@Autowired` to OSGi DS `@Component` / `@Reference`. Third-party bundles that `@Reference` the old Spring bean must update their bindings.

### Added

- **Force Regeneration panel** — bulk preview and apply vanity URL regeneration across the entire site. Displays stale, manual, and missing vanities before writing anything. Includes confirm modal and per-page result report with previous/new URL columns.
- **Smart scan filter** — Force Regen scan only lists nodes where the computed URL actually differs from what is stored (stale, manual, or missing), keeping large-site results actionable.
- **Cascading prefix update for manual vanities** — when a parent page is renamed or moved, manual vanities on child pages receive a prefix-only update; the editor's slug after the last `/` is preserved.
- **Vanity undelete** — when the module recomputes the same URL as an existing inactive auto-generated vanity, it re-promotes that vanity instead of creating a duplicate.
- **Homepage guard** — home pages are listed in the audit/regen panels for visibility but are always skipped during generation.
- **No-title indicator** — pages without a `jcr:title` are flagged; the node name is used as the slug.
- **Pill color legend** — collapsible help overlay in both Audit and Regen panels explaining each pill state.
- **Accessibility improvements** — `aria-label` on every checkbox row; `aria-live` status on save result; keyboard-navigable panels.
- **Excluded paths — bypass option** — Force Regen panel exposes a *Include excluded paths* toggle to override the site's excluded-paths list for a single operation.
- **Previous vanity in report** — the regeneration result now includes the old URL alongside the new one.
- **INFO logging** — operation counts (generated, promoted, skipped) logged at INFO level for audit trails.
- **Full i18n** — zero hardcoded strings. All labels, placeholders, ARIA strings, and error messages are in resource bundles. Translations shipped: EN, FR, DE, ES, IT, PT.
- **`jmix:permalinkExcluded` mixin** — editors can exclude a single page from automatic vanity generation directly in Content Editor without touching site settings.
- **Cypress E2E test suite** — 6 scenarios, 30 tests covering: setup/teardown, SMART mode, title rename, audit panel, force-regen (SMART vs FORCE), and manual vanity preservation.
- **pom.xml community metadata** — `<licenses>` (MIT), `<developers>`, and `<organization>` sections.
- **Javadoc** — all public methods on `PermalinkGeneratorService`, `GeneratePermalinksAction`, and `PermalinkModuleGlobalObject`.

### Fixed

- CSRF guard: all POST requests now carry the correct CSRF token via `X-Jahia-Token` / `CsrfGuard` API; XHR used for action POSTs so the CSRFGuard patch auto-injects the token.
- `VanityUrlManager` obtained from Spring application context instead of a broken OSGi `@Reference`, resolving `NullPointerException` on first vanity write.
- `NonUniqueUrlMappingException` guard when two nodes compute the same slug after a bulk generation.
- All `.properties` resource bundle files are now pure ASCII with `\uXXXX` escapes (Java spec requirement); eliminates garbled characters on non-UTF-8 JVM defaults.
- Resource bundle keys for JCR properties use `j_` separator (`j_permalinkGeneratorMode`) instead of `j:`, which was silently parsed as a key/value separator by `java.util.Properties`.

### Performance

- Force Regen preview and generate operations are depth-sorted and process parent nodes before children, avoiding redundant re-computations on large site trees.

---

## [1.0.4] — 2022-11-18

### Fixed

- Vanity URL generation is now skipped for file nodes (`jnt:file` and subtypes). Previously the module attempted to generate vanities for binary content, which produced incorrect URLs.

