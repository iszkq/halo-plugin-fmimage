package run.halo.fmimage.config;

public record ProviderSettings(
    Boolean enabled,
    String baseUrl,
    String apiKey,
    String defaultModel,
    String modelOptions
) {
    public static ProviderSettings defaults() {
        return new ProviderSettings(Boolean.FALSE, "", "", "", "");
    }
}
