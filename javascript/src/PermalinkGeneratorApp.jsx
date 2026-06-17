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
.permalink-mode-panel h4 { margin: 0 0 6px 0; font-size: 14px; }
.permalink-mode-panel p  { margin: 0; font-size: 13px; color: #555; }
.pl-audit { margin-top: 32px; border-top: 2px solid #e0e0e0; padding-top: 24px; max-width: 960px; }
.pl-audit h3 { margin-top: 0; font-size: 18px; }
.pl-audit-table { width: 100%; border-collapse: collapse; font-size: 12px; margin: 0; }
.pl-audit-table th { background: #f0f4f8; padding: 7px 8px; text-align: left;
                     border-bottom: 2px solid #ccd6e0; white-space: nowrap; }
.pl-audit-table td { padding: 6px 8px; border-bottom: 1px solid #eaeaea; vertical-align: middle; }
.pl-audit-table tr.pl-row-done td { opacity: 0.45; }
.pl-audit-table tr:hover td { background: #f9fbfd; }
.pl-lang-th { text-align: center !important; width: 44px; cursor: pointer; user-select: none; }
.pl-lang-th:hover { background: #dde8f0 !important; }
.pl-pill { display: inline-block; min-width: 28px; padding: 2px 5px; border-radius: 3px;
           font-size: 10px; font-weight: bold; text-transform: uppercase; }
.pl-pill-has  { background: #d4edda; color: #155724; cursor: default; }
.pl-pill-miss { background: #f8d7da; color: #721c24; cursor: pointer; }
.pl-pill-sel  { background: #fff3cd; color: #856404; cursor: pointer; outline: 2px solid #ffc107; }
.pl-pill-gen  { background: #d4edda; color: #155724; cursor: default; }
.pl-pill-spin  { background: #cce5ff; color: #004085; cursor: default; }
.pl-pill-stale  { background: #fde8c8; color: #7a4000; cursor: pointer; }
.pl-pill-manual { background: #ece8f7; color: #5c3d8f; cursor: pointer; }
.pl-progress-wrap { background: #e9ecef; border-radius: 3px; height: 8px; margin-bottom: 10px; overflow: hidden; }
.pl-progress-bar  { height: 8px; background: #3c8cba; border-radius: 3px;
                    width: 100%; transform: scaleX(0); transform-origin: left;
                    transition: transform 0.25s ease-out; will-change: transform; }
@media (prefers-reduced-motion: reduce) { .pl-progress-bar { transition: none; } }
.pl-notitle { font-style: italic; }
.pl-audit-table tr.pl-row-ignored td { opacity: 0.45; pointer-events: none; }
.pl-regen { margin-top: 32px; border-top: 2px solid #e0e0e0; padding-top: 24px; max-width: 960px; }
.pl-regen h3 { margin-top: 0; font-size: 18px; color: #555; }
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
