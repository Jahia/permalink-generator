import React, { useState } from 'react';
import { gql } from '../utils/api';

const GQL_SAVE = 'mutation setPermalinkSettings($path:String!,$mode:String!,$paths:[String]!){jcr{mutateNode(pathOrId:$path){addMixins(mixins:["jmix:permalinkGeneratorSettings"]) modeProperty:mutateProperty(name:"j:permalinkGeneratorMode"){setValue(type:STRING,value:$mode)} excludedPaths:mutateProperty(name:"j:excludedPaths"){setValues(type:STRING,values:$paths)}}}}';

export default function SiteSettings({ contextPath, sitePath, currentMode, excludedPaths, i18n }) {
    const [mode, setMode] = useState(currentMode || 'SMART');
    const [paths, setPaths] = useState((excludedPaths || []).join('\n'));
    const [saving, setSaving] = useState(false);
    const [status, setStatus] = useState(null); // { ok: bool, msg: string }

    const modes = {
        SMART: { title: i18n.modeSmart, help: i18n.modeSmartHelp, css: 'smart' },
        FORCE: { title: i18n.modeForce, help: i18n.modeForceHelp, css: 'force' }
    };

    const modeInfo = modes[mode] || modes.SMART;

    function handleSave() {
        setSaving(true);
        setStatus(null);
        const pathArr = paths.split('\n').map(p => p.trim()).filter(p => p.length > 0);
        gql(contextPath, { query: GQL_SAVE, variables: { path: sitePath, mode, paths: pathArr } })
            .then(d => {
                if (d.errors) {
                    setStatus({ ok: false, msg: d.errors[0].message });
                } else {
                    setStatus({ ok: true, msg: i18n.saveSuccess });
                }
            })
            .catch(e => setStatus({ ok: false, msg: e.message || i18n.saveError }))
            .finally(() => setSaving(false));
    }

    function handleCancel() {
        window.location.reload();
    }

    return (
        <div>
            <div className="page-header">
                <h2>{i18n.title}</h2>
            </div>
            <p className="text-muted">{i18n.desc}</p>

            <div className="container-fluid" style={{ maxWidth: 720 }}>
                <div className="control-group">
                    <label className="control-label" htmlFor="permalinkMode" style={{ fontWeight: 'bold' }}>
                        {i18n.modeLabel}
                    </label>
                    <div className="controls">
                        <select id="permalinkMode" className="input-xlarge" value={mode} onChange={e => setMode(e.target.value)}>
                            <option value="SMART">{i18n.modeSmart}</option>
                            <option value="FORCE">{i18n.modeForce}</option>
                        </select>
                    </div>
                </div>

                <div className={`permalink-mode-panel ${modeInfo.css}`}>
                    <h4>{modeInfo.title}</h4>
                    <p>{modeInfo.help}</p>
                </div>

                <div className="control-group" style={{ marginTop: 20 }}>
                    <label className="control-label" htmlFor="excludedPaths" style={{ fontWeight: 'bold' }}>
                        {i18n.exclLabel}
                    </label>
                    <div className="controls">
                        <textarea
                            id="excludedPaths"
                            className="input-xlarge"
                            rows={5}
                            placeholder={i18n.exclPlaceholder}
                            style={{ fontFamily: 'monospace', fontSize: 12 }}
                            value={paths}
                            onChange={e => setPaths(e.target.value)}
                        />
                        <p className="help-block">{i18n.exclHelp}</p>
                    </div>
                </div>

                <div style={{ marginTop: 20 }}>
                    <button
                        className="btn btn-primary"
                        onClick={handleSave}
                        disabled={saving}
                        aria-busy={saving}
                    >
                        {i18n.save}
                    </button>
                    {' '}
                    <button className="btn" onClick={handleCancel}>
                        {i18n.cancel}
                    </button>
                    {/* aria-live so screen readers announce save result */}
                    <span
                        role="status"
                        aria-live="polite"
                        style={{ marginLeft: 12, fontSize: '0.8125rem', color: status ? (status.ok ? '#0d6636' : '#922b21') : undefined }}
                    >
                        {status ? status.msg : ''}
                    </span>
                </div>
            </div>
        </div>
    );
}
