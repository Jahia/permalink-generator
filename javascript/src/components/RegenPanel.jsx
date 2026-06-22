import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { gql, postAction } from '../utils/api';
import {
    BATCH, CHUNK, GQL_QUERY,
    hasActiveDefault, isExcludedBySettings,
    selectCell, deselectCell, totalSelected
} from '../utils/permalink';
import { LegendItem, ProgressBar, PillCell, LoadMore } from './shared';

function ConfirmModal({ show, i18n, onCancel, onConfirm }) {
    const modalRef = useRef(null);
    const triggerRef = useRef(null);

    // Capture the element that had focus when the dialog opened, then move
    // focus into the dialog; restore focus to the trigger on close (SC 2.4.3).
    useEffect(() => {
        if (show) {
            triggerRef.current = document.activeElement;
            if (modalRef.current) modalRef.current.focus();
        } else if (triggerRef.current && typeof triggerRef.current.focus === 'function') {
            triggerRef.current.focus();
            triggerRef.current = null;
        }
    }, [show]);

    // Escape to cancel + Tab/Shift+Tab focus trap confined to the dialog (SC 2.1.2).
    useEffect(() => {
        if (!show) return undefined;
        const onKey = e => {
            if (e.key === 'Escape') { onCancel(); return; }
            if (e.key !== 'Tab' || !modalRef.current) return;
            const focusable = modalRef.current.querySelectorAll(
                'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'
            );
            if (focusable.length === 0) { e.preventDefault(); modalRef.current.focus(); return; }
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            const active = document.activeElement;
            if (e.shiftKey) {
                if (active === first || active === modalRef.current) { e.preventDefault(); last.focus(); }
            } else if (active === last || active === modalRef.current) {
                e.preventDefault();
                first.focus();
            }
        };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [show, onCancel]);

    if (!show) return null;
    return (
        <>
            <div
                style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1040 }}
                onClick={onCancel}
                aria-hidden="true"
            />
            <div
                ref={modalRef}
                className="modal"
                style={{ display: 'block', position: 'fixed', top: '20%', left: '50%', transform: 'translateX(-50%)', zIndex: 1050, width: 500, background: '#fff', borderRadius: 4, boxShadow: '0 3px 9px rgba(0,0,0,0.5)' }}
                tabIndex={-1}
                role="dialog"
                aria-modal="true"
                aria-labelledby="plConfirmTitle"
            >
                <div className="modal-header">
                    <button type="button" className="close" aria-label={i18n.cancel} onClick={onCancel}>
                        <span aria-hidden="true">&times;</span>
                    </button>
                    <h3 id="plConfirmTitle">{i18n.confirmTitle}</h3>
                </div>
                <div className="modal-body">
                    <p>{i18n.confirmBody}</p>
                </div>
                <div className="modal-footer">
                    <button className="btn" onClick={onCancel}>{i18n.cancel}</button>
                    {' '}
                    <button className="btn btn-warning" onClick={onConfirm}>
                        <i className="icon-cog icon-white" aria-hidden="true"></i> {i18n.confirmProceed}
                    </button>
                </div>
            </div>
        </>
    );
}

export default function RegenPanel({ contextPath, sitePath, langs, excludedPaths, actionUrl, i18n }) {
    const [bypassExcluded, setBypassExcluded] = useState(false);
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
    const [showConfirm, setShowConfirm] = useState(false);
    const [showLegend, setShowLegend] = useState(false);
    const [reportEntries, setReportEntries] = useState([]);

    // Independent per-query offsets to avoid pagination drift.
    const offsetPageRef = useRef(0);
    const offsetMixinRef = useRef(0);
    const totalScannedRef = useRef(0);
    const rowsRef = useRef([]);
    const selectionsRef = useRef({});
    // In-flight guard (replaces the stale `scanning`-in-deps guard).
    const inFlightRef = useRef(false);
    // Read bypassExcluded through a ref so the memoized doScan always sees the
    // latest value without being re-created on every checkbox toggle.
    const bypassExcludedRef = useRef(bypassExcluded);
    useEffect(() => { bypassExcludedRef.current = bypassExcluded; }, [bypassExcluded]);

    const doScan = useCallback(async (reset) => {
        if (inFlightRef.current) return;
        if (reset) {
            offsetPageRef.current = 0;
            offsetMixinRef.current = 0;
            totalScannedRef.current = 0;
            rowsRef.current = [];
            selectionsRef.current = {};
            setRows([]);
            setSelections({});
            setShowResults(false);
            setHasMore(false);
            setLoadMoreStatus('');
            setReportEntries([]);
        }

        inFlightRef.current = true;
        setScanning(true);
        setScanStatus({ msg: i18n.scanRunning, color: '#4d4d4d' });

        const escapedPath = sitePath.replace(/'/g, "''");
        const qPage  = `SELECT * FROM [jnt:page] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const qMixin = `SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const offPage = offsetPageRef.current;
        const offMixin = offsetMixinRef.current;
        const bypass = bypassExcludedRef.current;

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
                setScanStatus({ msg: i18n.errorGraphql.replace('{0}', errors.join('; ')), color: '#922b21' });
                return;
            }

            const pageFull = nodesOf(r1).length === BATCH;
            const mixinFull = nodesOf(r2).length === BATCH;
            if (pageFull) offsetPageRef.current += BATCH;
            if (mixinFull) offsetMixinRef.current += BATCH;
            const more = pageFull || mixinFull;

            totalScannedRef.current += nodes.length;

            const candidates = nodes.filter(n => bypass || !isExcludedBySettings(n.path, excludedPaths));

            // Preview call
            let previewMap = null;
            if (candidates.length > 0) {
                const params = new URLSearchParams();
                candidates.forEach(n => params.append('nodeIds[]', n.uuid));
                langs.forEach(l => params.append('languages[]', l));
                params.append('preview', 'true');
                params.append('bypassExcluded', bypass ? 'true' : 'false');
                try {
                    const previewData = await postAction(actionUrl, params);
                    if (previewData && previewData.results) {
                        previewMap = {};
                        previewData.results.forEach(r => {
                            if (!previewMap[r.uuid]) previewMap[r.uuid] = { stale: new Set(), manual: new Set() };
                            if (r.willChange) previewMap[r.uuid].stale.add(r.language);
                            if (r.isManual && !r.willChange) previewMap[r.uuid].manual.add(r.language);
                        });
                    } else {
                        previewMap = false;
                    }
                } catch(e) {
                    previewMap = false;
                }
            } else {
                previewMap = {};
            }

            const newRows = [];
            const previewFailed = previewMap === false;
            const sortedNodes = [...nodes].sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);

            for (const n of sortedNodes) {
                if (!bypass && isExcludedBySettings(n.path, excludedPaths)) continue;
                const missingLangs = new Set(langs.filter(l => !hasActiveDefault(n.vanityUrls, l)));
                let staleLangs, manualLangs;
                if (previewFailed) {
                    staleLangs = new Set(langs.filter(l => !missingLangs.has(l)));
                    manualLangs = new Set();
                } else if (previewMap) {
                    staleLangs = new Set(langs.filter(l => !missingLangs.has(l) && previewMap[n.uuid] && previewMap[n.uuid].stale.has(l)));
                    manualLangs = new Set(langs.filter(l => !missingLangs.has(l) && previewMap[n.uuid] && previewMap[n.uuid].manual.has(l)));
                } else {
                    staleLangs = new Set();
                    manualLangs = new Set();
                }
                if (missingLangs.size === 0 && staleLangs.size === 0 && manualLangs.size === 0) continue;
                const nodeName = n.path.split('/').pop();
                newRows.push({
                    uuid: n.uuid,
                    path: n.path,
                    displayName: n.displayName || nodeName,
                    hasNoTitle: (n.displayName === nodeName),
                    isHomePage: !!(n.isHomePage && n.isHomePage.value === 'true'),
                    missingLangs,
                    staleLangs,
                    manualLangs,
                    generated: new Set()
                });
            }

            // Auto-select stale + manual + missing
            let newSels = { ...selectionsRef.current };
            for (const row of newRows) {
                if (row.isHomePage) continue;
                row.missingLangs.forEach(l => { newSels = selectCell(newSels, row.uuid, l); });
                row.staleLangs.forEach(l => { newSels = selectCell(newSels, row.uuid, l); });
                row.manualLangs.forEach(l => { newSels = selectCell(newSels, row.uuid, l); });
            }
            selectionsRef.current = newSels;

            rowsRef.current = [...rowsRef.current, ...newRows];
            setRows([...rowsRef.current]);
            setSelections({ ...newSels });
            setHasMore(more);

            const scannedSoFar = Math.max(offsetPageRef.current, offsetMixinRef.current);
            if (more) {
                setLoadMoreStatus(i18n.loadMoreSt.replace('{0}', scannedSoFar).replace('{1}', rowsRef.current.length));
            } else {
                setLoadMoreStatus('');
            }

            setScanStatus({ msg: i18n.regenScanned.replace('{0}', totalScannedRef.current).replace('{1}', rowsRef.current.length), color: '#333' });
            setShowResults(true);
        } catch(e) {
            setScanStatus({ msg: i18n.errorNetwork.replace('{0}', e.message || '?'), color: '#922b21' });
        } finally {
            inFlightRef.current = false;
            setScanning(false);
        }
    }, [sitePath, contextPath, langs, excludedPaths, actionUrl, i18n]);

    function toggleCell(uuid, lang) {
        setSelections(prev => {
            if (prev[uuid] && prev[uuid].has(lang)) return deselectCell(prev, uuid, lang);
            return selectCell(prev, uuid, lang);
        });
    }

    function toggleRow(uuid, checked) {
        setSelections(prev => {
            let next = { ...prev };
            if (checked) {
                const row = rowsRef.current.find(r => r.uuid === uuid);
                if (row) langs.forEach(l => { if (!row.generated.has(l)) next = selectCell(next, uuid, l); });
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
                    langs.forEach(l => { if (!row.generated.has(l)) next = selectCell(next, row.uuid, l); });
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
                if (row.isHomePage || row.generated.has(lang)) return;
                if (checked) next = selectCell(next, row.uuid, lang);
                else next = deselectCell(next, row.uuid, lang);
            });
            return next;
        });
    }

    async function doGenerate() {
        setShowConfirm(false);
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
        const entries = [];

        setGenerating(true);
        setGenStatus({ msg: '', color: '#4d4d4d' });
        setShowProgress(true);
        setProgress(0);
        setReportEntries([]);

        try {
            for (const lang of langKeys) {
                const uuids = byLang[lang];
                for (let c = 0; c < uuids.length; c += CHUNK) {
                    const chunk = uuids.slice(c, c + CHUNK);
                    const params = new URLSearchParams();
                    chunk.forEach(uid => params.append('nodeIds[]', uid));
                    params.append('languages[]', lang);
                    params.append('force', 'true');

                    try {
                        const data = await postAction(actionUrl, params);
                        const chunkResults = (data && data.results) || [];
                        chunkResults.forEach(r => {
                            entries.push({ uuid: r.uuid, path: r.path, language: lang, action: r.action, url: r.url, oldUrl: r.oldUrl || '' });
                        });
                        // Fresh row objects with a new Set rather than in-place mutation.
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
                        done += chunk.length;
                        setProgress(Math.min(1, done / total));
                        setRows([...rowsRef.current]);
                    } catch(e) {
                        errorCount += chunk.length;
                        setGenStatus({ msg: i18n.regenError.replace('{0}', e.status || '?').replace('{1}', lang), color: '#922b21' });
                    }
                }
            }
        } finally {
            setShowProgress(false);
            setReportEntries([...entries]);
            if (errorCount > 0 && done > 0) {
                setGenStatus({ msg: i18n.regenPartial.replace('{0}', done).replace('{1}', errorCount), color: '#8a4500' });
            } else if (errorCount > 0) {
                setGenStatus({ msg: i18n.regenError.replace('{0}', '?').replace('{1}', '—'), color: '#922b21' });
            } else if (done > 0) {
                setGenStatus({ msg: i18n.regenSuccess.replace('{0}', done), color: '#0a4d25' });
            } else {
                setGenStatus({ msg: i18n.regenZero, color: '#8a4500' });
            }
            setGenerating(false);
        }
    }

    const selCount = totalSelected(selections);

    const allActive = rows.reduce((acc, row) => {
        if (row.isHomePage) return acc;
        langs.forEach(l => { if (!row.generated.has(l)) acc.push({ uuid: row.uuid, l }); });
        return acc;
    }, []);
    const allSelected = allActive.length > 0 && allActive.every(item => selections[item.uuid] && selections[item.uuid].has(item.l));

    // Per-column "all selected" state, hoisted out of render's inline IIFE.
    const colAllSelected = useMemo(() => {
        const map = {};
        langs.forEach(lang => {
            const colActive = rows.filter(r => !r.generated.has(lang) && !r.isHomePage);
            map[lang] = colActive.length > 0 && colActive.every(r => selections[r.uuid] && selections[r.uuid].has(lang));
        });
        return map;
    }, [langs, rows, selections]);

    const hasOldUrls = reportEntries.some(e => e.oldUrl);
    const actionLabel = { created: i18n.reportCreated, promoted: i18n.reportPromoted, already_correct: i18n.reportCorrect };
    const actionColor = { created: '#0a4d25', promoted: '#1d5278', already_correct: '#4d4d4d' };

    return (
        <div className="pl-regen">
            <ConfirmModal show={showConfirm} i18n={i18n} onCancel={() => setShowConfirm(false)} onConfirm={doGenerate} />

            <h3 style={{ display: 'flex', alignItems: 'center' }}>
                {i18n.regenTitle}
                <button
                    className="pl-help-btn"
                    aria-expanded={showLegend}
                    aria-controls="plRegenLegend"
                    aria-label={i18n.legendHelp}
                    onClick={() => setShowLegend(v => !v)}
                >?</button>
            </h3>
            <p className="text-muted">{i18n.regenDesc}</p>

            <div id="plRegenLegend" className={'pl-legend-wrap' + (showLegend ? ' open' : '')}>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center', padding: '0 0 16px', fontSize: '0.75rem' }}>
                    <LegendItem cls="pl-pill-miss"   label={i18n.pillMissing} />
                    <LegendItem cls="pl-pill-stale"  label={i18n.pillStale} />
                    <LegendItem cls="pl-pill-manual" label={i18n.pillManual} />
                    <LegendItem cls="pl-pill-has"    label={i18n.pillHasForce} />
                    <LegendItem cls="pl-pill-sel"    label={i18n.pillSelForce} />
                    <LegendItem cls="pl-pill-gen"    label={i18n.pillGenerated} />
                </div>
            </div>

            <div className="control-group">
                <div className="controls" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <button
                        className="btn"
                        onClick={() => doScan(true)}
                        disabled={scanning}
                        aria-busy={scanning}
                    >
                        <i className="icon-search" aria-hidden="true"></i> {i18n.regenScan}
                    </button>
                    <label style={{ margin: 0, fontWeight: 'normal', fontSize: '0.8125rem' }}>
                        <input
                            type="checkbox"
                            checked={bypassExcluded}
                            onChange={e => setBypassExcluded(e.target.checked)}
                            style={{ marginRight: 4 }}
                        />
                        {i18n.regenBypass}
                    </label>
                    <span role="status" aria-live="polite" style={{ fontSize: '0.75rem', color: scanStatus.color }}>{scanStatus.msg}</span>
                </div>
            </div>

            {showResults && (
                <div style={{ marginTop: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
                        <span role="status" aria-live="polite" style={{ fontSize: '0.8125rem', fontWeight: 'bold' }}>
                            {i18n.regenSummary.replace('{0}', rows.length)}
                        </span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                            <button
                                className="btn btn-warning"
                                onClick={() => setShowConfirm(true)}
                                disabled={selCount === 0 || generating}
                                aria-busy={generating}
                            >
                                <i className="icon-cog icon-white" aria-hidden="true"></i> {i18n.regenGenerate} ({selCount})
                            </button>
                            <span role="status" aria-live="polite" style={{ fontSize: '0.75rem', color: genStatus.color }}>{genStatus.msg}</span>
                        </div>
                    </div>

                    {showProgress && (
                        <ProgressBar progress={progress} label={i18n.generating} />
                    )}

                    <div style={{ overflowX: 'auto' }}>
                        <table className="pl-audit-table">
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
                                    const active = langs.filter(l => !row.generated.has(l));
                                    const rowChecked = active.length > 0 && active.every(l => selections[row.uuid] && selections[row.uuid].has(l));
                                    const allDone = langs.every(l => row.generated.has(l));
                                    return (
                                        <tr
                                            key={row.uuid}
                                            className={'pl-audit-row' + (row.isHomePage ? ' pl-row-ignored' : '') + (allDone ? ' pl-row-done' : '')}
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
                                                style={{ fontFamily: 'monospace', fontSize: '0.6875rem', color: '#4d4d4d', maxWidth: 380, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                                            >
                                                {row.path}
                                            </td>
                                            {langs.map(lang => {
                                                let cls = 'pl-pill';
                                                let pillLabel = '';
                                                const clickable = !row.isHomePage && !row.generated.has(lang);
                                                if (row.generated.has(lang)) {
                                                    cls += ' pl-pill-gen'; pillLabel = i18n.pillGenerated;
                                                } else {
                                                    const isSel = !!(selections[row.uuid] && selections[row.uuid].has(lang));
                                                    if (isSel) {
                                                        cls += ' pl-pill-sel'; pillLabel = i18n.pillSelForce;
                                                    } else if (row.missingLangs.has(lang)) {
                                                        cls += ' pl-pill-miss'; pillLabel = i18n.pillMissing;
                                                    } else if (row.staleLangs && row.staleLangs.has(lang)) {
                                                        cls += ' pl-pill-stale'; pillLabel = i18n.pillStale;
                                                    } else if (row.manualLangs && row.manualLangs.has(lang)) {
                                                        cls += ' pl-pill-manual'; pillLabel = i18n.pillManual;
                                                    } else {
                                                        cls += ' pl-pill-has'; pillLabel = i18n.pillHasForce;
                                                    }
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

                    {reportEntries.length > 0 && (
                        <div style={{ marginTop: 20 }}>
                            <h4 style={{ margin: '0 0 8px 0', fontSize: '0.875rem' }}>{i18n.reportTitle}</h4>
                            <div style={{ overflowX: 'auto' }}>
                                <table className="pl-audit-table" style={{ fontSize: '0.6875rem' }}>
                                    <thead>
                                        <tr>
                                            <th scope="col" style={{ width: 44 }}>{i18n.reportColLang}</th>
                                            <th scope="col">{i18n.reportColPath}</th>
                                            <th scope="col">{i18n.reportColAction}</th>
                                            {hasOldUrls && <th scope="col">{i18n.reportColOldUrl}</th>}
                                            <th scope="col">{i18n.reportColNewUrl}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {reportEntries.map((e, idx) => (
                                            <tr key={(e.uuid || e.path) + '-' + e.language + '-' + idx}>
                                                <td style={{ textAlign: 'center' }}>
                                                    <span className="pl-pill" style={{ background: '#e8eef4', color: '#333' }}>{e.language.toUpperCase()}</span>
                                                </td>
                                                <td style={{ fontFamily: 'monospace', maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={e.path}>
                                                    {e.path}
                                                </td>
                                                <td style={{ color: actionColor[e.action] || '#4d4d4d', whiteSpace: 'nowrap' }}>
                                                    {actionLabel[e.action] || e.action}
                                                </td>
                                                {hasOldUrls && <td style={{ fontFamily: 'monospace', color: '#4d4d4d' }}>{e.oldUrl || '—'}</td>}
                                                <td style={{ fontFamily: 'monospace' }}>{e.url}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
