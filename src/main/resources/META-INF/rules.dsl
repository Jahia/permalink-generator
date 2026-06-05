[consequence][]Create permanent URL for node {node} and language {language}=permalinkGeneratorService.addVanity({node}, {language}, drools);
[consequence][]Recreate permanent URLs for moved node {node}=permalinkGeneratorService.onNodeMoved({node}, drools);
[consequence][]Clean permanent URLs for deleted node {node}=permalinkGeneratorService.onNodeDeleted({node}, drools);
[consequence][]Strip copied vanities for node {node}=permalinkGeneratorService.stripCopiedVanities({node}, drools);
[consequence][]Remove permalink generated mixin from node {node}=permalinkGeneratorService.removePermalinkMixin({node}, drools);
