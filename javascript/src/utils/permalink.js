// Shared logic & constants for AuditPanel and RegenPanel.
// Kept framework-agnostic (no React) so it is trivially unit-testable.

export const BATCH = 100;
export const CHUNK = 20;

export const GQL_QUERY =
    'query($q:String!,$lim:Int!,$off:Int!){jcr{nodesByQuery(query:$q,queryLanguage:SQL2,limit:$lim,offset:$off){nodes{uuid path displayName isHomePage:property(name:"j:isHomePage"){value} vanityUrls{url language active default}}}}}';

/**
 * @param {Array<{language:string,active:boolean,default:boolean}>} vanityUrls
 * @param {string} lang
 * @returns {boolean} true when an active, default vanity URL exists for lang.
 */
export function hasActiveDefault(vanityUrls, lang) {
    return (vanityUrls || []).some(v => v.language === lang && v.active && v['default']);
}

/**
 * @param {string} nodePath
 * @param {string[]} excludedPaths
 * @returns {boolean} true when nodePath is under any configured excluded path.
 */
export function isExcludedBySettings(nodePath, excludedPaths) {
    return (excludedPaths || []).some(ep => ep && nodePath.startsWith(ep));
}

/**
 * Immutably add (uuid, lang) to a selections map. Never mutates the input.
 */
export function selectCell(sels, uuid, lang) {
    const next = { ...sels };
    next[uuid] = next[uuid] ? new Set(next[uuid]) : new Set();
    next[uuid].add(lang);
    return next;
}

/**
 * Immutably remove (uuid, lang) from a selections map. Never mutates the input.
 */
export function deselectCell(sels, uuid, lang) {
    if (!sels[uuid]) return sels;
    const next = { ...sels };
    next[uuid] = new Set(next[uuid]);
    next[uuid].delete(lang);
    if (next[uuid].size === 0) delete next[uuid];
    return next;
}

/**
 * Count all selected (uuid, lang) pairs across the selections map.
 */
export function totalSelected(sels) {
    return Object.values(sels).reduce((n, s) => n + s.size, 0);
}
