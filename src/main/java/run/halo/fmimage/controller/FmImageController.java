package run.halo.fmimage.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.fmimage.model.FetchImageContentRequest;
import run.halo.fmimage.model.GenerateImageRequest;
import run.halo.fmimage.model.ImageGenerationResponse;
import run.halo.fmimage.model.PluginSummaryResponse;
import run.halo.fmimage.model.ProviderCatalogResponse;
import run.halo.fmimage.service.FmImageSettingsService;
import run.halo.fmimage.service.GeneratedImageContentService;
import run.halo.fmimage.service.ImageGenerationService;

@RestController
@RequestMapping("/apis/fmimage.halo.run/v1alpha1")
public class FmImageController {
    private final FmImageSettingsService settingsService;
    private final ImageGenerationService imageGenerationService;
    private final GeneratedImageContentService generatedImageContentService;

    public FmImageController(FmImageSettingsService settingsService,
        ImageGenerationService imageGenerationService,
        GeneratedImageContentService generatedImageContentService) {
        this.settingsService = settingsService;
        this.imageGenerationService = imageGenerationService;
        this.generatedImageContentService = generatedImageContentService;
    }

    @GetMapping("/providers")
    @PreAuthorize("isAuthenticated()")
    public Mono<ProviderCatalogResponse> listProviders() {
        return settingsService.getProviderCatalog();
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public Mono<PluginSummaryResponse> getSummary() {
        return settingsService.getPluginSummary();
    }

    @PostMapping("/images/generate")
    @PreAuthorize("isAuthenticated()")
    public Mono<ImageGenerationResponse> generate(@Valid @RequestBody GenerateImageRequest request) {
        return imageGenerationService.generate(request);
    }

    @PostMapping("/images/fetch")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<byte[]>> fetchImage(@Valid @RequestBody FetchImageContentRequest request) {
        return generatedImageContentService.fetch(request);
    }
}
