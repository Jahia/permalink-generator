// This file defines only the consequence-side DSL expansions for this module's rules.
// The condition-side patterns used in rules.drl (e.g. 'A property X has been set on a node',
// 'A node is moved', 'A node is deleted', 'A node is a top copy', '- not in operation X')
// are provided by Jahia core's built-in DSL, loaded automatically by the Jahia rules engine.
[consequence][]Create permanent URL for node {node} and language {language}=permalinkGeneratorService.addVanity({node}, {language}, drools);
[consequence][]Recreate permanent URLs for moved node {node}=permalinkGeneratorService.onNodeMoved({node}, drools);
[consequence][]Clean permanent URLs for deleted node {node}=permalinkGeneratorService.onNodeDeleted({node}, drools);
[consequence][]Strip copied vanities for node {node}=permalinkGeneratorService.stripCopiedVanities({node}, drools);
[consequence][]Remove permalink generated mixin from node {node}=permalinkGeneratorService.removePermalinkMixin({node}, drools);
