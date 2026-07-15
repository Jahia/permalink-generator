package org.jahiacommunity.modules.permalinkgenerator.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the action's authentication/authorization configuration set in {@code activate()}
 * (gap G3 / spec S3, F16). The existing {@code GeneratePermalinksActionTest} never invoked
 * {@code activate()}.
 */
class GeneratePermalinksActionAuthConfigTest {

    private GeneratePermalinksAction action;

    @BeforeEach
    void setUp() {
        action = new GeneratePermalinksAction();
        action.activate();
    }

    @Test
    @DisplayName("activate() requires an authenticated user")
    void requiresAuthenticatedUser() {
        assertThat(action.isRequireAuthenticatedUser()).isTrue();
    }

    @Test
    @DisplayName("activate() requires the siteAdminPermalinkGenerator permission")
    void requiresSiteAdminPermission() {
        assertThat(action.getRequiredPermission()).isEqualTo("siteAdminPermalinkGenerator");
    }

    @Test
    @DisplayName("activate() restricts the action to POST")
    void restrictsToPost() {
        assertThat(action.getRequiredMethods()).contains("POST");
    }
}
