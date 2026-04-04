package run.halo.fmimage.provider;

import reactor.core.publisher.Mono;
import run.halo.fmimage.config.GeneralSettings;
import run.halo.fmimage.config.ResolvedProviderConfig;
import run.halo.fmimage.model.GenerateImageRequest;
import run.halo.fmimage.model.UpstreamImageResult;

public interface ImageProviderAdapter {
    boolean supports(ProviderType providerType);

    Mono<UpstreamImageResult> generate(ProviderType providerType, GenerateImageRequest request,
        ResolvedProviderConfig providerConfig, GeneralSettings generalSettings);
}
