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

<fmt:message key="permalinkgenerator.mode.SMART.title"    var="titleSmart"/>
<fmt:message key="permalinkgenerator.mode.SMART.help"     var="helpSmart"/>
<fmt:message key="permalinkgenerator.mode.FORCE.title"    var="titleForce"/>
<fmt:message key="permalinkgenerator.mode.FORCE.help"     var="helpForce"/>
<fmt:message key="permalinkgenerator.mode.DISABLED.title" var="titleDisabled"/>
<fmt:message key="permalinkgenerator.mode.DISABLED.help"  var="helpDisabled"/>

<style>
    .permalink-mode-panel {
        border-left: 4px solid #3c8cba;
        background: #f5f9fd;
        border-radius: 0 4px 4px 0;
        padding: 14px 18px;
        margin-top: 12px;
    }
    .permalink-mode-panel.force  { border-color: #e67e22; background: #fdf6ee; }
    .permalink-mode-panel.disabled { border-color: #aaa; background: #f5f5f5; }
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
        titleEl.textContent = m.title;
        helpEl.textContent  = m.help;
    }
    select.addEventListener('change', updatePanel);
    updatePanel();

    var GQL = 'mutation setPermalinkMode($path:String!,$mode:String!){jcr{mutateNode(pathOrId:$path){addMixins(mixins:["jmix:permalinkGeneratorSettings"]) mutateProperty(name:"j:permalinkGeneratorMode"){setValue(type:STRING,value:$mode)}}}}';

    var status = document.getElementById('saveStatus');

    document.getElementById('btnSave').addEventListener('click', function () {
        var btn = this; btn.disabled = true; status.textContent = '';
        fetch(contextPath + '/modules/graphql', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'include',
            body: JSON.stringify({ query: GQL, variables: { path: sitePath, mode: select.value } })
        })
        .then(function (r) { return r.json(); })
        .then(function (d) {
            if (d.errors) { status.style.color = '#c0392b'; status.textContent = d.errors[0].message; }
            else          { status.style.color = '#27ae60'; status.textContent = '✓ <fmt:message key="label.save"/>'; }
        })
        .catch(function (e) { status.style.color = '#c0392b'; status.textContent = e.message || 'Error'; })
        .finally(function () { btn.disabled = false; });
    });

    document.getElementById('btnCancel').addEventListener('click', function () { window.location.reload(); });
}());
</script>
