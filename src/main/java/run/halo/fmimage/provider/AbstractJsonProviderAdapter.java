package run.halo.fmimage.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import run.halo.fmimage.config.GeneralSettings;
import run.halo.fmimage.config.ResolvedProviderConfig;
import run.halo.fmimage.model.GenerateImageRequest;
import run.halo.fmimage.model.ImageGenerationResponse;

public abstract class AbstractJsonProviderAdapter implements ImageProviderAdapter {
    private static final Logger log = LoggerFactory.getLogger(AbstractJsonProviderAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected final WebClient.Builder webClientBuilder;

    protected AbstractJsonProviderAdapter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    protected Mono<JsonNode> postJson(URI uri, Object payload, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings) {
        return executePostJson(uri, payload, providerConfig, generalSettings, providerConfig.baseUrl())
            .onErrorResume(WebClientRequestException.class, ex -> retryWithAlternativeBaseUrl(
                uri,
                payload,
                providerConfig,
                generalSettings,
                ex))
            .timeout(Duration.ofSeconds(generalSettings.timeoutSeconds()))
            .onErrorMap(TimeoutException.class, ex -> upstreamException(
                HttpStatus.GATEWAY_TIMEOUT,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 请求超时，请检查网络、Base URL 或稍后重试",
                ex))
            .onErrorMap(WebClientRequestException.class, ex -> upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                connectionFailureMessage(providerConfig, ex),
                ex))
            .onErrorMap(DecodingException.class, ex -> upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 返回了无法解析的响应，请检查 Base URL 是否指向正确的接口",
                ex));
    }

    protected Mono<JsonNode> getJson(URI uri, ResolvedProviderConfig providerConfig, GeneralSettings generalSettings) {
        return executeGetJson(uri, providerConfig, generalSettings, providerConfig.baseUrl())
            .onErrorResume(WebClientRequestException.class, ex -> retryGetWithAlternativeBaseUrl(
                uri,
                providerConfig,
                generalSettings,
                ex))
            .timeout(Duration.ofSeconds(generalSettings.timeoutSeconds()))
            .onErrorMap(TimeoutException.class, ex -> upstreamException(
                HttpStatus.GATEWAY_TIMEOUT,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 任务状态查询超时，请稍后重试",
                ex))
            .onErrorMap(WebClientRequestException.class, ex -> upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                connectionFailureMessage(providerConfig, ex),
                ex))
            .onErrorMap(DecodingException.class, ex -> upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 返回了无法解析的任务结果，请检查上游响应内容",
                ex));
    }

    private Mono<JsonNode> executePostJson(URI uri, Object payload, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, String baseUrl) {
        var requestUri = resolveRequestUri(baseUrl, uri);
        var request = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build()
            .post()
            .uri(requestUri)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(payload);

        if (StringUtils.hasText(providerConfig.apiKey())) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + providerConfig.apiKey().trim());
        }

        return request.retrieve()
            .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                .defaultIfEmpty("Upstream service returned an error")
                .flatMap(message -> Mono.error(upstreamException(
                    HttpStatus.BAD_GATEWAY,
                    providerConfig,
                    generalSettings,
                    providerConfig.displayName() + " 调用失败: " + summarizeUpstreamMessage(message),
                    null))))
            .bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> decodeJsonBody(body, providerConfig, generalSettings));
    }

    private Mono<JsonNode> executeGetJson(URI uri, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, String baseUrl) {
        var requestUri = resolveRequestUri(baseUrl, uri);
        var request = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build()
            .get()
            .uri(requestUri);

        if (StringUtils.hasText(providerConfig.apiKey())) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + providerConfig.apiKey().trim());
        }

        return request.retrieve()
            .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                .defaultIfEmpty("Upstream service returned an error")
                .flatMap(message -> Mono.error(upstreamException(
                    HttpStatus.BAD_GATEWAY,
                    providerConfig,
                    generalSettings,
                    providerConfig.displayName() + " 调用失败: " + summarizeUpstreamMessage(message),
                    null))))
            .bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> decodeJsonBody(body, providerConfig, generalSettings));
    }

    private URI resolveRequestUri(String baseUrl, URI uri) {
        if (uri.isAbsolute()) {
            return uri;
        }
        var normalizedBaseUrl = trimTrailingSlash(baseUrl);
        var relativePath = trimLeadingSlash(uri.toString());
        return URI.create(normalizedBaseUrl + "/" + relativePath);
    }

    protected String trimTrailingSlash(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        var normalized = raw.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    protected String trimLeadingSlash(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        var normalized = raw.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    protected String composedPrompt(GenerateImageRequest request) {
        if (!StringUtils.hasText(request.negativePrompt())) {
            return request.prompt().trim();
        }
        return request.prompt().trim() + "\n\nAvoid: " + request.negativePrompt().trim();
    }

    protected String responseFormat(GenerateImageRequest request, GeneralSettings generalSettings) {
        if (StringUtils.hasText(request.responseFormat())) {
            return request.responseFormat().trim();
        }
        return generalSettings.defaultResponseFormat();
    }

    protected boolean supportsResponseFormat(String model) {
        if (!StringUtils.hasText(model)) {
            return true;
        }
        var normalized = model.trim().toLowerCase();
        return !(normalized.startsWith("gpt-image-") || normalized.contains("/gpt-image-"));
    }

    protected Integer requestCount(GenerateImageRequest request, GeneralSettings generalSettings) {
        var requested = request.count() == null ? generalSettings.defaultCount() : request.count();
        return Math.min(requested, generalSettings.maxImagesPerRequest());
    }

    protected String requestSize(GenerateImageRequest request, GeneralSettings generalSettings) {
        if (StringUtils.hasText(request.size())) {
            return request.size().trim();
        }
        return generalSettings.defaultSize();
    }

    protected URI imagesGenerationsUri() {
        return URI.create("images/generations");
    }

    protected URI chatCompletionsUri() {
        return URI.create("chat/completions");
    }

    protected URI predictionUri(String model) {
        var normalizedModel = StringUtils.hasText(model) ? model.trim() : "";
        var encodedModelPath = Arrays.stream(normalizedModel.split("/"))
            .filter(StringUtils::hasText)
            .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
        return URI.create("models/" + encodedModelPath + "/predictions");
    }

    protected URI tasksUri(String taskId) {
        return URI.create("tasks/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8));
    }

    protected List<ImageGenerationResponse.Item> parseOpenAiLikeItems(JsonNode dataNode, String fallbackMediaType) {
        return parseItemsFromArray(dataNode, fallbackMediaType);
    }

    protected List<ImageGenerationResponse.Item> parseFlexibleOutputItems(JsonNode root, String fallbackMediaType) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        if (root.isTextual() && looksLikeRemoteImage(root.asText())) {
            return List.of(new ImageGenerationResponse.Item(root.asText(), "url", fallbackMediaType, "", ""));
        }

        var items = new ArrayList<ImageGenerationResponse.Item>();
        var seen = new LinkedHashSet<String>();
        collectFlexibleItems(root, fallbackMediaType, items, seen);
        return items;
    }

    protected String extractErrorText(JsonNode root) {
        return firstText(
            root.path("error").path("message"),
            root.path("message"),
            root.path("detail"),
            root.path("error_message"),
            root.path("status_message")
        );
    }

    private void collectFlexibleItems(JsonNode node, String fallbackMediaType,
        List<ImageGenerationResponse.Item> items, Set<String> seen) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual() && looksLikeRemoteImage(node.asText())) {
            addItem(items, seen, new ImageGenerationResponse.Item(node.asText(), "url", fallbackMediaType, "", ""));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectFlexibleItems(child, fallbackMediaType, items, seen));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        addItem(items, seen, parseItemFromObject(node, fallbackMediaType));

        for (var nestedArray : List.of(
            node.path("data"),
            node.path("images"),
            node.path("output"),
            node.path("outputs"),
            node.path("items"),
            node.path("artifacts"),
            node.path("urls"),
            node.path("image_urls"),
            node.path("choices"),
            node.path("candidates"),
            node.path("parts"),
            node.path("multi_mod_content"),
            node.path("multiModalContent"),
            node.path("result").path("data"),
            node.path("result").path("images"),
            node.path("result").path("output"),
            node.path("prediction").path("data"),
            node.path("prediction").path("images"),
            node.path("prediction").path("output"),
            node.path("response").path("data"),
            node.path("response").path("images"),
            node.path("response").path("output"),
            node.path("message").path("multi_mod_content"),
            node.path("message").path("multiModalContent"),
            node.path("content").path("parts")
        )) {
            if (nestedArray != null && nestedArray.isArray()) {
                collectFlexibleItems(nestedArray, fallbackMediaType, items, seen);
            }
        }

        for (var nestedObject : List.of(
            node.path("result"),
            node.path("prediction"),
            node.path("response"),
            node.path("output"),
            node.path("image"),
            node.path("message"),
            node.path("content"),
            node.path("inline_data"),
            node.path("inlineData")
        )) {
            if (nestedObject != null && nestedObject.isObject()) {
                collectFlexibleItems(nestedObject, fallbackMediaType, items, seen);
            }
        }
    }

    private List<ImageGenerationResponse.Item> parseItemsFromArray(JsonNode arrayNode, String fallbackMediaType) {
        var items = new ArrayList<ImageGenerationResponse.Item>();
        if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return items;
        }

        arrayNode.forEach(node -> {
            if (node.isTextual() && looksLikeRemoteImage(node.asText())) {
                items.add(new ImageGenerationResponse.Item(node.asText(), "url", fallbackMediaType, "", ""));
                return;
            }
            if (!node.isObject()) {
                return;
            }

            var item = parseItemFromObject(node, fallbackMediaType);
            if (item != null) {
                items.add(item);
            }
        });
        return items;
    }

    private ImageGenerationResponse.Item parseItemFromObject(JsonNode node, String fallbackMediaType) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return null;
        }

        var mediaType = text(node, "media_type", fallbackMediaType);
        var url = firstText(
            node.path("url"),
            node.path("image_url"),
            node.path("href"),
            node.path("download_url"),
            node.path("file_url"),
            node.path("result_url")
        );

        var imageField = node.path("image");
        if (!StringUtils.hasText(url) && imageField.isTextual() && looksLikeRemoteImage(imageField.asText())) {
            url = imageField.asText();
        }

        var inlineData = firstObject(node.path("inline_data"), node.path("inlineData"));
        if (inlineData != null) {
            mediaType = firstText(inlineData.path("mime_type"), inlineData.path("mimeType"), node.path("media_type"));
            if (!StringUtils.hasText(mediaType)) {
                mediaType = fallbackMediaType;
            }
        } else {
            mediaType = StringUtils.hasText(mediaType) ? mediaType : fallbackMediaType;
        }

        var b64 = firstText(
            node.path("b64_json"),
            node.path("base64_json"),
            node.path("base64"),
            node.path("image_base64")
        );
        if (!StringUtils.hasText(b64) && inlineData != null) {
            b64 = firstText(inlineData.path("data"));
        }
        if (!StringUtils.hasText(b64)
            && firstText(node.path("mime_type"), node.path("mimeType")).length() > 0
            && node.path("data").isTextual()) {
            mediaType = firstText(node.path("mime_type"), node.path("mimeType"));
            b64 = node.path("data").asText();
        }
        if (!StringUtils.hasText(b64) && imageField.isTextual() && !looksLikeRemoteImage(imageField.asText())) {
            b64 = imageField.asText();
        }

        var previewUrl = StringUtils.hasText(url)
            ? url
            : (StringUtils.hasText(b64) ? "data:" + mediaType + ";base64," + b64 : "");
        if (!StringUtils.hasText(previewUrl)) {
            return null;
        }

        return new ImageGenerationResponse.Item(
            previewUrl,
            previewUrl.startsWith("data:") ? "b64_json" : "url",
            mediaType,
            text(node, "revised_prompt", ""),
            firstText(node.path("id"), node.path("taskId"), node.path("task_id"))
        );
    }

    private void addItem(List<ImageGenerationResponse.Item> items, Set<String> seen, ImageGenerationResponse.Item item) {
        if (item == null || !StringUtils.hasText(item.previewUrl())) {
            return;
        }
        if (seen.add(item.previewUrl())) {
            items.add(item);
        }
    }

    private boolean looksLikeRemoteImage(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        var normalized = value.trim().toLowerCase();
        return normalized.startsWith("http://")
            || normalized.startsWith("https://")
            || normalized.startsWith("data:image/");
    }

    private Mono<JsonNode> decodeJsonBody(String body, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings) {
        if (!StringUtils.hasText(body)) {
            return Mono.error(upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 返回了空响应",
                null
            ));
        }
        try {
            return Mono.just(OBJECT_MAPPER.readTree(body));
        } catch (JsonProcessingException ex) {
            return Mono.error(upstreamException(
                HttpStatus.BAD_GATEWAY,
                providerConfig,
                generalSettings,
                providerConfig.displayName() + " 返回了无法解析的 JSON: " + summarizeUpstreamMessage(body),
                ex
            ));
        }
    }

    private ResponseStatusException upstreamException(HttpStatus status, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, String message, Throwable cause) {
        if (Boolean.TRUE.equals(generalSettings.logUpstreamErrors())) {
            if (cause == null) {
                log.error("Provider [{}] request failed: {}", providerConfig.providerType().id(), message);
            } else {
                log.error("Provider [{}] request failed: {}", providerConfig.providerType().id(), message, cause);
            }
        }
        return new ResponseStatusException(status, message, cause);
    }

    private String summarizeUpstreamMessage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "上游接口返回错误";
        }
        var compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 280 ? compact.substring(0, 280) + "..." : compact;
    }

    private String connectionFailureMessage(ResolvedProviderConfig providerConfig, WebClientRequestException exception) {
        var alternativeBaseUrl = resolveAlternativeBaseUrl(providerConfig, exception);
        if (isDnsResolutionFailure(exception)) {
            if (StringUtils.hasText(alternativeBaseUrl)) {
                return providerConfig.displayName()
                    + " 无法解析主上游地址，且切换官方备用地址后仍失败。请检查 Halo 服务器的 DNS 配置、网络连通性，或直接将 Base URL 改为: "
                    + alternativeBaseUrl;
            }
            return providerConfig.displayName()
                + " 无法解析上游地址，请检查 Halo 服务器的 DNS 配置、网络连通性，或确认 Base URL 是否可用: "
                + trimTrailingSlash(providerConfig.baseUrl());
        }
        return providerConfig.displayName() + " 无法连接上游服务: " + mostSpecificMessage(exception);
    }

    private Mono<JsonNode> retryWithAlternativeBaseUrl(URI uri, Object payload, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, WebClientRequestException exception) {
        var alternativeBaseUrl = resolveAlternativeBaseUrl(providerConfig, exception);
        if (!StringUtils.hasText(alternativeBaseUrl)) {
            return Mono.error(exception);
        }
        return executePostJson(uri, payload, providerConfig, generalSettings, alternativeBaseUrl)
            .doOnSubscribe(ignored -> log.info(
                "Provider [{}] DNS resolution failed for [{}], retrying with fallback [{}]",
                providerConfig.providerType().id(),
                trimTrailingSlash(providerConfig.baseUrl()),
                alternativeBaseUrl));
    }

    private Mono<JsonNode> retryGetWithAlternativeBaseUrl(URI uri, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, WebClientRequestException exception) {
        var alternativeBaseUrl = resolveAlternativeBaseUrl(providerConfig, exception);
        if (!StringUtils.hasText(alternativeBaseUrl)) {
            return Mono.error(exception);
        }
        return executeGetJson(uri, providerConfig, generalSettings, alternativeBaseUrl)
            .doOnSubscribe(ignored -> log.info(
                "Provider [{}] DNS resolution failed for [{}], retrying GET with fallback [{}]",
                providerConfig.providerType().id(),
                trimTrailingSlash(providerConfig.baseUrl()),
                alternativeBaseUrl));
    }

    private String resolveAlternativeBaseUrl(ResolvedProviderConfig providerConfig, Throwable throwable) {
        if (providerConfig.providerType() != ProviderType.AIHUBMIX || !isDnsResolutionFailure(throwable)) {
            return "";
        }
        try {
            var configuredUri = URI.create(trimTrailingSlash(providerConfig.baseUrl()));
            var host = configuredUri.getHost();
            if (!StringUtils.hasText(host) || !"aihubmix.com".equalsIgnoreCase(host)) {
                return "";
            }
            var scheme = StringUtils.hasText(configuredUri.getScheme()) ? configuredUri.getScheme() : "https";
            var path = StringUtils.hasText(configuredUri.getPath()) && !"/".equals(configuredUri.getPath())
                ? configuredUri.getPath()
                : "/v1";
            return scheme + "://api.aihubmix.com" + path;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private boolean isDnsResolutionFailure(Throwable throwable) {
        var cause = throwable;
        while (cause != null) {
            if (cause instanceof UnknownHostException) {
                return true;
            }
            var message = cause.getMessage();
            if (StringUtils.hasText(message)) {
                var normalized = message.toLowerCase();
                if (normalized.contains("nxdomain") || normalized.contains("failed to resolve")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String mostSpecificMessage(Throwable throwable) {
        var cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return StringUtils.hasText(cause.getMessage()) ? cause.getMessage() : throwable.getMessage();
    }

    protected JsonNode firstArray(JsonNode... nodes) {
        for (var node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    protected JsonNode firstObject(JsonNode... nodes) {
        for (var node : nodes) {
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    protected String firstText(JsonNode... nodes) {
        for (var node : nodes) {
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return "";
    }

    protected String text(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }
}
