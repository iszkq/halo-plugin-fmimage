package run.halo.fmimage.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderTypeTest {
    @Test
    void shouldResolveProviderById() {
        var provider = ProviderType.fromId("openaiCompatible");

        assertTrue(provider.isPresent());
        assertEquals(ProviderType.OPENAI_COMPATIBLE, provider.get());
    }

    @Test
    void shouldExposeOnlyOfficialProvidersInSupportedList() {
        var supported = ProviderType.supported();

        assertTrue(supported.contains(ProviderType.OPENAI));
        assertTrue(supported.contains(ProviderType.AIHUBMIX));
        assertFalse(supported.contains(ProviderType.DEEPSEEK));
        assertFalse(supported.contains(ProviderType.OLLAMA));
    }
}
