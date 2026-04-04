package run.halo.fmimage.config;

import run.halo.fmimage.provider.ProviderType;

public record GeneralSettings(
    String defaultProvider,
    String defaultSize,
    Integer defaultCount,
    String defaultResponseFormat,
    String defaultPolicyName,
    String defaultGroupName,
    Boolean allowPolicySwitch,
    Boolean allowGroupSwitch,
    Boolean showExperimentalProviders,
    Integer timeoutSeconds,
    Integer maxImagesPerRequest,
    Boolean logUpstreamErrors
) {
    public static final String GROUP = "general";
    public static final String ADVANCED_GROUP = "advanced";

    public static GeneralSettings defaults() {
        return new GeneralSettings(
            ProviderType.AIHUBMIX.id(),
            "1K",
            1,
            "url",
            "",
            "",
            Boolean.TRUE,
            Boolean.TRUE,
            Boolean.FALSE,
            120,
            4,
            Boolean.FALSE
        );
    }

    public GeneralSettings merge(GeneralSettings override) {
        if (override == null) {
            return this;
        }
        return new GeneralSettings(
            override.defaultProvider == null ? defaultProvider : override.defaultProvider.trim(),
            override.defaultSize == null ? defaultSize : override.defaultSize.trim(),
            override.defaultCount == null ? defaultCount : override.defaultCount,
            override.defaultResponseFormat == null ? defaultResponseFormat : override.defaultResponseFormat.trim(),
            override.defaultPolicyName == null ? defaultPolicyName : override.defaultPolicyName.trim(),
            override.defaultGroupName == null ? defaultGroupName : override.defaultGroupName.trim(),
            override.allowPolicySwitch == null ? allowPolicySwitch : override.allowPolicySwitch,
            override.allowGroupSwitch == null ? allowGroupSwitch : override.allowGroupSwitch,
            override.showExperimentalProviders == null ? showExperimentalProviders : override.showExperimentalProviders,
            override.timeoutSeconds == null ? timeoutSeconds : override.timeoutSeconds,
            override.maxImagesPerRequest == null ? maxImagesPerRequest : override.maxImagesPerRequest,
            override.logUpstreamErrors == null ? logUpstreamErrors : override.logUpstreamErrors
        );
    }
}
