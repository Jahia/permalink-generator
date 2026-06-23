<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<fmt:setBundle basename="resources.permalink-generator"/>

<template:addResources type="css" resources="admin-bootstrap.css"/>

<c:set var="currentMode" value="${renderContext.site.hasProperty('j:permalinkGeneratorMode') ? renderContext.site.getProperty('j:permalinkGeneratorMode').string : 'SMART'}"/>

<div id="permalink-generator-root"></div>

<%-- i18n values are emitted as a JSON object to avoid single-quote injection
     in JS string literals (WCAG / security defence-in-depth). --%>
<script type="application/json" id="pl-i18n">
{
    "title":           "<fmt:message key="permalinkgenerator.siteSettings.title"/>",
    "desc":            "<fmt:message key="permalinkgenerator.siteSettings.description"/>",
    "modeLabel":       "<fmt:message key="permalinkgenerator.mode.label"/>",
    "modeSmart":       "<fmt:message key="permalinkgenerator.mode.SMART.title"/>",
    "modeSmartHelp":   "<fmt:message key="permalinkgenerator.mode.SMART.help"/>",
    "modeForce":       "<fmt:message key="permalinkgenerator.mode.FORCE.title"/>",
    "modeForceHelp":   "<fmt:message key="permalinkgenerator.mode.FORCE.help"/>",
    "save":            "<fmt:message key="label.save"/>",
    "cancel":          "<fmt:message key="label.cancel"/>",
    "closeDialog":     "Close dialog",
    "exclLabel":       "<fmt:message key="permalinkgenerator.excludedPaths.label"/>",
    "exclHelp":        "<fmt:message key="permalinkgenerator.excludedPaths.help"/>",
    "auditTitle":      "<fmt:message key="permalinkgenerator.audit.title"/>",
    "auditDesc":       "<fmt:message key="permalinkgenerator.audit.description"/>",
    "auditStartPath":  "<fmt:message key="permalinkgenerator.audit.startPath"/>",
    "auditScan":       "<fmt:message key="permalinkgenerator.audit.scan"/>",
    "auditGenerate":   "<fmt:message key="permalinkgenerator.audit.generate"/>",
    "auditSelectAll":  "<fmt:message key="permalinkgenerator.audit.selectAll"/>",
    "auditColPath":    "<fmt:message key="permalinkgenerator.audit.col.path"/>",
    "auditLoadMore":   "<fmt:message key="permalinkgenerator.audit.loadMore"/>",
    "pathRequired":    "<fmt:message key="permalinkgenerator.audit.pathRequired"/>",
    "scanRunning":     "<fmt:message key="permalinkgenerator.audit.scanRunning"/>",
    "errorGraphql":    "<fmt:message key="permalinkgenerator.audit.error.graphql"/>",
    "errorNetwork":    "<fmt:message key="permalinkgenerator.audit.error.network"/>",
    "scanned":         "<fmt:message key="permalinkgenerator.audit.scanned"/>",
    "allGood":         "<fmt:message key="permalinkgenerator.audit.allGood"/>",
    "loadMoreSt":      "<fmt:message key="permalinkgenerator.audit.loadmore.status"/>",
    "summary":         "<fmt:message key="permalinkgenerator.audit.summary"/>",
    "genSuccess":      "<fmt:message key="permalinkgenerator.audit.generate.success"/>",
    "genZero":         "<fmt:message key="permalinkgenerator.audit.generate.zero"/>",
    "genError":        "<fmt:message key="permalinkgenerator.audit.generate.error"/>",
    "genAllFailed":    "<fmt:message key="permalinkgenerator.audit.generate.error.allFailed"/>",
    "pillGenerated":   "<fmt:message key="permalinkgenerator.audit.pill.generated"/>",
    "pillExisting":    "<fmt:message key="permalinkgenerator.audit.pill.existing"/>",
    "pillSelected":    "<fmt:message key="permalinkgenerator.audit.pill.selected"/>",
    "pillMissing":     "<fmt:message key="permalinkgenerator.audit.pill.missing"/>",
    "colLangTitle":    "<fmt:message key="permalinkgenerator.audit.col.lang.title"/>",
    "pillNoTitle":     "<fmt:message key="permalinkgenerator.regen.pill.noTitle"/>",
    "pillHasForce":    "<fmt:message key="permalinkgenerator.regen.pill.hasForce"/>",
    "pillSelForce":    "<fmt:message key="permalinkgenerator.regen.pill.selForce"/>",
    "pillStale":       "<fmt:message key="permalinkgenerator.regen.pill.stale"/>",
    "pillManual":      "<fmt:message key="permalinkgenerator.regen.pill.manual"/>",
    "regenTitle":      "<fmt:message key="permalinkgenerator.regen.title"/>",
    "regenDesc":       "<fmt:message key="permalinkgenerator.regen.description"/>",
    "regenScan":       "<fmt:message key="permalinkgenerator.regen.scan"/>",
    "regenBypass":     "<fmt:message key="permalinkgenerator.regen.bypassExcluded"/>",
    "regenGenerate":   "<fmt:message key="permalinkgenerator.regen.generate"/>",
    "regenSummary":    "<fmt:message key="permalinkgenerator.regen.summary"/>",
    "regenSuccess":    "<fmt:message key="permalinkgenerator.regen.generate.success"/>",
    "regenZero":       "<fmt:message key="permalinkgenerator.regen.generate.zero"/>",
    "regenError":      "<fmt:message key="permalinkgenerator.regen.generate.error"/>",
    "regenAllFailed":  "<fmt:message key="permalinkgenerator.regen.generate.error.allFailed"/>",
    "regenScanned":    "<fmt:message key="permalinkgenerator.regen.scanned"/>",
    "confirmTitle":    "<fmt:message key="permalinkgenerator.regen.confirm.title"/>",
    "confirmBody":     "<fmt:message key="permalinkgenerator.regen.confirm.body"/>",
    "confirmProceed":  "<fmt:message key="permalinkgenerator.regen.confirm.proceed"/>",
    "reportTitle":     "<fmt:message key="permalinkgenerator.regen.report.title"/>",
    "reportCreated":   "<fmt:message key="permalinkgenerator.regen.report.created"/>",
    "reportPromoted":  "<fmt:message key="permalinkgenerator.regen.report.promoted"/>",
    "reportCorrect":   "<fmt:message key="permalinkgenerator.regen.report.already_correct"/>",
    "legendHelp":      "<fmt:message key="permalinkgenerator.legend.help"/>",
    "homepageAria":    "<fmt:message key="permalinkgenerator.audit.homepage.aria"/>",
    "reportColLang":   "<fmt:message key="permalinkgenerator.regen.report.col.lang"/>",
    "reportColPath":   "<fmt:message key="permalinkgenerator.regen.report.col.path"/>",
    "reportColAction": "<fmt:message key="permalinkgenerator.regen.report.col.action"/>",
    "reportColOldUrl": "<fmt:message key="permalinkgenerator.regen.report.col.oldUrl"/>",
    "reportColNewUrl": "<fmt:message key="permalinkgenerator.regen.report.col.newUrl"/>",
    "exclPlaceholder": "<fmt:message key="permalinkgenerator.excludedPaths.placeholder"/>",
    "saveError":       "<fmt:message key="permalinkgenerator.save.error"/>",
    "generating":      "<fmt:message key="permalinkgenerator.generating"/>",
    "saveSuccess":     "<fmt:message key="permalinkgenerator.saveSuccess"/>",
    "genPartial":      "<fmt:message key="permalinkgenerator.audit.generate.partial"/>",
    "regenPartial":    "<fmt:message key="permalinkgenerator.regen.generate.partial"/>",
    "regenSelectAll":  "<fmt:message key="permalinkgenerator.regen.selectAll"/>",
    "regenColPath":    "<fmt:message key="permalinkgenerator.regen.col.path"/>",
    "regenLoadMore":   "<fmt:message key="permalinkgenerator.regen.loadMore"/>",
    "regenAllGood":    "<fmt:message key="permalinkgenerator.regen.allGood"/>"
}
</script>
<script>
window.__PL_CONFIG__ = {
    contextPath: '${fn:escapeXml(pageContext.request.contextPath)}',
    sitePath: '${fn:escapeXml(renderContext.site.path)}',
    currentMode: '${fn:escapeXml(currentMode)}',
    siteLangs: [<c:forEach items="${renderContext.site.languages}" var="_l" varStatus="_ls"><c:if test="${!_ls.first}">,</c:if>'${fn:escapeXml(_l)}'</c:forEach>],
    excludedPaths: [<c:if test="${renderContext.site.hasProperty('j:excludedPaths')}"><c:forEach items="${renderContext.site.getProperty('j:excludedPaths').values}" var="_ep" varStatus="_eps"><c:if test="${!_eps.first}">,</c:if>'${fn:escapeXml(_ep.string)}'</c:forEach></c:if>],
    i18n: JSON.parse(document.getElementById('pl-i18n').textContent)
};
window.__PL_CONFIG__.actionUrl = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.generatePermalinks.do';
</script>

<script src="${pageContext.request.contextPath}/modules/permalink-generator/javascript/permalink-generator-admin.js"></script>
