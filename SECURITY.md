# Security Policy

## Authorization

The `generatePermalinks` action endpoint (used for bulk vanity URL generation) requires:
- Authentication (valid Jahia user session)
- `siteAdminPermalinkGenerator` permission on the target site

These checks prevent unauthorized bulk URL generation.

## Reporting a Vulnerability

Security information can be found in our [security.txt file](https://academy.jahia.com/.well-known/security.txt).
