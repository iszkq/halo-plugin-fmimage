package run.halo.fmimage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeneralSettingsTest {
    @Test
    void shouldKeepDefaultsWhenOverrideIsNull() {
        var defaults = GeneralSettings.defaults();

        var merged = defaults.merge(null);

        assertEquals(defaults.defaultProvider(), merged.defaultProvider());
        assertEquals(defaults.defaultResponseFormat(), merged.defaultResponseFormat());
    }

    @Test
    void shouldOverrideOnlyProvidedFields() {
        var defaults = GeneralSettings.defaults();
        var override = new GeneralSettings(
            "zhipu",
            null,
            2,
            "url",
            "local-policy",
            null,
            Boolean.FALSE,
            null,
            null,
            60,
            3,
            Boolean.TRUE
        );

        var merged = defaults.merge(override);

        assertEquals("zhipu", merged.defaultProvider());
        assertEquals(defaults.defaultSize(), merged.defaultSize());
        assertEquals(2, merged.defaultCount());
        assertEquals("url", merged.defaultResponseFormat());
        assertEquals("local-policy", merged.defaultPolicyName());
        assertTrue(merged.logUpstreamErrors());
    }
}
