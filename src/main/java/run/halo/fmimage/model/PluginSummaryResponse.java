package run.halo.fmimage.model;

import java.util.List;

public record PluginSummaryResponse(
    String pluginName,
    String version,
    String defaultProvider,
    String defaultSize,
    String defaultResponseFormat,
    List<ProviderStatus> providers
) {
    public record ProviderStatus(
        String name,
        String displayName,
        boolean enabled,
        boolean configured,
        boolean supported,
        String defaultModel,
        String note
    ) {
    }
}
