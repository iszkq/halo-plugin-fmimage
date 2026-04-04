package run.halo.fmimage.service;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.fmimage.model.GenerateImageRequest;
import run.halo.fmimage.model.ImageGenerationResponse;
import run.halo.fmimage.provider.ImageProviderAdapter;
import run.halo.fmimage.provider.ProviderType;

@Service
public class ImageGenerationService {
    private final FmImageSettingsService settingsService;
    private final List<ImageProviderAdapter> providerAdapters;

    public ImageGenerationService(FmImageSettingsService settingsService, List<ImageProviderAdapter> providerAdapters) {
        this.settingsService = settingsService;
        this.providerAdapters = providerAdapters;
    }

    public Mono<ImageGenerationResponse> generate(GenerateImageRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt 不能为空"));
        }

        var providerType = ProviderType.fromId(request.provider())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 provider: " + request.provider()));

        if (!providerType.officiallySupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                providerType.displayName() + " 当前版本未提供官方文生图实现");
        }

        return Mono.zip(settingsService.getGeneralSettings(), settingsService.getProviderConfig(providerType))
            .flatMap(tuple -> {
                var generalSettings = tuple.getT1();
                var providerConfig = tuple.getT2();

                if (!providerConfig.enabled()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        providerConfig.displayName() + " 未启用"));
                }
                if (!providerConfig.configured()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        providerConfig.displayName() + " 尚未配置"));
                }

                var adapter = providerAdapters.stream()
                    .filter(candidate -> candidate.supports(providerType))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        providerConfig.displayName() + " 暂无可用适配器"));

                return adapter.generate(providerType, request, providerConfig, generalSettings)
                    .map(result -> new ImageGenerationResponse(
                        result.provider(),
                        result.displayName(),
                        result.model(),
                        result.prompt(),
                        result.size(),
                        OffsetDateTime.now(),
                        result.items()
                    ));
            })
            .onErrorMap(ResponseStatusException.class, ex -> ex)
            .onErrorMap(ex -> new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "生成失败: " + rootMessage(ex),
                ex
            ));
    }

    private String rootMessage(Throwable throwable) {
        var cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }
        if (StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage();
        }
        return "未知错误";
    }
}
