package run.halo.fmimage.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum ProviderType {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-image-1", true, false),
    OPENAI_COMPATIBLE("openaiCompatible", "OpenAI Compatible", "", "gpt-image-1", false, false),
    AIHUBMIX("aihubmix", "AiHubMix", "https://aihubmix.com/v1", "doubao/doubao-seedream-4-0", true, true),
    SILICONFLOW("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Kwai-Kolors/Kolors", true, false),
    DOUBAO("doubao", "豆包大模型", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seedream-4-5-250828", true, false),
    ZHIPU("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "cogview-4", true, false),
    BAIDU_QIANFAN("baiduQianfan", "百度千帆 Air Image", "https://qianfan.baidubce.com/v2/musesteamer", "musesteamer-air-image", true, false),
    DEEPSEEK("deepseek", "DeepSeek", "", "", true, false),
    OLLAMA("ollama", "Ollama", "", "", false, false);

    private final String id;
    private final String displayName;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final boolean requiresApiKey;
    private final boolean officiallySupported;

    ProviderType(String id, String displayName, String defaultBaseUrl, String defaultModel,
        boolean requiresApiKey, boolean officiallySupported) {
        this.id = id;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.requiresApiKey = requiresApiKey;
        this.officiallySupported = officiallySupported;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    public boolean officiallySupported() {
        return officiallySupported;
    }

    public static Optional<ProviderType> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(value -> value.id.equalsIgnoreCase(raw.trim()))
            .findFirst();
    }

    public static List<ProviderType> supported() {
        return Arrays.stream(values())
            .filter(ProviderType::officiallySupported)
            .toList();
    }
}
