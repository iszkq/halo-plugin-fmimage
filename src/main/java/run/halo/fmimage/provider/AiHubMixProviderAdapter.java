package run.halo.fmimage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.fmimage.config.GeneralSettings;
import run.halo.fmimage.config.ResolvedProviderConfig;
import run.halo.fmimage.model.GenerateImageRequest;
import run.halo.fmimage.model.UpstreamImageResult;

@Component
public class AiHubMixProviderAdapter extends AbstractJsonProviderAdapter {
    private static final List<String> VERIFIED_MODELS = List.of(
        "doubao/doubao-seedream-4-0"
    );

    private static final Duration TASK_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final int MAX_TASK_POLLS = 30;

    public AiHubMixProviderAdapter(WebClient.Builder webClientBuilder) {
        super(webClientBuilder);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return providerType == ProviderType.AIHUBMIX;
    }

    @Override
    public Mono<UpstreamImageResult> generate(ProviderType providerType, GenerateImageRequest request,
        ResolvedProviderConfig providerConfig, GeneralSettings generalSettings) {
        var model = StringUtils.hasText(request.model()) ? request.model().trim() : providerConfig.defaultModel();
        var normalizedModel = normalizeModelPath(model);
        validateModel(normalizedModel);

        var normalizedSize = normalizeSize(normalizedModel, requestSize(request, generalSettings));
        var input = new LinkedHashMap<String, Object>();
        input.put("prompt", composedPrompt(request));
        input.put("size", normalizedSize);
        input.put("sequential_image_generation", "disabled");
        input.put("stream", false);
        input.put("response_format", "base64_json");
        input.put("watermark", false);

        if (request.extra() != null && !request.extra().isEmpty()) {
            input.putAll(request.extra());
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("input", input);

        return postJson(predictionUri(normalizedModel), payload, providerConfig, generalSettings)
            .flatMap(root -> resolveResultRoot(root, providerConfig, generalSettings))
            .map(root -> {
                var items = parseFlexibleOutputItems(root, "image/png");
                if (items.isEmpty()) {
                    throw noImageResultException(root, providerConfig);
                }
                return new UpstreamImageResult(
                    providerConfig.providerType().id(),
                    providerConfig.displayName(),
                    normalizedModel,
                    request.prompt(),
                    normalizedSize,
                    items
                );
            });
    }

    private Mono<JsonNode> resolveResultRoot(JsonNode root, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings) {
        if (!parseFlexibleOutputItems(root, "image/png").isEmpty()) {
            return Mono.just(root);
        }

        var taskReference = extractTaskReference(root);
        if (taskReference == null) {
            return Mono.just(root);
        }

        return pollTask(taskReference, providerConfig, generalSettings, 0);
    }

    private Mono<JsonNode> pollTask(TaskReference taskReference, ResolvedProviderConfig providerConfig,
        GeneralSettings generalSettings, int attempt) {
        if (attempt >= MAX_TASK_POLLS) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                providerConfig.displayName() + " 任务处理超时，请稍后重试"
            ));
        }

        var taskUri = resolveTaskUri(taskReference, providerConfig);
        if (taskUri == null) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                providerConfig.displayName() + " 返回了任务信息，但缺少可轮询地址"
            ));
        }

        return getJson(taskUri, providerConfig, generalSettings)
            .flatMap(response -> {
                if (!parseFlexibleOutputItems(response, "image/png").isEmpty()) {
                    return Mono.just(response);
                }

                var status = normalizeStatus(extractTaskStatus(response));
                if (isFailedStatus(status)) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        providerConfig.displayName() + " 任务失败: " + taskErrorMessage(response)
                    ));
                }

                if (isSucceededStatus(status)) {
                    return Mono.just(response);
                }

                var currentTaskReference = extractTaskReference(response);
                if (!StringUtils.hasText(status) && currentTaskReference == null) {
                    return Mono.just(response);
                }

                var nextTaskReference = mergeTaskReference(taskReference, currentTaskReference);
                if (nextTaskReference == null) {
                    return Mono.just(response);
                }

                return Mono.delay(TASK_POLL_INTERVAL)
                    .then(pollTask(nextTaskReference, providerConfig, generalSettings, attempt + 1));
            });
    }

    private ResponseStatusException noImageResultException(JsonNode root, ResolvedProviderConfig providerConfig) {
        var taskError = taskErrorMessage(root);
        if (StringUtils.hasText(taskError)) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                providerConfig.displayName() + " 未返回可用图片结果: " + taskError
            );
        }

        var status = normalizeStatus(extractTaskStatus(root));
        if (StringUtils.hasText(status)) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                providerConfig.displayName() + " 未返回可用图片结果，当前状态: " + status
            );
        }

        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            providerConfig.displayName() + " 未返回可用图片结果");
    }

    private TaskReference extractTaskReference(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }

        var pollingUrl = firstText(
            root.path("polling_url"),
            root.path("pollingUrl"),
            root.path("data").path("polling_url"),
            root.path("data").path("pollingUrl"),
            root.path("result").path("polling_url"),
            root.path("prediction").path("polling_url")
        );

        var explicitTaskId = firstText(
            root.path("task_id"),
            root.path("taskId"),
            root.path("data").path("task_id"),
            root.path("data").path("taskId"),
            root.path("result").path("task_id"),
            root.path("result").path("taskId"),
            root.path("prediction").path("id")
        );

        var status = extractTaskStatus(root);
        var fallbackId = StringUtils.hasText(pollingUrl) || StringUtils.hasText(status)
            ? firstText(root.path("id"), root.path("data").path("id"), root.path("result").path("id"))
            : "";

        var taskId = StringUtils.hasText(explicitTaskId) ? explicitTaskId : fallbackId;
        if (!StringUtils.hasText(taskId) && !StringUtils.hasText(pollingUrl)) {
            return null;
        }

        return new TaskReference(taskId, pollingUrl);
    }

    private TaskReference mergeTaskReference(TaskReference previous, TaskReference current) {
        if (current == null) {
            return previous;
        }
        return new TaskReference(
            StringUtils.hasText(current.taskId()) ? current.taskId() : previous.taskId(),
            StringUtils.hasText(current.pollingUrl()) ? current.pollingUrl() : previous.pollingUrl()
        );
    }

    private String extractTaskStatus(JsonNode root) {
        return firstText(
            root.path("status"),
            root.path("state"),
            root.path("data").path("status"),
            root.path("result").path("status"),
            root.path("prediction").path("status")
        );
    }

    private String taskErrorMessage(JsonNode root) {
        var message = extractErrorText(root);
        if (StringUtils.hasText(message)) {
            return message;
        }
        return firstText(
            root.path("status_message"),
            root.path("data").path("message"),
            root.path("result").path("message"),
            root.path("prediction").path("message")
        );
    }

    private URI resolveTaskUri(TaskReference taskReference, ResolvedProviderConfig providerConfig) {
        if (StringUtils.hasText(taskReference.pollingUrl())) {
            try {
                var pollingUri = URI.create(taskReference.pollingUrl().trim());
                if (pollingUri.isAbsolute()) {
                    return pollingUri;
                }
                var taskBaseUrl = resolveTaskBaseUrl(providerConfig.baseUrl());
                return URI.create(trimTrailingSlash(taskBaseUrl) + "/" + trimLeadingSlash(pollingUri.toString()));
            } catch (IllegalArgumentException ignored) {
                // Fall back to task id resolution below.
            }
        }

        if (!StringUtils.hasText(taskReference.taskId())) {
            return null;
        }

        var baseUrl = resolveTaskBaseUrl(providerConfig.baseUrl());
        return URI.create(trimTrailingSlash(baseUrl) + "/" + trimLeadingSlash(tasksUri(taskReference.taskId()).toString()));
    }

    private String resolveTaskBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.aihubmix.com/v1";
        }
        try {
            var configuredUri = URI.create(trimTrailingSlash(baseUrl));
            var host = configuredUri.getHost();
            var scheme = StringUtils.hasText(configuredUri.getScheme()) ? configuredUri.getScheme() : "https";
            var path = StringUtils.hasText(configuredUri.getPath()) && !"/".equals(configuredUri.getPath())
                ? configuredUri.getPath()
                : "/v1";
            if ("aihubmix.com".equalsIgnoreCase(host) || "api.aihubmix.com".equalsIgnoreCase(host)) {
                return scheme + "://api.aihubmix.com" + path;
            }
            return trimTrailingSlash(baseUrl);
        } catch (IllegalArgumentException ignored) {
            return trimTrailingSlash(baseUrl);
        }
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "";
    }

    private boolean isSucceededStatus(String status) {
        return "completed".equals(status)
            || "succeeded".equals(status)
            || "success".equals(status)
            || "done".equals(status);
    }

    private boolean isFailedStatus(String status) {
        return "failed".equals(status)
            || "error".equals(status)
            || "cancelled".equals(status)
            || "canceled".equals(status);
    }

    private String normalizeModelPath(String model) {
        if (!StringUtils.hasText(model)) {
            return model;
        }
        return model.trim();
    }

    private void validateModel(String model) {
        if (VERIFIED_MODELS.contains(model)) {
            return;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "当前版本仅保留并验证 AiHubMix 模型: doubao/doubao-seedream-4-0。"
        );
    }

    private String normalizeSize(String model, String size) {
        if (!isDoubaoModel(model)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持 Doubao Seedream 4.0");
        }

        if (!StringUtils.hasText(size)) {
            return "1K";
        }
        return switch (size.trim()) {
            case "1K", "2K", "4K", "auto" -> size.trim();
            default -> "1K";
        };
    }

    private boolean isDoubaoModel(String model) {
        return startsWithPrefix(model, "doubao/");
    }

    private boolean startsWithPrefix(String model, String prefix) {
        return StringUtils.hasText(model) && model.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private record TaskReference(String taskId, String pollingUrl) {
    }
}
