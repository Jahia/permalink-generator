<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%--@elvariable id="site" type="org.jahia.services.content.decorator.JCRSiteNode"--%>
<fmt:setBundle basename="resources.permalink-generator"/>

<template:addResources type="javascript" resources="jquery.min.js,admin-bootstrap.js"/>
<template:addResources type="css" resources="admin-bootstrap.css"/>

<c:set var="currentMode" value="${site.hasProperty('j:permalinkGeneratorMode') ? site.getProperty('j:permalinkGeneratorMode').string : 'SMART'}"/>

<fmt:message key="permalinkgenerator.mode.SMART.title" var="titleSmart"/>
<fmt:message key="permalinkgenerator.mode.SMART.help"  var="helpSmart"/>
<fmt:message key="permalinkgenerator.mode.FORCE.title" var="titleForce"/>
<fmt:message key="permalinkgenerator.mode.FORCE.help"  var="helpForce"/>
<fmt:message key="label.save"                          var="labelSave"/>

<c:set var="excludedPathsList" value=""/>
<c:if test="${site.hasProperty('j:excludedPaths')}">
    <c:forEach items="${site.getProperty('j:excludedPaths').values}" var="epVal" varStatus="epStatus">
        <c:if test="${!epStatus.first}"><c:set var="excludedPathsList" value="${excludedPathsList}&#10;"/></c:if>
        <c:set var="excludedPathsList" value="${excludedPathsList}${epVal.string}"/>
    </c:forEach>
</c:if>

<style>
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
</style>

<div class="page-header">
    <h2><fmt:message key="permalinkgenerator.siteSettings.title"/></h2>
</div>
<p class="text-muted"><fmt:message key="permalinkgenerator.siteSettings.description"/></p>

<div class="container-fluid" style="max-width:720px;">
    <div class="control-group">
        <label class="control-label" for="permalinkMode" style="font-weight:bold;">
            <fmt:message key="permalinkgenerator.mode.label"/>
        </label>
        <div class="controls">
            <select id="permalinkMode" class="input-xlarge">
                <option value="SMART" ${currentMode == 'SMART' ? 'selected' : ''}><fmt:message key="permalinkgenerator.mode.SMART.title"/></option>
                <option value="FORCE" ${currentMode == 'FORCE' ? 'selected' : ''}><fmt:message key="permalinkgenerator.mode.FORCE.title"/></option>
            </select>
        </div>
    </div>

    <div id="modePanel" class="permalink-mode-panel">
        <h4 id="modeTitle"></h4>
        <p id="modeHelp"></p>
    </div>

    <div class="control-group" style="margin-top:20px;">
        <label class="control-label" for="excludedPaths" style="font-weight:bold;">
            <fmt:message key="permalinkgenerator.excludedPaths.label"/>
        </label>
        <div class="controls">
            <textarea id="excludedPaths" class="input-xlarge" rows="5"
                      placeholder="/sites/mysite/contents/legacy"
                      style="font-family:monospace;font-size:12px;">${fn:escapeXml(excludedPathsList)}</textarea>
            <p class="help-block"><fmt:message key="permalinkgenerator.excludedPaths.help"/></p>
        </div>
    </div>

    <div style="margin-top:20px;">
        <button class="btn btn-primary" id="btnSave"><fmt:message key="label.save"/></button>
        <button class="btn" id="btnCancel"><fmt:message key="label.cancel"/></button>
        <span id="saveStatus" style="margin-left:12px;font-size:13px;"></span>
    </div>
</div>

<script>
(function () {
    var contextPath = '${pageContext.request.contextPath}';
    var sitePath    = '${fn:escapeXml(site.path)}';

    var modes = {
        SMART: { title: '${fn:escapeXml(titleSmart)}', help: '${fn:escapeXml(helpSmart)}', css: 'smart' },
        FORCE: { title: '${fn:escapeXml(titleForce)}', help: '${fn:escapeXml(helpForce)}', css: 'force' }
    };

    var select  = document.getElementById('permalinkMode');
    var panel   = document.getElementById('modePanel');
    var titleEl = document.getElementById('modeTitle');
    var helpEl  = document.getElementById('modeHelp');

    function updatePanel() {
        var m = modes[select.value] || modes.SMART;
        panel.className = 'permalink-mode-panel ' + m.css;
        titleEl.innerHTML = m.title;
        helpEl.innerHTML  = m.help;
    }
    select.addEventListener('change', updatePanel);
    updatePanel();

    var labelSave = '${fn:escapeXml(labelSave)}';
    var GQL = 'mutation setPermalinkSettings($path:String!,$mode:String!,$paths:[String]!){jcr{mutateNode(pathOrId:$path){addMixins(mixins:["jmix:permalinkGeneratorSettings"]) modeProperty:mutateProperty(name:"j:permalinkGeneratorMode"){setValue(type:STRING,value:$mode)} excludedPaths:mutateProperty(name:"j:excludedPaths"){setValues(type:STRING,values:$paths)}}}}';

    var status = document.getElementById('saveStatus');

    document.getElementById('btnSave').addEventListener('click', function () {
        var btn = this; btn.disabled = true; status.textContent = '';
        var paths = document.getElementById('excludedPaths').value
            .split('\n').map(function (p) { return p.trim(); }).filter(function (p) { return p.length > 0; });
        fetch(contextPath + '/modules/graphql', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'include',
            body: JSON.stringify({ query: GQL, variables: { path: sitePath, mode: select.value, paths: paths } })
        })
        .then(function (r) { return r.json(); })
        .then(function (d) {
            if (d.errors) { status.style.color = '#c0392b'; status.textContent = d.errors[0].message; }
            else          { status.style.color = '#27ae60'; status.textContent = '✓ ' + labelSave; }
        })
        .catch(function (e) { status.style.color = '#c0392b'; status.textContent = e.message || 'Error'; })
        .finally(function () { btn.disabled = false; });
    });

    document.getElementById('btnCancel').addEventListener('click', function () { window.location.reload(); });
}());
</script>

<%-- ═══════════════════════════════════════════════════════════════════════
     AUDIT SECTION — Find renderableMainResource nodes without vanity URLs
     ═══════════════════════════════════════════════════════════════════════ --%>

<style>
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
.pl-pill-stale { background: #fde8c8; color: #7a4000; cursor: pointer; }
.pl-progress-wrap { background: #e9ecef; border-radius: 3px; height: 8px; margin-bottom: 10px; overflow: hidden; }
.pl-progress-bar  { height: 8px; background: #3c8cba; border-radius: 3px;
                    width: 100%; transform: scaleX(0); transform-origin: left;
                    transition: transform 0.25s ease-out; will-change: transform; }
@media (prefers-reduced-motion: reduce) { .pl-progress-bar { transition: none; } }
.pl-notitle { font-style: italic; }
.pl-audit-table tr.pl-row-ignored td { opacity: 0.45; pointer-events: none; }
.pl-regen { margin-top: 32px; border-top: 2px solid #e0e0e0; padding-top: 24px; max-width: 960px; }
.pl-regen h3 { margin-top: 0; font-size: 18px; color: #555; }
</style>

<%-- Expose site languages, excluded paths, and i18n strings to JS before the IIFE --%>
<script>
var _plSiteLangs = [];
<c:forEach items="${site.languages}" var="_l">_plSiteLangs.push('${_l}');</c:forEach>
_plSiteLangs.sort();
var _plExcludedPaths = [];
<c:if test="${site.hasProperty('j:excludedPaths')}"><c:forEach items="${site.getProperty('j:excludedPaths').values}" var="_ep">_plExcludedPaths.push('${fn:escapeXml(_ep.string)}');</c:forEach></c:if>

var _plI18n = {
    pathRequired:  '<fmt:message key="permalinkgenerator.audit.pathRequired"/>',
    scanRunning:   '<fmt:message key="permalinkgenerator.audit.scanRunning"/>',
    errorGraphql:  '<fmt:message key="permalinkgenerator.audit.error.graphql"/>',
    errorNetwork:  '<fmt:message key="permalinkgenerator.audit.error.network"/>',
    scanned:       '<fmt:message key="permalinkgenerator.audit.scanned"/>',
    allGood:       '<fmt:message key="permalinkgenerator.audit.allGood"/>',
    loadMoreSt:    '<fmt:message key="permalinkgenerator.audit.loadmore.status"/>',
    summary:       '<fmt:message key="permalinkgenerator.audit.summary"/>',
    genSuccess:    '<fmt:message key="permalinkgenerator.audit.generate.success"/>',
    genZero:       '<fmt:message key="permalinkgenerator.audit.generate.zero"/>',
    genError:      '<fmt:message key="permalinkgenerator.audit.generate.error"/>',
    pillGenerated: '<fmt:message key="permalinkgenerator.audit.pill.generated"/>',
    pillExisting:  '<fmt:message key="permalinkgenerator.audit.pill.existing"/>',
    pillSelected:  '<fmt:message key="permalinkgenerator.audit.pill.selected"/>',
    pillMissing:   '<fmt:message key="permalinkgenerator.audit.pill.missing"/>',
    colLangTitle:  '<fmt:message key="permalinkgenerator.audit.col.lang.title"/>',
    pillNoTitle:   '<fmt:message key="permalinkgenerator.regen.pill.noTitle"/>',
    pillHasForce:  '<fmt:message key="permalinkgenerator.regen.pill.hasForce"/>',
    pillSelForce:  '<fmt:message key="permalinkgenerator.regen.pill.selForce"/>',
    pillStale:     '<fmt:message key="permalinkgenerator.regen.pill.stale"/>',
    regenSummary:  '<fmt:message key="permalinkgenerator.regen.summary"/>',
    regenSuccess:  '<fmt:message key="permalinkgenerator.regen.generate.success"/>',
    regenZero:     '<fmt:message key="permalinkgenerator.regen.generate.zero"/>',
    regenError:    '<fmt:message key="permalinkgenerator.regen.generate.error"/>',
    regenScanned:  '<fmt:message key="permalinkgenerator.regen.scanned"/>',
    reportCreated: '<fmt:message key="permalinkgenerator.regen.report.created"/>',
    reportPromoted:'<fmt:message key="permalinkgenerator.regen.report.promoted"/>',
    reportCorrect: '<fmt:message key="permalinkgenerator.regen.report.already_correct"/>',
    reportTitle:   '<fmt:message key="permalinkgenerator.regen.report.title"/>'
};
</script>

<div class="pl-audit">
    <h3><fmt:message key="permalinkgenerator.audit.title"/></h3>
    <p class="text-muted"><fmt:message key="permalinkgenerator.audit.description"/></p>

    <div class="control-group">
        <label class="control-label" for="plAuditPath" style="font-weight:bold;">
            <fmt:message key="permalinkgenerator.audit.startPath"/>
        </label>
        <div class="controls" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
            <input type="text" id="plAuditPath" class="input-xlarge"
                   value="${fn:escapeXml(site.path)}"
                   style="font-family:monospace;font-size:12px;"/>
            <button class="btn" id="plBtnScan">
                <i class="icon-search"></i> <fmt:message key="permalinkgenerator.audit.scan"/>
            </button>
            <span id="plScanStatus" style="font-size:12px;color:#666;"></span>
        </div>
    </div>

    <div id="plAuditResults" style="display:none;margin-top:16px;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;flex-wrap:wrap;gap:8px;">
            <span id="plAuditSummary" style="font-size:13px;font-weight:bold;"></span>
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
                <button class="btn btn-success" id="plBtnGenerate" disabled>
                    <i class="icon-cog icon-white"></i>
                    <fmt:message key="permalinkgenerator.audit.generate"/>
                    (<span id="plSelCount">0</span>)
                </button>
                <span id="plGenStatus" style="font-size:12px;"></span>
            </div>
        </div>
        <div class="pl-progress-wrap" id="plProgressWrap" style="display:none;">
            <div class="pl-progress-bar" id="plProgressBar"></div>
        </div>
        <div style="overflow-x:auto;">
            <table class="pl-audit-table" id="plAuditTable">
                <thead>
                    <tr id="plAuditThead">
                        <th style="width:28px;">
                            <input type="checkbox" id="plSelectAll"
                                   title="<fmt:message key='permalinkgenerator.audit.selectAll'/>"/>
                        </th>
                        <th><fmt:message key="permalinkgenerator.audit.col.path"/></th>
                        <%-- Language columns injected by JS --%>
                    </tr>
                </thead>
                <tbody id="plAuditTbody"></tbody>
            </table>
        </div>
        <div id="plLoadMoreWrap" style="text-align:center;margin-top:12px;display:none;">
            <button class="btn" id="plBtnLoadMore">
                <fmt:message key="permalinkgenerator.audit.loadMore"/>
            </button>
            <span id="plLoadMoreStatus" style="font-size:12px;color:#777;margin-left:8px;"></span>
        </div>
    </div>
</div>

<script>
(function () {
    var langs         = _plSiteLangs;
    var excludedPaths = _plExcludedPaths;
    var i18n          = _plI18n;
    var contextPath   = '${pageContext.request.contextPath}';
    var sitePath      = '${fn:escapeXml(site.path)}';
    var actionUrl     = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.generatePermalinks.do';
    var BATCH         = 100;

    function isExcludedBySettings(nodePath) {
        return excludedPaths.some(function (ep) { return ep && nodePath.startsWith(ep); });
    }

    var offset       = 0;
    var totalScanned = 0;
    var missingRows  = []; // { uuid, path, displayName, missing:Set<lang>, generated:Set<lang> }
    var selections   = {}; // { uuid: Set<lang> }
    var scanning     = false;

    // DOM refs
    var elResults   = document.getElementById('plAuditResults');
    var elSummary   = document.getElementById('plAuditSummary');
    var elTbody     = document.getElementById('plAuditTbody');
    var elSelCount  = document.getElementById('plSelCount');
    var elBtnGen    = document.getElementById('plBtnGenerate');
    var elGenSt     = document.getElementById('plGenStatus');
    var elLoadWrap  = document.getElementById('plLoadMoreWrap');
    var elLoadSt    = document.getElementById('plLoadMoreStatus');
    var elScanSt    = document.getElementById('plScanStatus');
    var elProgBar   = document.getElementById('plProgressBar');
    var elProgWrap  = document.getElementById('plProgressWrap');
    var elSelectAll = document.getElementById('plSelectAll');
    var elPath      = document.getElementById('plAuditPath');

    // ── Build language column headers ──────────────────────────
    function buildLangHeaders() {
        var thead = document.getElementById('plAuditThead');
        langs.forEach(function (lang) {
            var th = document.createElement('th');
            th.className = 'pl-lang-th';
            th.title = i18n.colLangTitle.replace('{0}', lang.toUpperCase());
            th.dataset.lang = lang;
            th.innerHTML = lang.toUpperCase() + '<br/><input type="checkbox" class="pl-col-cb" data-lang="' + lang + '" style="margin:2px 0 0 0;" />';
            thead.appendChild(th);
        });
        document.querySelectorAll('.pl-col-cb').forEach(function (cb) {
            cb.addEventListener('change', function (e) {
                e.stopPropagation();
                var lang = cb.dataset.lang;
                missingRows.forEach(function (row) {
                    if (row.isHomePage || !row.missing.has(lang) || row.generated.has(lang)) return;
                    if (cb.checked) selectCell(row.uuid, lang);
                    else            deselectCell(row.uuid, lang);
                });
                refreshAllCells();
                updateUI();
            });
        });
    }

    // ── GraphQL helper ─────────────────────────────────────────
    function gql(body) {
        return fetch(contextPath + '/modules/graphql', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'include',
            body: JSON.stringify(body)
        }).then(function (r) { return r.json(); });
    }

    function hasActiveDefault(vanityUrls, lang) {
        return (vanityUrls || []).some(function (v) {
            return v.language === lang && v.active && v['default'];
        });
    }

    // ── Scan ───────────────────────────────────────────────────
    function doScan(reset) {
        if (scanning) return;
        var scanPath = elPath.value.trim();
        if (!scanPath.startsWith('/sites/')) {
            elScanSt.style.color = '#c0392b';
            elScanSt.textContent = i18n.pathRequired;
            return;
        }
        if (reset) {
            offset = 0; totalScanned = 0; missingRows = []; selections = {};
            elTbody.innerHTML = '';
            elResults.style.display = 'none';
        }

        scanning = true;
        document.getElementById('plBtnScan').disabled = true;
        elScanSt.style.color = '#666';
        elScanSt.textContent = i18n.scanRunning;

        var escapedPath = scanPath.replace(/'/g, "''");
        var GQL_QUERY = 'query($q:String!,$lim:Int!,$off:Int!){jcr{' +
            'nodesByQuery(query:$q,queryLanguage:SQL2,limit:$lim,offset:$off){' +
            'nodes{uuid path displayName isHomePage:property(name:"j:isHomePage"){value} vanityUrls{url language active default}}}}}';
        var qPage = "SELECT * FROM [jnt:page] AS n WHERE ISDESCENDANTNODE(n, '" + escapedPath + "')";
        var qMixin = "SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '" + escapedPath + "')";

        Promise.all([
            gql({ query: GQL_QUERY, variables: { q: qPage,  lim: BATCH, off: offset } }),
            gql({ query: GQL_QUERY, variables: { q: qMixin, lim: BATCH, off: offset } })
        ]).then(function (results) {
                var nodes = [];
                var seen = {};
                var errors = [];
                results.forEach(function (data) {
                    var errs = (data.errors || []).map(function(e){ return e.message; });
                    if (errs.length) { errors = errors.concat(errs); return; }
                    var batch = [];
                    try { batch = data.data.jcr.nodesByQuery.nodes || []; } catch (e) {}
                    batch.forEach(function (n) {
                        if (!seen[n.uuid]) { seen[n.uuid] = true; nodes.push(n); }
                    });
                });
                if (errors.length && nodes.length === 0) {
                    elScanSt.style.color = '#c0392b';
                    elScanSt.textContent = i18n.errorGraphql.replace('{0}', errors.join('; '));
                    return;
                }
                var hasMore = results.some(function(data) {
                    try { return (data.data.jcr.nodesByQuery.nodes || []).length === BATCH; } catch(e) { return false; }
                });

                totalScanned += nodes.length;
                offset += BATCH; // advance by BATCH; both queries use same offset

                nodes.forEach(function (n) {
                    if (isExcludedBySettings(n.path)) return;
                    var missing = new Set(langs.filter(function (l) {
                        return !hasActiveDefault(n.vanityUrls, l);
                    }));
                    if (missing.size === 0) return;
                    var nodeName = n.path.split('/').pop();
                    missingRows.push({
                        uuid: n.uuid,
                        path: n.path,
                        displayName: n.displayName || nodeName,
                        hasNoTitle: (n.displayName === nodeName),
                        isHomePage: !!(n.isHomePage && n.isHomePage.value === 'true'),
                        missing: missing,
                        generated: new Set()
                    });
                    appendRow(missingRows[missingRows.length - 1]);
                });

                elLoadWrap.style.display = hasMore ? 'block' : 'none';
                elLoadSt.textContent = hasMore
                    ? i18n.loadMoreSt.replace('{0}', offset).replace('{1}', missingRows.length)
                    : '';

                if (missingRows.length === 0 && !hasMore) {
                    elScanSt.style.color = '#27ae60';
                    elScanSt.textContent = i18n.allGood.replace('{0}', totalScanned);
                } else {
                    elScanSt.style.color = missingRows.length > 0 ? '#333' : '#27ae60';
                    elScanSt.textContent = i18n.scanned.replace('{0}', totalScanned).replace('{1}', missingRows.length);
                    elResults.style.display = 'block';
                    updateSummary();
                    updateUI();
                }
            })
            .catch(function (e) {
                elScanSt.style.color = '#c0392b';
                elScanSt.textContent = i18n.errorNetwork.replace('{0}', e.message || '?');
            })
            .finally(function () {
                scanning = false;
                document.getElementById('plBtnScan').disabled = false;
            });
    }

    // ── Table row ──────────────────────────────────────────────
    function appendRow(row) {
        var tr = document.createElement('tr');
        tr.id = 'pl-row-' + row.uuid;
        tr.className = 'pl-audit-row' + (row.isHomePage ? ' pl-row-ignored' : '');

        // Checkbox
        var tdCb = document.createElement('td');
        var cb = document.createElement('input');
        cb.type = 'checkbox'; cb.className = 'pl-row-cb'; cb.dataset.uuid = row.uuid;
        if (row.isHomePage) {
            cb.disabled = true;
            cb.title = 'Homepage — skipped';
        } else {
            cb.addEventListener('change', function () {
                if (cb.checked) {
                    row.missing.forEach(function (l) { if (!row.generated.has(l)) selectCell(row.uuid, l); });
                } else {
                    langs.forEach(function (l) { deselectCell(row.uuid, l); });
                }
                refreshRowCells(row);
                updateUI();
            });
        }
        tdCb.appendChild(cb); tr.appendChild(tdCb);

        // Path
        var tdPath = document.createElement('td');
        tdPath.style.cssText = 'font-family:monospace;font-size:11px;color:#666;max-width:340px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
        tdPath.title = row.path; tdPath.textContent = row.path;
        tr.appendChild(tdPath);

        // Language cells
        langs.forEach(function (lang) {
            var td = document.createElement('td');
            td.style.textAlign = 'center';
            td.dataset.uuid = row.uuid; td.dataset.lang = lang;
            renderCell(td, row, lang);
            tr.appendChild(td);
        });

        elTbody.appendChild(tr);
    }

    function renderCell(td, row, lang) {
        td.innerHTML = '';
        var pill = document.createElement('span');
        pill.className = 'pl-pill';
        if (row.generated.has(lang)) {
            pill.className += ' pl-pill-gen'; pill.textContent = lang;
            pill.title = i18n.pillGenerated;
        } else if (!row.missing.has(lang)) {
            pill.className += ' pl-pill-has'; pill.textContent = lang;
            pill.title = i18n.pillExisting;
        } else {
            var isSel = !!(selections[row.uuid] && selections[row.uuid].has(lang));
            pill.className += isSel ? ' pl-pill-sel' : ' pl-pill-miss';
            pill.textContent = lang;
            pill.title = isSel ? i18n.pillSelected : i18n.pillMissing;
            if (!row.isHomePage) {
                (function (r, l, p, td) {
                    p.addEventListener('click', function () {
                        if (selections[r.uuid] && selections[r.uuid].has(l)) deselectCell(r.uuid, l);
                        else selectCell(r.uuid, l);
                        renderCell(td, r, l);
                        updateUI();
                    });
                }(row, lang, pill, td));
            }
        }
        if (row.hasNoTitle) { pill.classList.add('pl-notitle'); pill.title += ' — ' + i18n.pillNoTitle; }
        td.appendChild(pill);
    }

    function refreshRowCells(row) {
        langs.forEach(function (lang) {
            var td = document.querySelector('#pl-row-' + row.uuid + ' td[data-lang="' + lang + '"]');
            if (td) renderCell(td, row, lang);
        });
        syncRowCheckbox(row);
        var allDone = langs.every(function (l) { return !row.missing.has(l) || row.generated.has(l); });
        var tr = document.getElementById('pl-row-' + row.uuid);
        if (tr) tr.classList.toggle('pl-row-done', allDone);
    }

    function refreshAllCells() {
        missingRows.forEach(function (row) { refreshRowCells(row); });
    }

    function syncRowCheckbox(row) {
        var cb = document.querySelector('#pl-row-' + row.uuid + ' .pl-row-cb');
        if (!cb) return;
        var missingActive = Array.from(row.missing).filter(function (l) { return !row.generated.has(l); });
        cb.checked = missingActive.length > 0 && missingActive.every(function (l) {
            return selections[row.uuid] && selections[row.uuid].has(l);
        });
    }

    // ── Selection helpers ──────────────────────────────────────
    function selectCell(uuid, lang) {
        if (!selections[uuid]) selections[uuid] = new Set();
        selections[uuid].add(lang);
    }
    function deselectCell(uuid, lang) {
        if (selections[uuid]) { selections[uuid].delete(lang); if (selections[uuid].size === 0) delete selections[uuid]; }
    }
    function totalSelected() {
        var n = 0; Object.keys(selections).forEach(function (uid) { n += selections[uid].size; }); return n;
    }

    // ── UI sync ────────────────────────────────────────────────
    function updateSummary() {
        elSummary.textContent = i18n.summary.replace('{0}', missingRows.length);
    }
    function updateUI() {
        var n = totalSelected();
        elSelCount.textContent = n;
        elBtnGen.disabled = (n === 0);

        // Sync select-all
        var allMissing = missingRows.reduce(function (acc, row) {
            if (row.isHomePage) return acc;
            Array.from(row.missing).forEach(function (l) { if (!row.generated.has(l)) acc.push({ uuid: row.uuid, l: l }); });
            return acc;
        }, []);
        elSelectAll.checked = allMissing.length > 0 && allMissing.every(function (item) {
            return selections[item.uuid] && selections[item.uuid].has(item.l);
        });

        // Sync column checkboxes
        document.querySelectorAll('.pl-col-cb').forEach(function (cb) {
            var lang = cb.dataset.lang;
            var colMissing = missingRows.filter(function (row) { return row.missing.has(lang) && !row.generated.has(lang); });
            cb.checked = colMissing.length > 0 && colMissing.every(function (row) {
                return selections[row.uuid] && selections[row.uuid].has(lang);
            });
        });
    }

    // ── Generate ───────────────────────────────────────────────
    function doGenerate() {
        // Group by language: { lang: [uuid, ...] }
        var byLang = {};
        Object.keys(selections).forEach(function (uuid) {
            selections[uuid].forEach(function (lang) {
                if (!byLang[lang]) byLang[lang] = [];
                byLang[lang].push(uuid);
            });
        });
        var langKeys = Object.keys(byLang);
        if (langKeys.length === 0) return;

        var total = totalSelected();
        var done  = 0;
        elBtnGen.disabled = true;
        elGenSt.style.color = '#666';
        elGenSt.textContent = '';
        elProgWrap.style.display = 'block';
        elProgBar.style.transform = 'scaleX(0)';

        // Process each language's nodes in chunks of 20
        function runLang(li) {
            if (li >= langKeys.length) {
                elProgWrap.style.display = 'none';
                if (done > 0) {
                    elGenSt.style.color = '#27ae60';
                    elGenSt.textContent = i18n.genSuccess.replace('{0}', done);
                } else if (elGenSt.style.color !== 'rgb(192, 57, 43)') {
                    elGenSt.style.color = '#e67e22';
                    elGenSt.textContent = i18n.genZero;
                }
                elBtnGen.disabled = (totalSelected() === 0);
                return;
            }
            var lang  = langKeys[li];
            var uuids = byLang[lang];
            var CHUNK = 20;
            var chunks = [];
            for (var c = 0; c < uuids.length; c += CHUNK) chunks.push(uuids.slice(c, c + CHUNK));

            function runChunk(ci) {
                if (ci >= chunks.length) { runLang(li + 1); return; }

                // Optimistic UI: show spinner
                chunks[ci].forEach(function (uuid) {
                    var td = document.querySelector('#pl-row-' + uuid + ' td[data-lang="' + lang + '"]');
                    if (td) { td.innerHTML = '<span class="pl-pill pl-pill-spin">' + lang + '</span>'; }
                });

                var params = new URLSearchParams();
                chunks[ci].forEach(function (uid) { params.append('nodeIds[]', uid); });
                params.append('languages[]', lang);

                $.ajax({
                    url: actionUrl,
                    method: 'POST',
                    contentType: 'application/x-www-form-urlencoded',
                    data: params.toString(),
                    success: function (data, textStatus, xhr) {
                        chunks[ci].forEach(function (uuid) {
                            var row = missingRows.find(function (r) { return r.uuid === uuid; });
                            if (!row) return;
                            row.generated.add(lang);
                            deselectCell(uuid, lang);
                            done++;
                            elProgBar.style.transform = 'scaleX(' + Math.min(1, done / total) + ')';
                            refreshRowCells(row);
                        });
                        updateUI();
                    },
                    error: function (xhr) {
                        elGenSt.style.color = '#c0392b';
                        elGenSt.textContent = i18n.genError.replace('{0}', xhr.status).replace('{1}', lang);
                        chunks[ci].forEach(function (uuid) {
                            var row = missingRows.find(function (r) { return r.uuid === uuid; });
                            if (row) { var td = document.querySelector('#pl-row-' + uuid + ' td[data-lang="' + lang + '"]'); if (td) renderCell(td, row, lang); }
                        });
                    },
                    complete: function () { runChunk(ci + 1); }
                });
            }
            runChunk(0);
        }
        runLang(0);
    }

    // ── Event listeners ────────────────────────────────────────
    document.getElementById('plBtnScan').addEventListener('click', function () { doScan(true); });
    document.getElementById('plBtnLoadMore').addEventListener('click', function () { doScan(false); });
    elBtnGen.addEventListener('click', doGenerate);

    elSelectAll.addEventListener('change', function () {
        if (elSelectAll.checked) {
            missingRows.forEach(function (row) {
                if (row.isHomePage) return;
                row.missing.forEach(function (l) { if (!row.generated.has(l)) selectCell(row.uuid, l); });
            });
        } else {
            selections = {};
        }
        refreshAllCells();
        updateUI();
    });

    // Inject language columns
    buildLangHeaders();
}());
</script>

<%-- ═══════════════════════════════════════════════════════════════════════
     FORCE REGENERATION SECTION
     ═══════════════════════════════════════════════════════════════════════ --%>

<div class="pl-regen">
    <h3><fmt:message key="permalinkgenerator.regen.title"/></h3>
    <p class="text-muted"><fmt:message key="permalinkgenerator.regen.description"/></p>

    <div class="control-group">
        <div class="controls" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
            <button class="btn" id="plRBtnScan">
                <i class="icon-search"></i> <fmt:message key="permalinkgenerator.regen.scan"/>
            </button>
            <label style="margin:0;font-weight:normal;font-size:13px;">
                <input type="checkbox" id="plRBypass" style="margin-right:4px;"/>
                <fmt:message key="permalinkgenerator.regen.bypassExcluded"/>
            </label>
            <span id="plRScanStatus" style="font-size:12px;color:#666;"></span>
        </div>
    </div>

    <div id="plRResults" style="display:none;margin-top:16px;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;flex-wrap:wrap;gap:8px;">
            <span id="plRSummary" style="font-size:13px;font-weight:bold;"></span>
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
                <button class="btn btn-warning" id="plRBtnGenerate" disabled>
                    <i class="icon-cog icon-white"></i>
                    <fmt:message key="permalinkgenerator.regen.generate"/>
                    (<span id="plRSelCount">0</span>)
                </button>
                <span id="plRGenStatus" style="font-size:12px;"></span>
            </div>
        </div>
        <div class="pl-progress-wrap" id="plRProgressWrap" style="display:none;">
            <div class="pl-progress-bar" id="plRProgressBar"></div>
        </div>
        <div style="overflow-x:auto;">
            <table class="pl-audit-table" id="plRTable">
                <thead>
                    <tr id="plRThead">
                        <th style="width:28px;">
                            <input type="checkbox" id="plRSelectAll"
                                   title="<fmt:message key='permalinkgenerator.audit.selectAll'/>"/>
                        </th>
                        <th><fmt:message key="permalinkgenerator.audit.col.path"/></th>
                        <%-- Language columns injected by JS --%>
                    </tr>
                </thead>
                <tbody id="plRTbody"></tbody>
            </table>
        </div>
        <div id="plRLoadMoreWrap" style="text-align:center;margin-top:12px;display:none;">
            <button class="btn" id="plRBtnLoadMore">
                <fmt:message key="permalinkgenerator.audit.loadMore"/>
            </button>
            <span id="plRLoadMoreStatus" style="font-size:12px;color:#777;margin-left:8px;"></span>
        </div>
        <div id="plRReport" style="display:none;margin-top:20px;"></div>
    </div>
</div>

<%-- Confirm modal for Force Regeneration --%>
<div id="plRConfirmModal" class="modal hide fade" tabindex="-1" role="dialog">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal">&times;</button>
        <h3><fmt:message key="permalinkgenerator.regen.confirm.title"/></h3>
    </div>
    <div class="modal-body">
        <p><fmt:message key="permalinkgenerator.regen.confirm.body"/></p>
    </div>
    <div class="modal-footer">
        <button class="btn" data-dismiss="modal"><fmt:message key="label.cancel"/></button>
        <button class="btn btn-warning" id="plRConfirmOk">
            <i class="icon-cog icon-white"></i>
            <fmt:message key="permalinkgenerator.regen.confirm.proceed"/>
        </button>
    </div>
</div>

<script>
(function () {
    var langs         = _plSiteLangs;
    var excludedPaths = _plExcludedPaths;
    var i18n          = _plI18n;
    var contextPath   = '${pageContext.request.contextPath}';
    var sitePath      = '${fn:escapeXml(site.path)}';
    var actionUrl     = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.generatePermalinks.do';
    var BATCH         = 100;

    function isExcludedBySettings(nodePath) {
        return excludedPaths.some(function (ep) { return ep && nodePath.startsWith(ep); });
    }

    var offset    = 0;
    var totalScanned = 0;
    var regenRows = [];
    var selections = {};
    var scanning  = false;

    var elResults   = document.getElementById('plRResults');
    var elSummary   = document.getElementById('plRSummary');
    var elTbody     = document.getElementById('plRTbody');
    var elSelCount  = document.getElementById('plRSelCount');
    var elBtnGen    = document.getElementById('plRBtnGenerate');
    var elGenSt     = document.getElementById('plRGenStatus');
    var elLoadWrap  = document.getElementById('plRLoadMoreWrap');
    var elLoadSt    = document.getElementById('plRLoadMoreStatus');
    var elScanSt    = document.getElementById('plRScanStatus');
    var elProgBar   = document.getElementById('plRProgressBar');
    var elProgWrap  = document.getElementById('plRProgressWrap');
    var elSelectAll = document.getElementById('plRSelectAll');
    var elBypass    = document.getElementById('plRBypass');

    function buildLangHeaders() {
        var thead = document.getElementById('plRThead');
        langs.forEach(function (lang) {
            var th = document.createElement('th');
            th.className = 'pl-lang-th';
            th.title = i18n.colLangTitle.replace('{0}', lang.toUpperCase());
            th.dataset.lang = lang;
            th.innerHTML = lang.toUpperCase() + '<br/><input type="checkbox" class="plR-col-cb" data-lang="' + lang + '" style="margin:2px 0 0 0;" />';
            thead.appendChild(th);
        });
        document.querySelectorAll('.plR-col-cb').forEach(function (cb) {
            cb.addEventListener('change', function (e) {
                e.stopPropagation();
                var lang = cb.dataset.lang;
                regenRows.forEach(function (row) {
                    if (row.isHomePage || row.generated.has(lang)) return;
                    if (cb.checked) selectCell(row.uuid, lang);
                    else            deselectCell(row.uuid, lang);
                });
                refreshAllCells();
                updateUI();
            });
        });
    }

    function gql(body) {
        return fetch(contextPath + '/modules/graphql', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'include',
            body: JSON.stringify(body)
        }).then(function (r) { return r.json(); });
    }

    function hasActiveDefault(vanityUrls, lang) {
        return (vanityUrls || []).some(function (v) {
            return v.language === lang && v.active && v['default'];
        });
    }

    function doScan(reset) {
        if (scanning) return;
        if (reset) {
            offset = 0; totalScanned = 0; regenRows = []; selections = {};
            elTbody.innerHTML = '';
            elResults.style.display = 'none';
        }

        scanning = true;
        document.getElementById('plRBtnScan').disabled = true;
        elScanSt.style.color = '#666';
        elScanSt.textContent = i18n.scanRunning;

        var escapedPath = sitePath.replace(/'/g, "''");
        var GQL_QUERY = 'query($q:String!,$lim:Int!,$off:Int!){jcr{' +
            'nodesByQuery(query:$q,queryLanguage:SQL2,limit:$lim,offset:$off){' +
            'nodes{uuid path displayName isHomePage:property(name:"j:isHomePage"){value} vanityUrls{url language active default}}}}}';
        var qPage  = "SELECT * FROM [jnt:page] AS n WHERE ISDESCENDANTNODE(n, '" + escapedPath + "')";
        var qMixin = "SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '" + escapedPath + "')";

        function finish() {
            scanning = false;
            document.getElementById('plRBtnScan').disabled = false;
        }

        function finalizeRows(nodes, previewMap) {
            var bypass = elBypass.checked;
            // previewMap === false → preview call failed; show all nodes (conservative fallback)
            var previewFailed = (previewMap === false);
            nodes.forEach(function (n) {
                if (!bypass && isExcludedBySettings(n.path)) return;
                var missingLangs = new Set(langs.filter(function (l) { return !hasActiveDefault(n.vanityUrls, l); }));
                // staleLangs: has a vanity but computed URL would differ (from preview)
                var staleLangs;
                if (previewFailed) {
                    // Preview unavailable — treat all non-missing langs as potentially stale
                    staleLangs = new Set(langs.filter(function (l) { return !missingLangs.has(l); }));
                } else if (previewMap) {
                    staleLangs = new Set(langs.filter(function (l) {
                        return !missingLangs.has(l) && previewMap[n.uuid] && previewMap[n.uuid].has(l);
                    }));
                } else {
                    staleLangs = new Set();
                }
                // Only list nodes where something would actually change
                if (missingLangs.size === 0 && staleLangs.size === 0) return;
                var nodeName = n.path.split('/').pop();
                var row = {
                    uuid: n.uuid,
                    path: n.path,
                    displayName: n.displayName || nodeName,
                    hasNoTitle: (n.displayName === nodeName),
                    isHomePage: !!(n.isHomePage && n.isHomePage.value === 'true'),
                    missingLangs: missingLangs,
                    staleLangs: staleLangs,
                    generated: new Set()
                };
                regenRows.push(row);
                appendRow(row);
            });
        }

        Promise.all([
            gql({ query: GQL_QUERY, variables: { q: qPage,  lim: BATCH, off: offset } }),
            gql({ query: GQL_QUERY, variables: { q: qMixin, lim: BATCH, off: offset } })
        ]).then(function (results) {
                var nodes = [];
                var seen = {};
                var errors = [];
                results.forEach(function (data) {
                    var errs = (data.errors || []).map(function(e){ return e.message; });
                    if (errs.length) { errors = errors.concat(errs); return; }
                    var batch = [];
                    try { batch = data.data.jcr.nodesByQuery.nodes || []; } catch (e) {}
                    batch.forEach(function (n) {
                        if (!seen[n.uuid]) { seen[n.uuid] = true; nodes.push(n); }
                    });
                });
                if (errors.length && nodes.length === 0) {
                    elScanSt.style.color = '#c0392b';
                    elScanSt.textContent = i18n.errorGraphql.replace('{0}', errors.join('; '));
                    finish();
                    return;
                }
                var hasMore = results.some(function(data) {
                    try { return (data.data.jcr.nodesByQuery.nodes || []).length === BATCH; } catch(e) { return false; }
                });

                totalScanned += nodes.length;
                offset += BATCH;

                // Collect candidates (bypass check) for preview call
                var bypass = elBypass.checked;
                var candidates = nodes.filter(function (n) { return bypass || !isExcludedBySettings(n.path); });

                function applyAndFinalize(previewMap) {
                    finalizeRows(nodes, previewMap);

                    elLoadWrap.style.display = hasMore ? 'block' : 'none';
                    elLoadSt.textContent = hasMore
                        ? i18n.loadMoreSt.replace('{0}', offset).replace('{1}', regenRows.length)
                        : '';
                    elScanSt.style.color = '#333';
                    elScanSt.textContent = i18n.regenScanned.replace('{0}', totalScanned).replace('{1}', regenRows.length);
                    elResults.style.display = 'block';
                    updateSummary();
                    updateUI();
                    finish();
                }

                if (candidates.length === 0) { applyAndFinalize({}); return; }

                // Preview: ask server which nodes would actually change
                var params = new URLSearchParams();
                candidates.forEach(function (n) { params.append('nodeIds[]', n.uuid); });
                langs.forEach(function (l) { params.append('languages[]', l); });
                params.append('preview', 'true');
                params.append('bypassExcluded', elBypass.checked ? 'true' : 'false');

                $.ajax({
                    url: actionUrl,
                    method: 'POST',
                    contentType: 'application/x-www-form-urlencoded',
                    data: params.toString(),
                    success: function (data) {
                        var previewMap = {};
                        (data.results || []).forEach(function (r) {
                            if (r.willChange) {
                                if (!previewMap[r.uuid]) previewMap[r.uuid] = new Set();
                                previewMap[r.uuid].add(r.language);
                            }
                        });
                        applyAndFinalize(previewMap);
                    },
                    error: function () {
                        // Fallback: preview failed — show all candidate nodes regardless of stale status
                        // (previewMap = false signals "show all" in finalizeRows)
                        applyAndFinalize(false);
                    }
                });
            })
            .catch(function (e) {
                elScanSt.style.color = '#c0392b';
                elScanSt.textContent = i18n.errorNetwork.replace('{0}', e.message || '?');
                finish();
            });
    }

    function appendRow(row) {
        var tr = document.createElement('tr');
        tr.id = 'plR-row-' + row.uuid;
        tr.className = 'pl-audit-row' + (row.isHomePage ? ' pl-row-ignored' : '');

        var tdCb = document.createElement('td');
        var cb = document.createElement('input');
        cb.type = 'checkbox'; cb.className = 'plR-row-cb'; cb.dataset.uuid = row.uuid;
        if (row.isHomePage) {
            cb.disabled = true;
            cb.title = 'Homepage — skipped';
        } else {
            cb.addEventListener('change', function () {
                if (cb.checked) {
                    langs.forEach(function (l) { if (!row.generated.has(l)) selectCell(row.uuid, l); });
                } else {
                    langs.forEach(function (l) { deselectCell(row.uuid, l); });
                }
                refreshRowCells(row);
                updateUI();
            });
        }
        tdCb.appendChild(cb); tr.appendChild(tdCb);

        var tdPath = document.createElement('td');
        tdPath.style.cssText = 'font-family:monospace;font-size:11px;color:#666;max-width:380px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
        tdPath.title = row.path; tdPath.textContent = row.path;
        tr.appendChild(tdPath);

        // Auto-select missing and stale langs
        if (!row.isHomePage) {
            row.missingLangs.forEach(function (l) { selectCell(row.uuid, l); });
            if (row.staleLangs) row.staleLangs.forEach(function (l) { selectCell(row.uuid, l); });
        }

        langs.forEach(function (lang) {
            var td = document.createElement('td');
            td.style.textAlign = 'center';
            td.dataset.uuid = row.uuid; td.dataset.lang = lang;
            renderCell(td, row, lang);
            tr.appendChild(td);
        });

        elTbody.appendChild(tr);
    }

    function renderCell(td, row, lang) {
        td.innerHTML = '';
        var pill = document.createElement('span');
        pill.className = 'pl-pill';

        if (row.generated.has(lang)) {
            pill.classList.add('pl-pill-gen');
            pill.textContent = lang;
            pill.title = i18n.pillGenerated;
        } else {
            var isSel = !!(selections[row.uuid] && selections[row.uuid].has(lang));
            if (isSel) {
                pill.classList.add('pl-pill-sel');
                pill.textContent = lang;
                pill.title = i18n.pillSelForce;
            } else if (row.missingLangs.has(lang)) {
                pill.classList.add('pl-pill-miss');
                pill.textContent = lang;
                pill.title = i18n.pillMissing;
            } else if (row.staleLangs && row.staleLangs.has(lang)) {
                pill.classList.add('pl-pill-stale');
                pill.textContent = lang;
                pill.title = i18n.pillStale;
            } else {
                pill.classList.add('pl-pill-has');
                pill.textContent = lang;
                pill.title = i18n.pillHasForce;
            }
            if (!row.isHomePage) {
                pill.style.cursor = 'pointer';
                (function (r, l, p, td) {
                    p.addEventListener('click', function () {
                        if (selections[r.uuid] && selections[r.uuid].has(l)) deselectCell(r.uuid, l);
                        else selectCell(r.uuid, l);
                        renderCell(td, r, l);
                        updateUI();
                    });
                }(row, lang, pill, td));
            }
        }

        if (row.hasNoTitle) { pill.classList.add('pl-notitle'); pill.title += ' — ' + i18n.pillNoTitle; }
        td.appendChild(pill);
    }

    function refreshRowCells(row) {
        langs.forEach(function (lang) {
            var td = document.querySelector('#plR-row-' + row.uuid + ' td[data-lang="' + lang + '"]');
            if (td) renderCell(td, row, lang);
        });
        syncRowCheckbox(row);
        var allDone = langs.every(function (l) { return row.generated.has(l); });
        var tr = document.getElementById('plR-row-' + row.uuid);
        if (tr) tr.classList.toggle('pl-row-done', allDone);
    }

    function refreshAllCells() {
        regenRows.forEach(function (row) { refreshRowCells(row); });
    }

    function syncRowCheckbox(row) {
        var cb = document.querySelector('#plR-row-' + row.uuid + ' .plR-row-cb');
        if (!cb) return;
        var active = langs.filter(function (l) { return !row.generated.has(l); });
        cb.checked = active.length > 0 && active.every(function (l) {
            return selections[row.uuid] && selections[row.uuid].has(l);
        });
    }

    function selectCell(uuid, lang) {
        if (!selections[uuid]) selections[uuid] = new Set();
        selections[uuid].add(lang);
    }
    function deselectCell(uuid, lang) {
        if (selections[uuid]) { selections[uuid].delete(lang); if (selections[uuid].size === 0) delete selections[uuid]; }
    }
    function totalSelected() {
        var n = 0; Object.keys(selections).forEach(function (uid) { n += selections[uid].size; }); return n;
    }

    function updateSummary() {
        elSummary.textContent = i18n.regenSummary.replace('{0}', regenRows.length);
    }

    function updateUI() {
        var n = totalSelected();
        elSelCount.textContent = n;
        elBtnGen.disabled = (n === 0);

        var allActive = [];
        regenRows.forEach(function (row) {
            if (row.isHomePage) return;
            langs.forEach(function (l) { if (!row.generated.has(l)) allActive.push({ uuid: row.uuid, l: l }); });
        });
        elSelectAll.checked = allActive.length > 0 && allActive.every(function (item) {
            return selections[item.uuid] && selections[item.uuid].has(item.l);
        });

        document.querySelectorAll('.plR-col-cb').forEach(function (cb) {
            var lang = cb.dataset.lang;
            var colActive = regenRows.filter(function (row) { return !row.generated.has(lang); });
            cb.checked = colActive.length > 0 && colActive.every(function (row) {
                return selections[row.uuid] && selections[row.uuid].has(lang);
            });
        });
    }

    var reportEntries = []; // accumulated across all chunks

    function renderReport() {
        var elReport = document.getElementById('plRReport');
        if (reportEntries.length === 0) { elReport.style.display = 'none'; return; }

        var actionLabel = { created: i18n.reportCreated, promoted: i18n.reportPromoted, already_correct: i18n.reportCorrect };
        var actionColor = { created: '#27ae60', promoted: '#3c8cba', already_correct: '#888' };

        var hasOldUrls = reportEntries.some(function (e) { return e.oldUrl; });
        var html = '<h4 style="margin:0 0 8px 0;font-size:14px;">' + i18n.reportTitle + '</h4>';
        html += '<div style="overflow-x:auto;"><table class="pl-audit-table" style="font-size:11px;">';
        html += '<thead><tr>';
        html += '<th style="width:44px;">Lang</th><th>Path</th><th>Action</th>';
        if (hasOldUrls) html += '<th>Previous vanity</th>';
        html += '<th>New vanity</th>';
        html += '</tr></thead><tbody>';
        reportEntries.forEach(function (e) {
            var label = actionLabel[e.action] || e.action;
            var color = actionColor[e.action] || '#555';
            html += '<tr>';
            html += '<td style="text-align:center;"><span class="pl-pill" style="background:#e8eef4;color:#333;">' + e.language.toUpperCase() + '</span></td>';
            html += '<td style="font-family:monospace;max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + e.path + '">' + e.path + '</td>';
            html += '<td style="color:' + color + ';white-space:nowrap;">' + label + '</td>';
            if (hasOldUrls) {
                html += '<td style="font-family:monospace;color:#999;">' + (e.oldUrl || '—') + '</td>';
            }
            html += '<td style="font-family:monospace;">' + e.url + '</td>';
            html += '</tr>';
        });
        html += '</tbody></table></div>';
        elReport.innerHTML = html;
        elReport.style.display = 'block';
    }

    function doGenerate() {
        var byLang = {};
        Object.keys(selections).forEach(function (uuid) {
            selections[uuid].forEach(function (lang) {
                if (!byLang[lang]) byLang[lang] = [];
                byLang[lang].push(uuid);
            });
        });
        var langKeys = Object.keys(byLang);
        if (langKeys.length === 0) return;

        reportEntries = [];
        var elReport = document.getElementById('plRReport');
        elReport.style.display = 'none';
        elReport.innerHTML = '';

        var total = totalSelected();
        var done  = 0;
        elBtnGen.disabled = true;
        elGenSt.style.color = '#666';
        elGenSt.textContent = '';
        elProgWrap.style.display = 'block';
        elProgBar.style.transform = 'scaleX(0)';

        function runLang(li) {
            if (li >= langKeys.length) {
                elProgWrap.style.display = 'none';
                if (done > 0) {
                    elGenSt.style.color = '#27ae60';
                    elGenSt.textContent = i18n.regenSuccess.replace('{0}', done);
                } else if (elGenSt.style.color !== 'rgb(192, 57, 43)') {
                    elGenSt.style.color = '#e67e22';
                    elGenSt.textContent = i18n.regenZero;
                }
                elBtnGen.disabled = (totalSelected() === 0);
                renderReport();
                return;
            }
            var lang  = langKeys[li];
            var uuids = byLang[lang];
            var CHUNK = 20;
            var chunks = [];
            for (var c = 0; c < uuids.length; c += CHUNK) chunks.push(uuids.slice(c, c + CHUNK));

            function runChunk(ci) {
                if (ci >= chunks.length) { runLang(li + 1); return; }

                chunks[ci].forEach(function (uuid) {
                    var td = document.querySelector('#plR-row-' + uuid + ' td[data-lang="' + lang + '"]');
                    if (td) { td.innerHTML = '<span class="pl-pill pl-pill-spin">' + lang + '</span>'; }
                });

                var params = new URLSearchParams();
                chunks[ci].forEach(function (uid) { params.append('nodeIds[]', uid); });
                params.append('languages[]', lang);
                params.append('force', 'true');

                $.ajax({
                    url: actionUrl,
                    method: 'POST',
                    contentType: 'application/x-www-form-urlencoded',
                    data: params.toString(),
                    success: function (data) {
                        // Parse JSON results from backend
                        var chunkResults = [];
                        try { chunkResults = (data.results || []); } catch(e) {}
                        // Build a map uuid → result for this chunk
                        var resultMap = {};
                        chunkResults.forEach(function (r) {
                            if (!resultMap[r.uuid]) resultMap[r.uuid] = {};
                            resultMap[r.uuid][r.language] = r;
                        });

                        chunks[ci].forEach(function (uuid) {
                            var row = regenRows.find(function (r) { return r.uuid === uuid; });
                            if (!row) return;
                            var opResult = resultMap[uuid] && resultMap[uuid][lang];
                            if (opResult) {
                                reportEntries.push({ path: opResult.path, language: lang, action: opResult.action, url: opResult.url, oldUrl: opResult.oldUrl || '' });
                            }
                            row.generated.add(lang);
                            deselectCell(uuid, lang);
                            done++;
                            elProgBar.style.transform = 'scaleX(' + Math.min(1, done / total) + ')';
                            refreshRowCells(row);
                        });
                        updateUI();
                    },
                    error: function (xhr) {
                        elGenSt.style.color = '#c0392b';
                        elGenSt.textContent = i18n.regenError.replace('{0}', xhr.status).replace('{1}', lang);
                        chunks[ci].forEach(function (uuid) {
                            var row = regenRows.find(function (r) { return r.uuid === uuid; });
                            if (row) { var td = document.querySelector('#plR-row-' + uuid + ' td[data-lang="' + lang + '"]'); if (td) renderCell(td, row, lang); }
                        });
                    },
                    complete: function () { runChunk(ci + 1); }
                });
            }
            runChunk(0);
        }
        runLang(0);
    }

    document.getElementById('plRBtnScan').addEventListener('click', function () { doScan(true); });
    document.getElementById('plRBtnLoadMore').addEventListener('click', function () { doScan(false); });
    // Generate button opens confirm modal; actual generation fires from modal confirm button
    elBtnGen.addEventListener('click', function () { $('#plRConfirmModal').modal('show'); });
    document.getElementById('plRConfirmOk').addEventListener('click', function () {
        $('#plRConfirmModal').modal('hide');
        doGenerate();
    });

    elSelectAll.addEventListener('change', function () {
        if (elSelectAll.checked) {
            regenRows.forEach(function (row) {
                if (row.isHomePage) return;
                langs.forEach(function (l) { if (!row.generated.has(l)) selectCell(row.uuid, l); });
            });
        } else {
            selections = {};
        }
        refreshAllCells();
        updateUI();
    });

    buildLangHeaders();
}());
</script>
