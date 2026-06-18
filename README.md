# permalink-generator

Automatically generates and maintains permanent vanity URLs for Jahia pages and displayable content. Module-generated vanities are tagged with `jmix:permalinkGenerated` to distinguish them from manually-set ones, enabling coexistence of automatic and editorial URL strategies.

- [For editors](#for-editors)
- [For site administrators](#for-site-administrators)
- [For system administrators](#for-system-administrators)
- [For developers](#for-developers)

---

## For editors

### What this module does

Every page automatically gets a permanent URL (a *vanity URL*) derived from its title. A page titled **Our Products** gets `/our-products`. A child page **Cloud Solutions** under it gets `/our-products/cloud-solutions`. Non-default languages are prefixed: `/fr/nos-produits/solutions-cloud`.

### When you rename a page

The module creates a new vanity for the new title. The old vanity is kept as a redirect — existing links and bookmarks continue to work.

### When you move a page

The slug is preserved; only the path prefix changes. All child pages are updated automatically. Old vanities become redirects.

### Manual vanity URLs (SMART mode)

If you manually set a vanity URL in the SEO tab, the module never overwrites it. When the page is renamed or moved, the module updates the URL prefix but keeps your slug.

> Example: you set `/promo-2024`. After moving the page under `/events/`, it becomes `/events/promo-2024` — your slug is preserved.

### FORCE mode

In **FORCE** mode the module replaces all vanities, including manually-set ones. Your manual vanity is demoted to a redirect.

---

## For site administrators

### Admin panel

**Site Settings → Permalink Generator**

### Modes

| Mode | Behaviour |
|---|---|
| **SMART** (default) | Never overwrites manually-set vanities. Updates their prefix on rename/move; preserves the editor's slug. |
| **FORCE** | Always applies the computed URL. Manual vanities are replaced and kept as redirects. |

Use **FORCE** when launching a new site or after a planned SEO migration.

### Excluded paths

JCR paths (one per line) — the module skips these subtrees entirely. To exclude a single page without touching the site list, add the `jmix:permalinkExcluded` mixin to the page in Content Editor.

### Missing Permalink Audit

Generates vanity URLs in bulk for pages that don't have one yet — useful after enabling the module on an existing site.

1. Enter a JCR path to scan (defaults to the site root).
2. Click **Scan** — pages with missing vanities are listed per language.
3. Select nodes (or **Select All**).
4. Click **Generate permalinks**.

Home pages are listed but skipped by the backend.

### Force Regeneration

Replaces stale or manual vanities across the site.

1. Click **Scan All Pages** — a preview shows exactly which URLs would change before writing anything.
2. Review:
   - **Stale** — computed URL differs from what's stored.
   - **Manual** — manually-set vanity present (replaced only in FORCE mode or with force=true).
   - **Missing** — no active default vanity exists.
3. Deselect rows to keep, click **Regenerate selected**, confirm in the modal.

Old vanities are always kept as redirects — no existing links 404.

**Include excluded paths** — bypasses the site's excluded-paths list for this operation.

---

## For system administrators

### Requirements

- Jahia 8.x
- `seo` module (Jahia built-in)

### Installation

Install from the Jahia Store or follow the [module installation guide](https://academy.jahia.com/training-kb/tutorials/administrators/installing-a-module).

Enabling the module on a site does **not** auto-generate vanities for existing pages. Use the Missing Permalink Audit to generate them.

### Permissions

Admin panel requires the `siteAdminPermalinkGenerator` permission. Assign via Roles Manager.

### Logging

Logger: `org.jahiacommunity.modules.permalinkgenerator`

`DEBUG` — per-node processing detail. `INFO` — operation counts.

### Performance

- Events are processed asynchronously by Drools; heavy imports may queue many events.
- The action endpoint processes nodes sequentially. For large trees, use the audit panel with pagination rather than selecting all nodes at once.

---

## For developers

### Module structure

```
src/main/java/.../
  action/GeneratePermalinksAction.java    — POST *.generatePermalinks.do
  services/PermalinkGeneratorService.java — core logic (Spring bean)
  rules/PermalinkModuleGlobalObject.java  — Drools global registration

src/main/resources/
  META-INF/definitions.cnd               — JCR node types and mixins
  META-INF/rules.drl / rules.dsl         — Drools event rules

javascript/src/
  index.jsx                              — React entry point (mounts on #permalink-generator-root)
  PermalinkGeneratorApp.jsx
  components/SiteSettings.jsx            — mode + excluded paths
  components/AuditPanel.jsx              — missing permalink audit
  components/RegenPanel.jsx              — force regeneration
  utils/api.js                           — gql() and postAction()

tests/                                   — @jahia/cypress integration tests
```

### JCR types

| Type | Purpose |
|---|---|
| `jmix:permalinkGenerated` | Mixin on a vanity URL node — marks it as module-managed. Absence = manual. |
| `jmix:permalinkGeneratorSettings` | Site-level settings (`j:permalinkGeneratorMode`, `j:excludedPaths`). |
| `jmix:permalinkExcluded` | Page-level — module skips this node and descendants. |
| `jnt:permalinkGeneratorSiteSettings` | Admin panel content node (rendered by JSP). |

### Drools rules (`rules.drl`)

| Rule | Trigger | Action |
|---|---|---|
| Create or update on title set | `jcr:title` written | Recompute vanity for node + language |
| Recreate on node move | Node moved | Recompute vanities for node + descendants |
| Clean on node deletion | Node deleted | Remove/deactivate vanities |
| Strip on page copy | Top copy created | Remove `jmix:permalinkGenerated` from copy |
| Mark as manual on URL edit | `j:url` written on vanity node | Remove `jmix:permalinkGenerated` mixin |

### URL construction

1. Skip: home page, non-displayable, `jmix:permalinkExcluded`, excluded path.
2. Slugify `jcr:title` (or node name if no title): lowercase, accents stripped, spaces→hyphens.
3. If parent has active default vanity → `{parent_vanity}/{slug}`.
4. Else → rebuild prefix from slugified ancestor titles.
5. If language ≠ site default → prepend `/{lang}`.
6. Resolve conflicts: append `-2`, `-3`, … up to 10 attempts.

### Vanity lifecycle

| Event | Auto-generated vanity | Manual vanity (SMART) |
|---|---|---|
| Title change | Removed; new one created | Prefix updated, slug preserved |
| Page move | Removed; new one created (slug preserved) | Prefix updated, slug preserved |
| Page copy | Stripped from copy | Untouched |
| Page delete | Deactivated (`j:active=false`) | Untouched |

Published vanities are never deleted — kept as active redirects.

### `GeneratePermalinksAction` — action endpoint

```
POST /cms/render/default/en/sites/{siteKey}/settings/site-settings-base/permalinkGeneratorSettings/pagecontent/permalinkGeneratorSiteSettings.generatePermalinks.do
Content-Type: application/x-www-form-urlencoded
X-Requested-With: XMLHttpRequest

nodeIds[]=<uuid>&languages[]=en[&languages[]=fr]
  [&preview=true]         — JSON preview, no writes
  [&bypassExcluded=true]  — ignore excluded-paths config
  [&force=true]           — override SMART-mode and idempotency check
```

Response (non-preview):
```json
{
  "results": [
    { "uuid": "…", "path": "…", "language": "en",
      "action": "created|promoted|already_correct",
      "url": "/new-url", "oldUrl": "/old-url" }
  ]
}
```

### SMART mode internals

`hasManualActiveDefaultVanity(node, lang)` returns true when an active+default vanity exists with **no** `jmix:permalinkGenerated` mixin. When true and `forceRegen == false`, the node is skipped. On cascade (rename/move of parent), manual vanities on children receive a prefix-only update — the slug after the last `/` is preserved.

### Building

```bash
cd javascript
yarn install
yarn build        # → src/main/resources/javascript/permalink-generator-admin.js
mvn clean install
```

### Tests

See [tests/README.md](tests/README.md).
