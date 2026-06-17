package org.jahiacommunity.modules.permalinkgenerator.rules;

import org.jahia.services.content.rules.ModuleGlobalObject;
import org.jahiacommunity.modules.permalinkgenerator.services.PermalinkGeneratorService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.HashMap;
import java.util.Map;

@Component(service = ModuleGlobalObject.class, immediate = true)
public class PermalinkModuleGlobalObject extends ModuleGlobalObject {

    @Reference
    private PermalinkGeneratorService permalinkGeneratorService;

    @Activate
    public void activate() {
        Map<String, Object> globals = new HashMap<>();
        globals.put("permalinkGeneratorService", permalinkGeneratorService);
        setGlobalRulesObject(globals);
    }
}
