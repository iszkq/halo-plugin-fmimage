package run.halo.fmimage.model;

import java.util.List;

public record ProviderCatalogResponse(
    String defaultProvider,
    String defaultSize,
    Integer defaultCount,
    String defaultResponseFormat,
    String defaultPolicyName,
    String defaultGroupName,
    boolean allowPolicySwitch,
    boolean allowGroupSwitch,
    List<ProviderItem> items
) {
    public record ProviderItem(
        String name,
        String displayName,
        boolean enabled,
        boolean configured,
        boolean supported,
        String defaultModel,
        List<String> models,
        String note
    ) {
    }
}
