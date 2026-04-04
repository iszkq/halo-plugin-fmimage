package run.halo.fmimage.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.fmimage.config.GeneralSettings;
import run.halo.fmimage.config.ProviderSettings;
import run.halo.fmimage.config.ResolvedProviderConfig;
import run.halo.fmimage.model.PluginSummaryResponse;
import run.halo.fmimage.model.ProviderCatalogResponse;
import run.halo.fmimage.provider.ProviderType;

@Service
public class FmImageSettingsService {
    private static final Map<ProviderType, String> GROUP_MAPPING = Map.of(
        ProviderType.OPENAI, "openai",
        ProviderType.OPENAI_COMPATIBLE, "openaiCompatible",
        ProviderType.AIHUBMIX, "aihubmix",
        ProviderType.SILICONFLOW, "siliconflow",
        ProviderType.DOUBAO, "doubao",
        ProviderType.ZHIPU, "zhipu",
        ProviderType.BAIDU_QIANFAN, "baiduQianfan"
    );

    private final ReactiveSettingFetcher settingFetcher;

    public FmImageSettingsService(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    public Mono<GeneralSettings> getGeneralSettings() {
        var defaults = GeneralSettings.defaults();
        return Mono.zip(
                settingFetcher.fetch(GeneralSettings.GROUP, GeneralSettings.class)
                    .defaultIfEmpty(defaults),
                settingFetcher.fetch(GeneralSettings.ADVANCED_GROUP, GeneralSettings.class)
                    .defaultIfEmpty(defaults)
            )
            .map(tuple -> defaults.merge(tuple.getT1()).merge(tuple.getT2()));
    }

    public Mono<ResolvedProviderConfig> getProviderConfig(ProviderType providerType) {
        var group = GROUP_MAPPING.get(providerType);
        if (group == null) {
            return Mono.just(new ResolvedProviderConfig(
                providerType,
                providerType.displayName(),
                false,
                false,
                providerType.defaultBaseUrl(),
                "",
                providerType.defaultModel(),
                List.of(),
                "当前版本未提供该厂商的官方文生图实现。"
            ));
        }

        return settingFetcher.fetch(group, ProviderSettings.class)
            .defaultIfEmpty(ProviderSettings.defaults())
            .map(settings -> resolve(providerType, settings));
    }

    public Mono<Map<ProviderType, ResolvedProviderConfig>> getResolvedProviders(boolean includeExperimental) {
        var providerTypes = new ArrayList<>(ProviderType.supported());
        if (includeExperimental) {
            providerTypes.add(ProviderType.DEEPSEEK);
            providerTypes.add(ProviderType.OLLAMA);
        }

        return Flux.fromIterable(providerTypes)
            .flatMap(providerType -> getProviderConfig(providerType).map(config -> Map.entry(providerType, config)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new);
    }

    public Mono<ProviderCatalogResponse> getProviderCatalog() {
        return getGeneralSettings().flatMap(general ->
            getResolvedProviders(Boolean.TRUE.equals(general.showExperimentalProviders()))
                .map(configMap -> new ProviderCatalogResponse(
                    general.defaultProvider(),
                    general.defaultSize(),
                    general.defaultCount(),
                    general.defaultResponseFormat(),
                    general.defaultPolicyName(),
                    general.defaultGroupName(),
                    Boolean.TRUE.equals(general.allowPolicySwitch()),
                    Boolean.TRUE.equals(general.allowGroupSwitch()),
                    configMap.values().stream()
                        .map(config -> new ProviderCatalogResponse.ProviderItem(
                            config.providerType().id(),
                            config.displayName(),
                            config.enabled(),
                            config.configured(),
                            config.providerType().officiallySupported(),
                            config.defaultModel(),
                            config.models(),
                            config.note()
                        ))
                        .toList()
                )));
    }

    public Mono<PluginSummaryResponse> getPluginSummary() {
        return getGeneralSettings().flatMap(general ->
            getResolvedProviders(Boolean.TRUE.equals(general.showExperimentalProviders()))
                .map(configMap -> new PluginSummaryResponse(
                    "halo-plugin-fmimage",
                    "1.0.24",
                    general.defaultProvider(),
                    general.defaultSize(),
                    general.defaultResponseFormat(),
                    configMap.values().stream()
                        .map(config -> new PluginSummaryResponse.ProviderStatus(
                            config.providerType().id(),
                            config.displayName(),
                            config.enabled(),
                            config.configured(),
                            config.providerType().officiallySupported(),
                            config.defaultModel(),
                            config.note()
                        ))
                        .toList()
                )));
    }

    private ResolvedProviderConfig resolve(ProviderType providerType, ProviderSettings settings) {
        var enabled = settings.enabled() == null || settings.enabled();
        var baseUrl = normalizeBaseUrl(providerType,
            StringUtils.hasText(settings.baseUrl()) ? settings.baseUrl().trim() : providerType.defaultBaseUrl());
        var apiKey = settings.apiKey() == null ? "" : settings.apiKey().trim();
        var defaultModel = StringUtils.hasText(settings.defaultModel())
            ? settings.defaultModel().trim()
            : providerType.defaultModel();
        var models = parseModels(settings.modelOptions(), defaultModel);
        var configured = StringUtils.hasText(baseUrl)
            && (!providerType.requiresApiKey() || StringUtils.hasText(apiKey));
        var note = configured
            ? ""
            : (providerType.requiresApiKey()
                ? "请在插件设置中补全 Base URL 与 API Key。"
                : "请在插件设置中补全 Base URL。");

        return new ResolvedProviderConfig(
            providerType,
            providerType.displayName(),
            enabled,
            configured,
            baseUrl,
            apiKey,
            defaultModel,
            models,
            note
        );
    }

    private List<String> parseModels(String raw, String defaultModel) {
        var models = new ArrayList<String>();
        if (StringUtils.hasText(raw)) {
            for (var line : raw.split("\\r?\\n")) {
                var model = line.trim();
                if (StringUtils.hasText(model) && !models.contains(model)) {
                    models.add(model);
                }
            }
        }
        if (StringUtils.hasText(defaultModel) && !models.contains(defaultModel)) {
            models.add(0, defaultModel);
        }
        return models;
    }

    private String normalizeBaseUrl(ProviderType providerType, String baseUrl) {
        if (providerType != ProviderType.AIHUBMIX || !StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        try {
            var uri = URI.create(baseUrl.trim());
            var host = uri.getHost();
            var path = uri.getPath();
            if (!StringUtils.hasText(host)) {
                return baseUrl.trim();
            }
            var isAiHubMixHost = "aihubmix.com".equalsIgnoreCase(host) || "api.aihubmix.com".equalsIgnoreCase(host);
            var missingVersionPath = !StringUtils.hasText(path) || "/".equals(path);
            if (!isAiHubMixHost || !missingVersionPath) {
                return baseUrl.trim();
            }
            var scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "https";
            return scheme + "://" + host + "/v1";
        } catch (IllegalArgumentException ignored) {
            return baseUrl.trim();
        }
    }
}
