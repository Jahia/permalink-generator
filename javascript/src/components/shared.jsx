import React from 'react';

// Shared presentational components used by both AuditPanel and RegenPanel.

export function LegendItem({ cls, label }) {
    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <span
                className={'pl-pill ' + cls}
                style={{ cursor: 'default', pointerEvents: 'none', minWidth: 28, minHeight: 'unset', padding: '2px 5px' }}
                aria-hidden="true"
            >XX</span>
            <span style={{ color: '#4d4d4d' }}>{label}</span>
        </span>
    );
}

/**
 * Accessible progress bar. aria-label is provided by the caller so the right
 * i18n string (scan vs generate) is announced.
 */
export function ProgressBar({ progress, label }) {
    return (
        <div
            className="pl-progress-wrap"
            role="progressbar"
            aria-valuenow={Math.round(progress * 100)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={label}
        >
            <div
                className="pl-progress-bar"
                style={{ transform: `scaleX(${progress})` }}
                aria-hidden="true"
            ></div>
        </div>
    );
}

/**
 * A single language pill inside a results table cell.
 * `cls` is the full class string (without the leading non-color marker),
 * `lang` the language code, `pillLabel` the accessible description,
 * `clickable`/`pressed` drive interactivity, `onToggle` flips the selection.
 */
export function PillCell({ cls, lang, pillLabel, clickable, pressed, onToggle }) {
    return (
        <td style={{ textAlign: 'center' }}>
            <span
                className={cls}
                title={pillLabel}
                role={clickable ? 'button' : undefined}
                tabIndex={clickable ? 0 : undefined}
                aria-pressed={clickable ? pressed : undefined}
                aria-label={`${lang.toUpperCase()} — ${pillLabel}`}
                onClick={clickable ? onToggle : undefined}
                onKeyDown={clickable ? e => {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onToggle(); }
                } : undefined}
            >
                {lang}
                {/* Visually hidden status text for non-interactive pills (SC 4.1.2). */}
                {!clickable && <span className="sr-only"> — {pillLabel}</span>}
            </span>
        </td>
    );
}

/**
 * "Load more" footer shown when a scan is paginated.
 */
export function LoadMore({ scanning, label, status, onClick }) {
    return (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
            <button className="btn" onClick={onClick} disabled={scanning} aria-busy={scanning}>
                {label}
            </button>
            {status && (
                <span role="status" aria-live="polite" style={{ fontSize: '0.75rem', color: '#4d4d4d', marginLeft: 8 }}>
                    {status}
                </span>
            )}
        </div>
    );
}
