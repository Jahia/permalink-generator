<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<fmt:setBundle basename="resources.permalink-generator"/>

<template:addResources type="css" resources="admin-bootstrap.css"/>

<fmt:message key="permalinkgenerator.siteSettings.title"       var="i18n_title"/>
<fmt:message key="permalinkgenerator.siteSettings.description" var="i18n_desc"/>
<fmt:message key="permalinkgenerator.mode.label"               var="i18n_modeLabel"/>
<fmt:message key="permalinkgenerator.mode.SMART.title"         var="i18n_modeSmart"/>
<fmt:message key="permalinkgenerator.mode.SMART.help"          var="i18n_modeSmartHelp"/>
<fmt:message key="permalinkgenerator.mode.FORCE.title"         var="i18n_modeForce"/>
<fmt:message key="permalinkgenerator.mode.FORCE.help"          var="i18n_modeForceHelp"/>
<fmt:message key="label.save"                                  var="i18n_save"/>
<fmt:message key="label.cancel"                                var="i18n_cancel"/>
<fmt:message key="permalinkgenerator.excludedPaths.label"      var="i18n_exclLabel"/>
<fmt:message key="permalinkgenerator.excludedPaths.help"       var="i18n_exclHelp"/>
<fmt:message key="permalinkgenerator.audit.title"              var="i18n_auditTitle"/>
<fmt:message key="permalinkgenerator.audit.description"        var="i18n_auditDesc"/>
<fmt:message key="permalinkgenerator.audit.startPath"          var="i18n_auditStartPath"/>
<fmt:message key="permalinkgenerator.audit.scan"               var="i18n_auditScan"/>
<fmt:message key="permalinkgenerator.audit.generate"           var="i18n_auditGenerate"/>
<fmt:message key="permalinkgenerator.audit.selectAll"          var="i18n_auditSelectAll"/>
<fmt:message key="permalinkgenerator.audit.col.path"           var="i18n_auditColPath"/>
<fmt:message key="permalinkgenerator.audit.loadMore"           var="i18n_auditLoadMore"/>
<fmt:message key="permalinkgenerator.audit.pathRequired"       var="i18n_auditPathRequired"/>
<fmt:message key="permalinkgenerator.audit.scanRunning"        var="i18n_auditScanRunning"/>
<fmt:message key="permalinkgenerator.audit.error.graphql"      var="i18n_auditErrorGraphql"/>
<fmt:message key="permalinkgenerator.audit.error.network"      var="i18n_auditErrorNetwork"/>
<fmt:message key="permalinkgenerator.audit.scanned"            var="i18n_auditScanned"/>
<fmt:message key="permalinkgenerator.audit.allGood"            var="i18n_auditAllGood"/>
<fmt:message key="permalinkgenerator.audit.loadmore.status"    var="i18n_auditLoadMoreSt"/>
<fmt:message key="permalinkgenerator.audit.summary"            var="i18n_auditSummary"/>
<fmt:message key="permalinkgenerator.audit.generate.success"   var="i18n_auditGenSuccess"/>
<fmt:message key="permalinkgenerator.audit.generate.zero"      var="i18n_auditGenZero"/>
<fmt:message key="permalinkgenerator.audit.generate.error"     var="i18n_auditGenError"/>
<fmt:message key="permalinkgenerator.audit.pill.generated"     var="i18n_pillGenerated"/>
<fmt:message key="permalinkgenerator.audit.pill.existing"      var="i18n_pillExisting"/>
<fmt:message key="permalinkgenerator.audit.pill.selected"      var="i18n_pillSelected"/>
<fmt:message key="permalinkgenerator.audit.pill.missing"       var="i18n_pillMissing"/>
<fmt:message key="permalinkgenerator.audit.col.lang.title"     var="i18n_colLangTitle"/>
<fmt:message key="permalinkgenerator.regen.pill.noTitle"       var="i18n_pillNoTitle"/>
<fmt:message key="permalinkgenerator.regen.pill.hasForce"      var="i18n_pillHasForce"/>
<fmt:message key="permalinkgenerator.regen.pill.selForce"      var="i18n_pillSelForce"/>
<fmt:message key="permalinkgenerator.regen.pill.stale"         var="i18n_pillStale"/>
<fmt:message key="permalinkgenerator.regen.pill.manual"        var="i18n_pillManual"/>
<fmt:message key="permalinkgenerator.regen.title"              var="i18n_regenTitle"/>
<fmt:message key="permalinkgenerator.regen.description"        var="i18n_regenDesc"/>
<fmt:message key="permalinkgenerator.regen.scan"               var="i18n_regenScan"/>
<fmt:message key="permalinkgenerator.regen.bypassExcluded"     var="i18n_regenBypass"/>
<fmt:message key="permalinkgenerator.regen.generate"           var="i18n_regenGenerate"/>
<fmt:message key="permalinkgenerator.regen.summary"            var="i18n_regenSummary"/>
<fmt:message key="permalinkgenerator.regen.generate.success"   var="i18n_regenSuccess"/>
<fmt:message key="permalinkgenerator.regen.generate.zero"      var="i18n_regenZero"/>
<fmt:message key="permalinkgenerator.regen.generate.error"     var="i18n_regenError"/>
<fmt:message key="permalinkgenerator.regen.scanned"            var="i18n_regenScanned"/>
<fmt:message key="permalinkgenerator.regen.confirm.title"      var="i18n_confirmTitle"/>
<fmt:message key="permalinkgenerator.regen.confirm.body"       var="i18n_confirmBody"/>
<fmt:message key="permalinkgenerator.regen.confirm.proceed"    var="i18n_confirmProceed"/>
<fmt:message key="permalinkgenerator.regen.report.title"       var="i18n_reportTitle"/>
<fmt:message key="permalinkgenerator.regen.report.created"     var="i18n_reportCreated"/>
<fmt:message key="permalinkgenerator.regen.report.promoted"    var="i18n_reportPromoted"/>
<fmt:message key="permalinkgenerator.regen.report.already_correct" var="i18n_reportCorrect"/>

<c:set var="currentMode" value="${renderContext.site.hasProperty('j:permalinkGeneratorMode') ? renderContext.site.getProperty('j:permalinkGeneratorMode').string : 'SMART'}"/>

<div id="permalink-generator-root"></div>

<script>
window.__PL_CONFIG__ = {
    contextPath: '${pageContext.request.contextPath}',
    sitePath: '${fn:escapeXml(renderContext.site.path)}',
    currentMode: '${fn:escapeXml(currentMode)}',
    siteLangs: [<c:forEach items="${renderContext.site.languages}" var="_l" varStatus="_ls"><c:if test="${!_ls.first}">,</c:if>'${fn:escapeXml(_l)}'</c:forEach>],
    excludedPaths: [<c:if test="${renderContext.site.hasProperty('j:excludedPaths')}"><c:forEach items="${renderContext.site.getProperty('j:excludedPaths').values}" var="_ep" varStatus="_eps"><c:if test="${!_eps.first}">,</c:if>'${fn:escapeXml(_ep.string)}'</c:forEach></c:if>],
    i18n: {
        title:          '${fn:escapeXml(i18n_title)}',
        desc:           '${fn:escapeXml(i18n_desc)}',
        modeLabel:      '${fn:escapeXml(i18n_modeLabel)}',
        modeSmart:      '${fn:escapeXml(i18n_modeSmart)}',
        modeSmartHelp:  '${fn:escapeXml(i18n_modeSmartHelp)}',
        modeForce:      '${fn:escapeXml(i18n_modeForce)}',
        modeForceHelp:  '${fn:escapeXml(i18n_modeForceHelp)}',
        save:           '${fn:escapeXml(i18n_save)}',
        cancel:         '${fn:escapeXml(i18n_cancel)}',
        exclLabel:      '${fn:escapeXml(i18n_exclLabel)}',
        exclHelp:       '${fn:escapeXml(i18n_exclHelp)}',
        auditTitle:     '${fn:escapeXml(i18n_auditTitle)}',
        auditDesc:      '${fn:escapeXml(i18n_auditDesc)}',
        auditStartPath: '${fn:escapeXml(i18n_auditStartPath)}',
        auditScan:      '${fn:escapeXml(i18n_auditScan)}',
        auditGenerate:  '${fn:escapeXml(i18n_auditGenerate)}',
        auditSelectAll: '${fn:escapeXml(i18n_auditSelectAll)}',
        auditColPath:   '${fn:escapeXml(i18n_auditColPath)}',
        auditLoadMore:  '${fn:escapeXml(i18n_auditLoadMore)}',
        pathRequired:   '${fn:escapeXml(i18n_auditPathRequired)}',
        scanRunning:    '${fn:escapeXml(i18n_auditScanRunning)}',
        errorGraphql:   '${fn:escapeXml(i18n_auditErrorGraphql)}',
        errorNetwork:   '${fn:escapeXml(i18n_auditErrorNetwork)}',
        scanned:        '${fn:escapeXml(i18n_auditScanned)}',
        allGood:        '${fn:escapeXml(i18n_auditAllGood)}',
        loadMoreSt:     '${fn:escapeXml(i18n_auditLoadMoreSt)}',
        summary:        '${fn:escapeXml(i18n_auditSummary)}',
        genSuccess:     '${fn:escapeXml(i18n_auditGenSuccess)}',
        genZero:        '${fn:escapeXml(i18n_auditGenZero)}',
        genError:       '${fn:escapeXml(i18n_auditGenError)}',
        pillGenerated:  '${fn:escapeXml(i18n_pillGenerated)}',
        pillExisting:   '${fn:escapeXml(i18n_pillExisting)}',
        pillSelected:   '${fn:escapeXml(i18n_pillSelected)}',
        pillMissing:    '${fn:escapeXml(i18n_pillMissing)}',
        colLangTitle:   '${fn:escapeXml(i18n_colLangTitle)}',
        pillNoTitle:    '${fn:escapeXml(i18n_pillNoTitle)}',
        pillHasForce:   '${fn:escapeXml(i18n_pillHasForce)}',
        pillSelForce:   '${fn:escapeXml(i18n_pillSelForce)}',
        pillStale:      '${fn:escapeXml(i18n_pillStale)}',
        pillManual:     '${fn:escapeXml(i18n_pillManual)}',
        regenTitle:     '${fn:escapeXml(i18n_regenTitle)}',
        regenDesc:      '${fn:escapeXml(i18n_regenDesc)}',
        regenScan:      '${fn:escapeXml(i18n_regenScan)}',
        regenBypass:    '${fn:escapeXml(i18n_regenBypass)}',
        regenGenerate:  '${fn:escapeXml(i18n_regenGenerate)}',
        regenSummary:   '${fn:escapeXml(i18n_regenSummary)}',
        regenSuccess:   '${fn:escapeXml(i18n_regenSuccess)}',
        regenZero:      '${fn:escapeXml(i18n_regenZero)}',
        regenError:     '${fn:escapeXml(i18n_regenError)}',
        regenScanned:   '${fn:escapeXml(i18n_regenScanned)}',
        confirmTitle:   '${fn:escapeXml(i18n_confirmTitle)}',
        confirmBody:    '${fn:escapeXml(i18n_confirmBody)}',
        confirmProceed: '${fn:escapeXml(i18n_confirmProceed)}',
        reportTitle:    '${fn:escapeXml(i18n_reportTitle)}',
        reportCreated:  '${fn:escapeXml(i18n_reportCreated)}',
        reportPromoted: '${fn:escapeXml(i18n_reportPromoted)}',
        reportCorrect:  '${fn:escapeXml(i18n_reportCorrect)}'
    }
};
window.__PL_CONFIG__.actionUrl = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.generatePermalinks.do';
</script>

<script src="${pageContext.request.contextPath}/modules/permalink-generator/javascript/permalink-generator-admin.js"></script>
