package run.halo.fmimage.config;

import java.util.List;
import run.halo.fmimage.provider.ProviderType;

public record ResolvedProviderConfig(
    ProviderType providerType,
    String displayName,
    boolean enabled,
    boolean configured,
    String baseUrl,
    String apiKey,
    String defaultModel,
    List<String> models,
    String note
) {
}
