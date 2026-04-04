package run.halo.fmimage.provider;

import java.util.LinkedHashMap;
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
public class BaiduAirImageProviderAdapter extends AbstractJsonProviderAdapter {
    public BaiduAirImageProviderAdapter(WebClient.Builder webClientBuilder) {
        super(webClientBuilder);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return providerType == ProviderType.BAIDU_QIANFAN;
    }

    @Override
    public Mono<UpstreamImageResult> generate(ProviderType providerType, GenerateImageRequest request,
        ResolvedProviderConfig providerConfig, GeneralSettings generalSettings) {
        var model = StringUtils.hasText(request.model()) ? request.model().trim() : providerConfig.defaultModel();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", model);
        payload.put("prompt", request.prompt().trim());
        payload.put("size", requestSize(request, generalSettings));
        payload.put("n", requestCount(request, generalSettings));

        if (StringUtils.hasText(request.negativePrompt())) {
            payload.put("negative_prompt", request.negativePrompt().trim());
        }
        if (request.extra() != null && !request.extra().isEmpty()) {
            payload.putAll(request.extra());
        }

        return postJson(imagesGenerationsUri(), payload, providerConfig, generalSettings)
            .map(root -> {
                var items = parseFlexibleOutputItems(root, "image/png");
                if (items.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        providerConfig.displayName() + " 未返回可用图片结果");
                }
                return new UpstreamImageResult(
                    providerConfig.providerType().id(),
                    providerConfig.displayName(),
                    model,
                    request.prompt(),
                    requestSize(request, generalSettings),
                    items
                );
            });
    }
}
