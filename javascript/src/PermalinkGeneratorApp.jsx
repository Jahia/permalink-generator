import React from 'react';
import SiteSettings from './components/SiteSettings';
import AuditPanel from './components/AuditPanel';
import RegenPanel from './components/RegenPanel';

const CUSTOM_CSS = `
.permalink-mode-panel {
    border: 1px solid #c8dcea;
    background: #f5f9fd;
    border-radius: 4px;
    padding: 14px 18px;
    margin-top: 12px;
}
.permalink-mode-panel.force  { border-color: #f0c890; background: #fdf6ee; }
.permalink-mode-panel.disabled { border-color: #ddd; background: #f5f5f5; }
.permalink-mode-panel h4 { margin: 0 0 6px 0; font-size: 0.875rem; }
.permalink-mode-panel p  { margin: 0; font-size: 0.8125rem; color: #4d4d4d; }
.pl-audit { margin-top: 32px; border-top: 2px solid #e0e0e0; padding-top: 24px; max-width: 960px; }
.pl-audit h3 { margin-top: 0; font-size: 1.125rem; }
.pl-audit-table { width: 100%; border-collapse: collapse; font-size: 0.75rem; margin: 0; }
.pl-audit-table th { background: #f0f4f8; padding: 7px 8px; text-align: left;
                     border-bottom: 2px solid #ccd6e0; white-space: nowrap; }
.pl-audit-table td { padding: 6px 8px; border-bottom: 1px solid #eaeaea; vertical-align: middle; }
.pl-audit-table tr.pl-row-done td { opacity: 0.45; }
.pl-audit-table tr:hover td { background: #f9fbfd; }
.pl-lang-th { text-align: center !important; width: 44px; cursor: pointer; user-select: none; }
.pl-lang-th:hover { background: #dde8f0 !important; }
/* Pills: >=44px hit area via min sizing + padding; non-color status signal
   provided by a per-state ::before glyph AND a per-state border-style so the
   meaning never relies on color alone (WCAG SC 1.4.1). */
.pl-pill { display: inline-flex; align-items: center; justify-content: center;
           min-width: 44px; min-height: 24px; padding: 6px 8px; border-radius: 3px;
           font-size: 0.625rem; font-weight: bold; text-transform: uppercase;
           border: 2px solid transparent; box-sizing: border-box; }
.pl-pill::before { font-size: 0.75rem; margin-right: 3px; line-height: 1; }
.pl-pill-has    { background: #d4edda; color: #0f4019; cursor: default; border-style: solid; }
.pl-pill-has::before    { content: "\\2713"; }                 /* check */
.pl-pill-miss   { background: #f8d7da; color: #721c24; cursor: pointer; border-style: dashed; }
.pl-pill-miss::before   { content: "\\2715"; }                 /* cross */
.pl-pill-sel    { background: #fff3cd; color: #5a4200; cursor: pointer; outline: 2px solid #ffc107; border-style: dotted; }
.pl-pill-sel::before    { content: "\\25CF"; }                 /* filled dot */
.pl-pill-gen    { background: #d4edda; color: #0f4019; cursor: default; border-style: solid; }
.pl-pill-gen::before    { content: "\\2713"; }                 /* check */
.pl-pill-spin   { background: #cce5ff; color: #004085; cursor: default; }
.pl-pill-stale  { background: #fde8c8; color: #5e3000; cursor: pointer; border-style: dashed; }
.pl-pill-stale::before  { content: "\\21BB"; }                 /* refresh arrow */
.pl-pill-manual { background: #ece8f7; color: #4a2e7a; cursor: pointer; border-style: double; }
.pl-pill-manual::before { content: "\\270E"; }                 /* pencil */
.pl-pill:focus-visible { outline: 2px solid #1d5278; outline-offset: 2px; }
.pl-progress-wrap { background: #e9ecef; border-radius: 3px; height: 8px; margin-bottom: 10px; overflow: hidden; }
.pl-progress-bar  { height: 8px; background: #1d5278; border-radius: 3px;
                    width: 100%; transform: scaleX(0); transform-origin: left;
                    transition: transform 0.25s ease-out; will-change: transform; }
@media (prefers-reduced-motion: reduce) { .pl-progress-bar { transition: none; } }
.pl-notitle { font-style: italic; }
.pl-audit-table tr.pl-row-ignored td { opacity: 0.45; pointer-events: none; }
.pl-regen { margin-top: 32px; border-top: 2px solid #e0e0e0; padding-top: 24px; max-width: 960px; }
.pl-regen h3 { margin-top: 0; font-size: 1.125rem; color: #4d4d4d; }
.pl-help-btn {
    display: inline-flex; align-items: center; justify-content: center;
    width: 44px; height: 44px; border-radius: 50%;
    border: 1px solid #767676; background: transparent;
    color: #4d4d4d; font-size: 0.875rem; font-weight: bold;
    cursor: pointer; margin-left: 8px; vertical-align: middle; line-height: 1;
    transition: background 150ms ease-out, color 150ms ease-out, border-color 150ms ease-out;
}
.pl-help-btn:hover, .pl-help-btn[aria-expanded="true"] {
    background: #1d5278; color: #fff; border-color: #1d5278;
}
.pl-help-btn:focus-visible { outline: 2px solid #1d5278; outline-offset: 2px; }
.pl-legend-wrap {
    display: grid; grid-template-rows: 0fr; opacity: 0;
    transition: grid-template-rows 180ms ease-out, opacity 150ms ease-out;
}
.pl-legend-wrap > * { min-height: 0; overflow: hidden; }
.pl-legend-wrap.open { grid-template-rows: 1fr; opacity: 1; }
@media (prefers-reduced-motion: reduce) { .pl-legend-wrap { transition: none; } }
`;

export default function PermalinkGeneratorApp({ contextPath, sitePath, siteLangs, excludedPaths, currentMode, actionUrl, i18n }) {
    const langs = [...(siteLangs || [])].sort();
    return (
        <>
            <style>{CUSTOM_CSS}</style>
            <SiteSettings
                contextPath={contextPath}
                sitePath={sitePath}
                currentMode={currentMode}
                excludedPaths={excludedPaths || []}
                i18n={i18n}
            />
            <AuditPanel
                contextPath={contextPath}
                sitePath={sitePath}
                langs={langs}
                excludedPaths={excludedPaths || []}
                actionUrl={actionUrl}
                i18n={i18n}
            />
            <RegenPanel
                contextPath={contextPath}
                sitePath={sitePath}
                langs={langs}
                excludedPaths={excludedPaths || []}
                actionUrl={actionUrl}
                i18n={i18n}
            />
        </>
    );
}
