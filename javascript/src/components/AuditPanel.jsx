import React, { useState, useRef, useCallback, useMemo, useEffect } from 'react';
import { gql, postAction } from '../utils/api';
import {
    BATCH, CHUNK, GQL_QUERY,
    hasActiveDefault, isExcludedBySettings,
    selectCell, deselectCell, totalSelected
} from '../utils/permalink';
import { LegendItem, ProgressBar, PillCell, LoadMore } from './shared';

export default function AuditPanel({ contextPath, sitePath, langs, excludedPaths, actionUrl, i18n }) {
    const [showLegend, setShowLegend] = useState(false);
    const [scanPath, setScanPath] = useState(sitePath);
    const [scanning, setScanning] = useState(false);
    const [scanStatus, setScanStatus] = useState({ msg: '', color: '#4d4d4d' });
    const [showResults, setShowResults] = useState(false);
    const [rows, setRows] = useState([]);
    const [selections, setSelections] = useState({});
    const [genStatus, setGenStatus] = useState({ msg: '', color: '#4d4d4d' });
    const [generating, setGenerating] = useState(false);
    const [progress, setProgress] = useState(0);
    const [showProgress, setShowProgress] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [loadMoreStatus, setLoadMoreStatus] = useState('');

    // Independent per-query offsets so pagination cannot drift when one query
    // exhausts before the other (each advances only when it returned a full BATCH).
    const offsetPageRef = useRef(0);
    const offsetMixinRef = useRef(0);
    const totalScannedRef = useRef(0);
    const rowsRef = useRef([]);
    // In-flight guard (replaces relying on the `scanning` state in deps, which
    // produced a stale closure / re-created callback on every toggle).
    const inFlightRef = useRef(false);
    // Ref to the results heading for focus management after scan (SC 2.4.3).
    const resultsHeadingRef = useRef(null);

    const doScan = useCallback(async (reset) => {
        if (inFlightRef.current) return;
        const path = scanPath.trim();
        if (!path.startsWith('/sites/')) {
            setScanStatus({ msg: 'Error: ' + i18n.pathRequired, color: '#922b21' });
            return;
        }

        if (reset) {
            offsetPageRef.current = 0;
            offsetMixinRef.current = 0;
            totalScannedRef.current = 0;
            rowsRef.current = [];
            setRows([]);
            setSelections({});
            setShowResults(false);
            setHasMore(false);
            setLoadMoreStatus('');
        }

        inFlightRef.current = true;
        setScanning(true);
        setScanStatus({ msg: i18n.scanRunning, color: '#4d4d4d' });

        const escapedPath = path.replace(/'/g, "''");
        const qPage  = `SELECT * FROM [jnt:page] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const qMixin = `SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const offPage = offsetPageRef.current;
        const offMixin = offsetMixinRef.current;

        try {
            const [r1, r2] = await Promise.all([
                gql(contextPath, { query: GQL_QUERY, variables: { q: qPage, lim: BATCH, off: offPage } }),
                gql(contextPath, { query: GQL_QUERY, variables: { q: qMixin, lim: BATCH, off: offMixin } })
            ]);

            const seen = {};
            const nodes = [];
            const errors = [];
            const nodesOf = data => {
                try { return data.data.jcr.nodesByQuery.nodes || []; } catch (e) { console.warn(e); return []; }
            };
            for (const data of [r1, r2]) {
                if (data.errors) { errors.push(...data.errors.map(e => e.message)); continue; }
                nodesOf(data).forEach(n => { if (!seen[n.uuid]) { seen[n.uuid] = true; nodes.push(n); } });
            }

            if (errors.length && nodes.length === 0) {
                console.error('GraphQL scan errors:', errors);
                setScanStatus({ msg: 'Error: ' + i18n.errorGraphql.replace('{0}', errors.length), color: '#922b21' });
                return;
            }

            // Advance each offset independently; only paginate a query that
            // returned a full BATCH (otherwise it is exhausted).
            const pageFull = nodesOf(r1).length === BATCH;
            const mixinFull = nodesOf(r2).length === BATCH;
            if (pageFull) offsetPageRef.current += BATCH;
            if (mixinFull) offsetMixinRef.current += BATCH;
            const more = pageFull || mixinFull;

            totalScannedRef.current += nodes.length;

            const newRows = [];
            for (const n of nodes) {
                if (isExcludedBySettings(n.path, excludedPaths)) continue;
                const missing = new Set(langs.filter(l => !hasActiveDefault(n.vanityUrls, l)));
                if (missing.size === 0) continue;
                const nodeName = n.path.split('/').pop();
                newRows.push({
                    uuid: n.uuid,
                    path: n.path,
                    displayName: n.displayName || nodeName,
                    hasNoTitle: (n.displayName === nodeName),
                    isHomePage: !!(n.isHomePage && n.isHomePage.value === 'true'),
                    missing,
                    generated: new Set()
                });
            }

            rowsRef.current = [...rowsRef.current, ...newRows];
            setRows([...rowsRef.current]);
            setHasMore(more);

            const total = totalScannedRef.current;
            const missing = rowsRef.current.length;
            const scannedSoFar = Math.max(offsetPageRef.current, offsetMixinRef.current);

            if (more) {
                setLoadMoreStatus(i18n.loadMoreSt.replace('{0}', scannedSoFar).replace('{1}', missing));
            } else {
                setLoadMoreStatus('');
            }

            if (missing === 0 && !more) {
                setScanStatus({ msg: i18n.allGood.replace('{0}', total), color: '#0a4d25' });
            } else {
                setScanStatus({ msg: i18n.scanned.replace('{0}', total).replace('{1}', missing), color: missing > 0 ? '#333' : '#0a4d25' });
                setShowResults(true);
            }
        } catch(e) {
            setScanStatus({ msg: i18n.errorNetwork.replace('{0}', e.message || '?'), color: '#922b21' });
        } finally {
            inFlightRef.current = false;
            setScanning(false);
        }
    }, [scanPath, contextPath, langs, excludedPaths, i18n]);

    function toggleCell(uuid, lang) {
        setSelections(prev => {
            if (prev[uuid] && prev[uuid].has(lang)) return deselectCell(prev, uuid, lang);
            return selectCell(prev, uuid, lang);
        });
    }

    function toggleRow(uuid, checked) {
        const row = rowsRef.current.find(r => r.uuid === uuid);
        if (!row) return;
        setSelections(prev => {
            let next = { ...prev };
            if (checked) {
                row.missing.forEach(l => { if (!row.generated.has(l)) next = selectCell(next, uuid, l); });
            } else {
                langs.forEach(l => { next = deselectCell(next, uuid, l); });
            }
            return next;
        });
    }

    function toggleSelectAll(checked) {
        setSelections(prev => {
            let next = { ...prev };
            if (checked) {
                rowsRef.current.forEach(row => {
                    if (row.isHomePage) return;
                    row.missing.forEach(l => { if (!row.generated.has(l)) next = selectCell(next, row.uuid, l); });
                });
            } else {
                next = {};
            }
            return next;
        });
    }

    function toggleColLang(lang, checked) {
        setSelections(prev => {
            let next = { ...prev };
            rowsRef.current.forEach(row => {
                if (row.isHomePage || !row.missing.has(lang) || row.generated.has(lang)) return;
                if (checked) next = selectCell(next, row.uuid, lang);
                else next = deselectCell(next, row.uuid, lang);
            });
            return next;
        });
    }

    async function doGenerate() {
        const byLang = {};
        Object.entries(selections).forEach(([uuid, set]) => {
            set.forEach(lang => {
                if (!byLang[lang]) byLang[lang] = [];
                byLang[lang].push(uuid);
            });
        });
        const langKeys = Object.keys(byLang);
        if (!langKeys.length) return;

        const total = totalSelected(selections);
        let done = 0;
        let errorCount = 0;
        setGenerating(true);
        setGenStatus({ msg: '', color: '#4d4d4d' });
        setShowProgress(true);
        setProgress(0);

        try {
            for (const lang of langKeys) {
                const uuids = byLang[lang];
                for (let c = 0; c < uuids.length; c += CHUNK) {
                    const chunk = uuids.slice(c, c + CHUNK);

                    const params = new URLSearchParams();
                    chunk.forEach(uid => params.append('nodeIds[]', uid));
                    params.append('languages[]', lang);

                    try {
                        const data = await postAction(actionUrl, params);
                        // Consume the real {results:[...]} response shape; count
                        // server-confirmed results when present, else the chunk size.
                        const results = (data && Array.isArray(data.results)) ? data.results : null;
                        const confirmed = results ? results.length : chunk.length;
                        // Build fresh row objects with a new Set rather than
                        // mutating the ref'd rows in place.
                        rowsRef.current = rowsRef.current.map(row => {
                            if (!chunk.includes(row.uuid)) return row;
                            const generated = new Set(row.generated);
                            generated.add(lang);
                            return { ...row, generated };
                        });
                        setSelections(prev => {
                            let next = { ...prev };
                            chunk.forEach(uuid => { next = deselectCell(next, uuid, lang); });
                            return next;
                        });
                        done += confirmed;
                        setProgress(Math.min(1, total ? done / total : 1));
                        setRows([...rowsRef.current]);
                    } catch(e) {
                        errorCount += chunk.length;
                        setGenStatus({ msg: i18n.genError.replace('{0}', e.status || '?').replace('{1}', lang), color: '#922b21' });
                    }
                }
            }
        } finally {
            // Set genStatus before hiding progress bar to avoid a render cycle
            // where neither the bar nor the message is visible (issue 12).
            if (errorCount > 0 && done > 0) {
                setGenStatus({ msg: i18n.genPartial.replace('{0}', done).replace('{1}', errorCount), color: '#8a4500' });
            } else if (errorCount > 0) {
                setGenStatus({ msg: i18n.genAllFailed, color: '#922b21' });
            } else if (done > 0) {
                setGenStatus({ msg: '✓ ' + i18n.genSuccess.replace('{0}', done), color: '#0a4d25' });
            } else {
                setGenStatus({ msg: i18n.genZero, color: '#8a4500' });
            }
            setShowProgress(false);
            setGenerating(false);
        }
    }

    // Move focus to the results heading when results first appear (SC 2.4.3).
    useEffect(() => {
        if (showResults && resultsHeadingRef.current) {
            resultsHeadingRef.current.focus();
        }
    }, [showResults]);

    const selCount = totalSelected(selections);

    const allMissing = rows.reduce((acc, row) => {
        if (row.isHomePage) return acc;
        row.missing.forEach(l => { if (!row.generated.has(l)) acc.push({ uuid: row.uuid, l }); });
        return acc;
    }, []);
    const allSelected = allMissing.length > 0 && allMissing.every(item => selections[item.uuid] && selections[item.uuid].has(item.l));

    // Per-column "all selected" state, hoisted out of render's inline IIFE.
    const colAllSelected = useMemo(() => {
        const map = {};
        langs.forEach(lang => {
            const colMissing = rows.filter(r => r.missing.has(lang) && !r.generated.has(lang));
            map[lang] = colMissing.length > 0 && colMissing.every(r => selections[r.uuid] && selections[r.uuid].has(lang));
        });
        return map;
    }, [langs, rows, selections]);

    return (
        <div className="pl-audit" role="region" aria-labelledby="plAuditHeading">
            <h3 id="plAuditHeading" style={{ display: 'flex', alignItems: 'center' }}>
                {i18n.auditTitle}
                <button
                    className="pl-help-btn"
                    aria-expanded={showLegend}
                    aria-controls="plAuditLegend"
                    aria-label={i18n.legendHelp}
                    onClick={() => setShowLegend(v => !v)}
                >?</button>
            </h3>
            <p className="text-muted">{i18n.auditDesc}</p>

            <div id="plAuditLegend" className={'pl-legend-wrap' + (showLegend ? ' open' : '')} aria-hidden={!showLegend}>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center', padding: '0 0 16px', fontSize: '0.75rem' }}>
                    <LegendItem cls="pl-pill-miss" label={i18n.pillMissing} />
                    <LegendItem cls="pl-pill-sel"  label={i18n.pillSelected} />
                    <LegendItem cls="pl-pill-has"  label={i18n.pillExisting} />
                    <LegendItem cls="pl-pill-gen"  label={i18n.pillGenerated} />
                </div>
            </div>

            <div className="control-group">
                <label className="control-label" htmlFor="plAuditPath" style={{ fontWeight: 'bold' }}>
                    {i18n.auditStartPath}
                </label>
                <div className="controls" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <input
                        type="text"
                        id="plAuditPath"
                        className="input-xlarge"
                        value={scanPath}
                        onChange={e => {
                            setScanPath(e.target.value);
                            // Clear error state when the user edits the path (issue 8).
                            if (scanStatus.color === '#922b21') setScanStatus({ msg: '', color: '#4d4d4d' });
                        }}
                        style={{ fontFamily: 'monospace', fontSize: '0.75rem' }}
                    />
                    <button
                        className="btn"
                        onClick={() => doScan(true)}
                        disabled={scanning}
                        aria-busy={scanning}
                    >
                        <i className="icon-search" aria-hidden="true"></i> {i18n.auditScan}
                    </button>
                    <span role="status" aria-live="polite" style={{ fontSize: '0.75rem', color: scanStatus.color }}>{scanStatus.msg}</span>
                </div>
            </div>

            {showResults && (
                <div style={{ marginTop: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
                        {/* Visually hidden heading receives focus after scan for SC 2.4.3 */}
                        <span
                            ref={resultsHeadingRef}
                            tabIndex={-1}
                            role="status"
                            aria-live="polite"
                            style={{ fontSize: '0.8125rem', fontWeight: 'bold' }}
                        >
                            {i18n.summary.replace('{0}', rows.length)}
                        </span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                            <button
                                className="btn btn-success"
                                onClick={doGenerate}
                                disabled={selCount === 0 || generating}
                                aria-busy={generating}
                            >
                                <i className="icon-cog icon-white" aria-hidden="true"></i> {i18n.auditGenerate}{selCount > 0 ? ' (' + selCount + ')' : ''}
                            </button>
                            <span role="status" aria-live="polite" style={{ fontSize: '0.75rem', color: genStatus.color }}>{genStatus.msg}</span>
                        </div>
                    </div>

                    {showProgress && (
                        <ProgressBar progress={progress} label={i18n.generating} />
                    )}

                    <div style={{ overflowX: 'auto' }}>
                        <table className="pl-audit-table" aria-describedby={showLegend ? 'plAuditLegend' : undefined}>
                            <caption className="sr-only">{i18n.auditTitle}</caption>
                            <thead>
                                <tr>
                                    <th scope="col" style={{ width: 28 }}>
                                        <input
                                            type="checkbox"
                                            aria-label={i18n.auditSelectAll}
                                            checked={allSelected}
                                            onChange={e => toggleSelectAll(e.target.checked)}
                                        />
                                    </th>
                                    <th scope="col">{i18n.auditColPath}</th>
                                    {langs.map(lang => (
                                        <th scope="col" key={lang} className="pl-lang-th" data-lang={lang}>
                                            {lang.toUpperCase()}<br/>
                                            <input
                                                type="checkbox"
                                                style={{ margin: '2px 0 0 0' }}
                                                aria-label={i18n.colLangTitle.replace('{0}', lang.toUpperCase())}
                                                checked={!!colAllSelected[lang]}
                                                onChange={e => { e.stopPropagation(); toggleColLang(lang, e.target.checked); }}
                                            />
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map(row => {
                                    const missingActive = [...row.missing].filter(l => !row.generated.has(l));
                                    const rowChecked = missingActive.length > 0 && missingActive.every(l => selections[row.uuid] && selections[row.uuid].has(l));
                                    const allDone = langs.every(l => !row.missing.has(l) || row.generated.has(l));
                                    return (
                                        <tr
                                            key={row.uuid}
                                            className={'pl-audit-row' + (row.isHomePage ? ' pl-row-ignored' : '') + (allDone ? ' pl-row-done' : '')}
                                            aria-disabled={row.isHomePage ? 'true' : undefined}
                                        >
                                            <td>
                                                <input
                                                    type="checkbox"
                                                    checked={rowChecked}
                                                    disabled={row.isHomePage}
                                                    aria-label={row.isHomePage ? i18n.homepageAria : row.path}
                                                    onChange={e => toggleRow(row.uuid, e.target.checked)}
                                                />
                                            </td>
                                            <td
                                                title={row.path}
                                                style={{ fontFamily: 'monospace', fontSize: '0.6875rem', color: '#4d4d4d', maxWidth: 340, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                                            >
                                                {row.path}
                                            </td>
                                            {langs.map(lang => {
                                                let cls = 'pl-pill';
                                                let pillLabel = '';
                                                let clickable = false;
                                                if (row.generated.has(lang)) {
                                                    cls += ' pl-pill-gen'; pillLabel = i18n.pillGenerated;
                                                } else if (!row.missing.has(lang)) {
                                                    cls += ' pl-pill-has'; pillLabel = i18n.pillExisting;
                                                } else {
                                                    const isSel = !!(selections[row.uuid] && selections[row.uuid].has(lang));
                                                    cls += isSel ? ' pl-pill-sel' : ' pl-pill-miss';
                                                    pillLabel = isSel ? i18n.pillSelected : i18n.pillMissing;
                                                    clickable = !row.isHomePage;
                                                }
                                                if (row.hasNoTitle) { cls += ' pl-notitle'; pillLabel += ' — ' + i18n.pillNoTitle; }
                                                const isPressed = !!(selections[row.uuid] && selections[row.uuid].has(lang));
                                                return (
                                                    <PillCell
                                                        key={lang}
                                                        cls={cls}
                                                        lang={lang}
                                                        pillLabel={pillLabel}
                                                        clickable={clickable}
                                                        pressed={isPressed}
                                                        onToggle={() => toggleCell(row.uuid, lang)}
                                                    />
                                                );
                                            })}
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {hasMore && (
                        <LoadMore
                            scanning={scanning}
                            label={i18n.auditLoadMore}
                            status={loadMoreStatus}
                            onClick={() => doScan(false)}
                        />
                    )}
                </div>
            )}
        </div>
    );
}
