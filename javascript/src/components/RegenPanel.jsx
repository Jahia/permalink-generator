import React, { useState, useRef, useEffect, useCallback } from 'react';
import { gql, postAction } from '../utils/api';

const BATCH = 100;
const CHUNK = 20;

const GQL_QUERY = 'query($q:String!,$lim:Int!,$off:Int!){jcr{nodesByQuery(query:$q,queryLanguage:SQL2,limit:$lim,offset:$off){nodes{uuid path displayName isHomePage:property(name:"j:isHomePage"){value} vanityUrls{url language active default}}}}}';

function hasActiveDefault(vanityUrls, lang) {
    return (vanityUrls || []).some(v => v.language === lang && v.active && v['default']);
}

function isExcludedBySettings(nodePath, excludedPaths) {
    return excludedPaths.some(ep => ep && nodePath.startsWith(ep));
}

function LegendItem({ cls, label }) {
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <span className={'pl-pill ' + cls} style={{ cursor: 'default', pointerEvents: 'none' }} aria-hidden="true">XX</span>
            <span style={{ color: '#555' }}>{label}</span>
        </span>
    );
}

function ConfirmModal({ show, i18n, onCancel, onConfirm }) {
    const modalRef = useRef(null);

    useEffect(() => {
        if (show && modalRef.current) modalRef.current.focus();
    }, [show]);

    useEffect(() => {
        if (!show) return;
        const onKey = e => { if (e.key === 'Escape') onCancel(); };
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
    const [scanStatus, setScanStatus] = useState({ msg: '', color: '#666' });
    const [showResults, setShowResults] = useState(false);
    const [rows, setRows] = useState([]);
    const [selections, setSelections] = useState({});
    const [genStatus, setGenStatus] = useState({ msg: '', color: '#666' });
    const [generating, setGenerating] = useState(false);
    const [progress, setProgress] = useState(0);
    const [showProgress, setShowProgress] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [loadMoreStatus, setLoadMoreStatus] = useState('');
    const [showConfirm, setShowConfirm] = useState(false);
    const [showLegend, setShowLegend] = useState(false);
    const [reportEntries, setReportEntries] = useState([]);

    const offsetRef = useRef(0);
    const totalScannedRef = useRef(0);
    const rowsRef = useRef([]);
    const selectionsRef = useRef({});

    function totalSelected(sels) {
        return Object.values(sels).reduce((n, s) => n + s.size, 0);
    }

    function selectCell(sels, uuid, lang) {
        const next = { ...sels };
        if (!next[uuid]) next[uuid] = new Set();
        else next[uuid] = new Set(next[uuid]);
        next[uuid].add(lang);
        return next;
    }
    function deselectCell(sels, uuid, lang) {
        if (!sels[uuid]) return sels;
        const next = { ...sels };
        next[uuid] = new Set(next[uuid]);
        next[uuid].delete(lang);
        if (next[uuid].size === 0) delete next[uuid];
        return next;
    }

    const doScan = useCallback(async (reset) => {
        if (scanning) return;
        if (reset) {
            offsetRef.current = 0;
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

        setScanning(true);
        setScanStatus({ msg: i18n.scanRunning, color: '#666' });

        const escapedPath = sitePath.replace(/'/g, "''");
        const qPage  = `SELECT * FROM [jnt:page] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const qMixin = `SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '${escapedPath}')`;
        const off = offsetRef.current;

        try {
            const [r1, r2] = await Promise.all([
                gql(contextPath, { query: GQL_QUERY, variables: { q: qPage, lim: BATCH, off } }),
                gql(contextPath, { query: GQL_QUERY, variables: { q: qMixin, lim: BATCH, off } })
            ]);

            const seen = {};
            const nodes = [];
            const errors = [];
            for (const data of [r1, r2]) {
                if (data.errors) { errors.push(...data.errors.map(e => e.message)); continue; }
                try { (data.data.jcr.nodesByQuery.nodes || []).forEach(n => { if (!seen[n.uuid]) { seen[n.uuid] = true; nodes.push(n); } }); } catch(e) {}
            }

            if (errors.length && nodes.length === 0) {
                setScanStatus({ msg: i18n.errorGraphql.replace('{0}', errors.join('; ')), color: '#c0392b' });
                setScanning(false);
                return;
            }

            const more = [r1, r2].some(data => { try { return (data.data.jcr.nodesByQuery.nodes || []).length === BATCH; } catch(e) { return false; } });
            totalScannedRef.current += nodes.length;
            offsetRef.current += BATCH;

            const bypass = bypassExcluded;
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

            if (more) {
                setLoadMoreStatus(i18n.loadMoreSt.replace('{0}', offsetRef.current).replace('{1}', rowsRef.current.length));
            } else {
                setLoadMoreStatus('');
            }

            setScanStatus({ msg: i18n.regenScanned.replace('{0}', totalScannedRef.current).replace('{1}', rowsRef.current.length), color: '#333' });
            setShowResults(true);
        } catch(e) {
            setScanStatus({ msg: i18n.errorNetwork.replace('{0}', e.message || '?'), color: '#c0392b' });
        } finally {
            setScanning(false);
        }
    }, [scanning, sitePath, contextPath, langs, excludedPaths, bypassExcluded, actionUrl, i18n]);

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
        let hadError = false;
        const entries = [];

        setGenerating(true);
        setGenStatus({ msg: '', color: '#666' });
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
                            entries.push({ path: r.path, language: lang, action: r.action, url: r.url, oldUrl: r.oldUrl || '' });
                        });
                        for (const uuid of chunk) {
                            const row = rowsRef.current.find(r => r.uuid === uuid);
                            if (row) row.generated.add(lang);
                        }
                        setSelections(prev => {
                            let next = { ...prev };
                            chunk.forEach(uuid => { next = deselectCell(next, uuid, lang); });
                            return next;
                        });
                        done += chunk.length;
                        setProgress(Math.min(1, done / total));
                        setRows([...rowsRef.current]);
                    } catch(e) {
                        hadError = true;
                        setGenStatus({ msg: i18n.regenError.replace('{0}', e.status || '?').replace('{1}', lang), color: '#c0392b' });
                    }
                }
            }
        } finally {
            setShowProgress(false);
            setReportEntries([...entries]);
            if (done > 0) {
                setGenStatus({ msg: i18n.regenSuccess.replace('{0}', done), color: '#27ae60' });
            } else if (!hadError) {
                setGenStatus({ msg: i18n.regenZero, color: '#e67e22' });
            }
            setGenerating(false);
        }
    }

    const selCount = totalSelected(selections);

    const allActive = rowsRef.current.reduce((acc, row) => {
        if (row.isHomePage) return acc;
        langs.forEach(l => { if (!row.generated.has(l)) acc.push({ uuid: row.uuid, l }); });
        return acc;
    }, []);
    const allSelected = allActive.length > 0 && allActive.every(item => selections[item.uuid] && selections[item.uuid].has(item.l));

    const hasOldUrls = reportEntries.some(e => e.oldUrl);
    const actionLabel = { created: i18n.reportCreated, promoted: i18n.reportPromoted, already_correct: i18n.reportCorrect };
    const actionColor = { created: '#27ae60', promoted: '#3c8cba', already_correct: '#888' };

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
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center', padding: '0 0 16px', fontSize: 12 }}>
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
                    <label style={{ margin: 0, fontWeight: 'normal', fontSize: 13 }}>
                        <input
                            type="checkbox"
                            checked={bypassExcluded}
                            onChange={e => setBypassExcluded(e.target.checked)}
                            style={{ marginRight: 4 }}
                        />
                        {i18n.regenBypass}
                    </label>
                    <span role="status" aria-live="polite" style={{ fontSize: 12, color: scanStatus.color }}>{scanStatus.msg}</span>
                </div>
            </div>

            {showResults && (
                <div style={{ marginTop: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
                        <span aria-live="polite" style={{ fontSize: 13, fontWeight: 'bold' }}>
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
                            <span role="status" aria-live="polite" style={{ fontSize: 12, color: genStatus.color }}>{genStatus.msg}</span>
                        </div>
                    </div>

                    {showProgress && (
                        <div
                            className="pl-progress-wrap"
                            role="progressbar"
                            aria-valuenow={Math.round(progress * 100)}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-label={i18n.scanRunning}
                        >
                            <div className="pl-progress-bar" style={{ transform: `scaleX(${progress})` }} aria-hidden="true"></div>
                        </div>
                    )}

                    <div style={{ overflowX: 'auto' }}>
                        <table className="pl-audit-table">
                            <thead>
                                <tr>
                                    <th style={{ width: 28 }}>
                                        <input
                                            type="checkbox"
                                            aria-label={i18n.auditSelectAll}
                                            checked={allSelected}
                                            onChange={e => toggleSelectAll(e.target.checked)}
                                        />
                                    </th>
                                    <th>{i18n.auditColPath}</th>
                                    {langs.map(lang => (
                                        <th key={lang} className="pl-lang-th" data-lang={lang}>
                                            {lang.toUpperCase()}<br/>
                                            <input
                                                type="checkbox"
                                                style={{ margin: '2px 0 0 0' }}
                                                aria-label={i18n.colLangTitle.replace('{0}', lang.toUpperCase())}
                                                checked={(() => {
                                                    const colActive = rows.filter(r => !r.generated.has(lang) && !r.isHomePage);
                                                    return colActive.length > 0 && colActive.every(r => selections[r.uuid] && selections[r.uuid].has(lang));
                                                })()}
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
                                                style={{ fontFamily: 'monospace', fontSize: 11, color: '#666', maxWidth: 380, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
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
                                                    <td key={lang} style={{ textAlign: 'center' }}>
                                                        <span
                                                            className={cls}
                                                            title={pillLabel}
                                                            role={clickable ? 'button' : undefined}
                                                            tabIndex={clickable ? 0 : undefined}
                                                            aria-pressed={clickable ? isPressed : undefined}
                                                            aria-label={clickable ? `${lang.toUpperCase()} — ${pillLabel}` : undefined}
                                                            onClick={clickable ? () => toggleCell(row.uuid, lang) : undefined}
                                                            onKeyDown={clickable ? e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleCell(row.uuid, lang); } } : undefined}
                                                        >
                                                            {lang}
                                                        </span>
                                                    </td>
                                                );
                                            })}
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {hasMore && (
                        <div style={{ textAlign: 'center', marginTop: 12 }}>
                            <button className="btn" onClick={() => doScan(false)} disabled={scanning} aria-busy={scanning}>
                                {i18n.auditLoadMore}
                            </button>
                            {loadMoreStatus && (
                                <span role="status" aria-live="polite" style={{ fontSize: 12, color: '#777', marginLeft: 8 }}>
                                    {loadMoreStatus}
                                </span>
                            )}
                        </div>
                    )}

                    {reportEntries.length > 0 && (
                        <div style={{ marginTop: 20 }}>
                            <h4 style={{ margin: '0 0 8px 0', fontSize: 14 }}>{i18n.reportTitle}</h4>
                            <div style={{ overflowX: 'auto' }}>
                                <table className="pl-audit-table" style={{ fontSize: 11 }}>
                                    <thead>
                                        <tr>
                                            <th style={{ width: 44 }}>{i18n.reportColLang}</th>
                                            <th>{i18n.reportColPath}</th>
                                            <th>{i18n.reportColAction}</th>
                                            {hasOldUrls && <th>{i18n.reportColOldUrl}</th>}
                                            <th>{i18n.reportColNewUrl}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {reportEntries.map((e, idx) => (
                                            <tr key={idx}>
                                                <td style={{ textAlign: 'center' }}>
                                                    <span className="pl-pill" style={{ background: '#e8eef4', color: '#333' }}>{e.language.toUpperCase()}</span>
                                                </td>
                                                <td style={{ fontFamily: 'monospace', maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={e.path}>
                                                    {e.path}
                                                </td>
                                                <td style={{ color: actionColor[e.action] || '#555', whiteSpace: 'nowrap' }}>
                                                    {actionLabel[e.action] || e.action}
                                                </td>
                                                {hasOldUrls && <td style={{ fontFamily: 'monospace', color: '#999' }}>{e.oldUrl || '—'}</td>}
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
