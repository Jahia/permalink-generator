import React, { useState, useRef, useCallback } from 'react';
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

export default function AuditPanel({ contextPath, sitePath, langs, excludedPaths, actionUrl, i18n }) {
    const [showLegend, setShowLegend] = useState(false);
    const [scanPath, setScanPath] = useState(sitePath);
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

    const offsetRef = useRef(0);
    const totalScannedRef = useRef(0);
    const rowsRef = useRef([]);

    function totalSelected(sels) {
        return Object.values(sels).reduce((n, s) => n + s.size, 0);
    }

    const doScan = useCallback(async (reset) => {
        if (scanning) return;
        const path = scanPath.trim();
        if (!path.startsWith('/sites/')) {
            setScanStatus({ msg: i18n.pathRequired, color: '#c0392b' });
            return;
        }

        if (reset) {
            offsetRef.current = 0;
            totalScannedRef.current = 0;
            rowsRef.current = [];
            setRows([]);
            setSelections({});
            setShowResults(false);
            setHasMore(false);
            setLoadMoreStatus('');
        }

        setScanning(true);
        setScanStatus({ msg: i18n.scanRunning, color: '#666' });

        const escapedPath = path.replace(/'/g, "''");
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
                return;
            }

            const more = [r1, r2].some(data => { try { return (data.data.jcr.nodesByQuery.nodes || []).length === BATCH; } catch(e) { return false; } });
            totalScannedRef.current += nodes.length;
            offsetRef.current += BATCH;

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

            if (more) {
                setLoadMoreStatus(i18n.loadMoreSt.replace('{0}', offsetRef.current).replace('{1}', missing));
            } else {
                setLoadMoreStatus('');
            }

            if (missing === 0 && !more) {
                setScanStatus({ msg: i18n.allGood.replace('{0}', total), color: '#27ae60' });
            } else {
                setScanStatus({ msg: i18n.scanned.replace('{0}', total).replace('{1}', missing), color: missing > 0 ? '#333' : '#27ae60' });
                setShowResults(true);
            }
        } catch(e) {
            setScanStatus({ msg: i18n.errorNetwork.replace('{0}', e.message || '?'), color: '#c0392b' });
        } finally {
            setScanning(false);
        }
    }, [scanning, scanPath, contextPath, langs, excludedPaths, i18n]);

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
        let hadError = false;
        setGenerating(true);
        setGenStatus({ msg: '', color: '#666' });
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
                        await postAction(actionUrl, params);
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
                        setGenStatus({ msg: i18n.genError.replace('{0}', e.status || '?').replace('{1}', lang), color: '#c0392b' });
                    }
                }
            }
        } finally {
            setShowProgress(false);
            if (done > 0) {
                setGenStatus({ msg: i18n.genSuccess.replace('{0}', done), color: '#27ae60' });
            } else if (!hadError) {
                setGenStatus({ msg: i18n.genZero, color: '#e67e22' });
            }
            setGenerating(false);
        }
    }

    const selCount = totalSelected(selections);

    const allMissing = rowsRef.current.reduce((acc, row) => {
        if (row.isHomePage) return acc;
        row.missing.forEach(l => { if (!row.generated.has(l)) acc.push({ uuid: row.uuid, l }); });
        return acc;
    }, []);
    const allSelected = allMissing.length > 0 && allMissing.every(item => selections[item.uuid] && selections[item.uuid].has(item.l));

    return (
        <div className="pl-audit">
            <h3 style={{ display: 'flex', alignItems: 'center' }}>
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

            <div id="plAuditLegend" className={'pl-legend-wrap' + (showLegend ? ' open' : '')}>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center', padding: '0 0 16px', fontSize: 12 }}>
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
                        onChange={e => setScanPath(e.target.value)}
                        style={{ fontFamily: 'monospace', fontSize: 12 }}
                    />
                    <button
                        className="btn"
                        onClick={() => doScan(true)}
                        disabled={scanning}
                        aria-busy={scanning}
                    >
                        <i className="icon-search" aria-hidden="true"></i> {i18n.auditScan}
                    </button>
                    <span role="status" aria-live="polite" style={{ fontSize: 12, color: scanStatus.color }}>{scanStatus.msg}</span>
                </div>
            </div>

            {showResults && (
                <div style={{ marginTop: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
                        <span aria-live="polite" style={{ fontSize: 13, fontWeight: 'bold' }}>
                            {i18n.summary.replace('{0}', rows.length)}
                        </span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                            <button
                                className="btn btn-success"
                                onClick={doGenerate}
                                disabled={selCount === 0 || generating}
                                aria-busy={generating}
                            >
                                <i className="icon-cog icon-white" aria-hidden="true"></i> {i18n.auditGenerate} ({selCount})
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
                                                    const colMissing = rows.filter(r => r.missing.has(lang) && !r.generated.has(lang));
                                                    return colMissing.length > 0 && colMissing.every(r => selections[r.uuid] && selections[r.uuid].has(lang));
                                                })()}
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
                                        >
                                            <td>
                                                <input
                                                    type="checkbox"
                                                    checked={rowChecked}
                                                    disabled={row.isHomePage}
                                                    aria-label={row.isHomePage ? 'Homepage — skipped' : row.path}
                                                    onChange={e => toggleRow(row.uuid, e.target.checked)}
                                                />
                                            </td>
                                            <td
                                                title={row.path}
                                                style={{ fontFamily: 'monospace', fontSize: 11, color: '#666', maxWidth: 340, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
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
                </div>
            )}
        </div>
    );
}
