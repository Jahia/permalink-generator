(function () {
    var contextPath = window.contextJsParameters.contextPath;

    window.jahia.i18n.loadNamespaces('permalink-generator').then(function () {
        window.jahia.uiExtender.registry.add('adminRoute', 'permalinkGeneratorSettings', {
            targets: ['administration-sites:60'],
            requiredPermission: 'siteAdminPermalinkGenerator',
            label: 'permalink-generator:permalinkgenerator.siteSettings.title',
            icon: window.jahia.moonstone.toIconComponent('Link'),
            isSelectable: true,
            iframeUrl: contextPath + '/cms/editframe/default/$lang/sites/$site-key.permalinkGeneratorSettings.html'
        });
    });
}());
