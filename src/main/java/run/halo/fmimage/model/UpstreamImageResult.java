package run.halo.fmimage.model;

import java.util.List;

public record UpstreamImageResult(
    String provider,
    String displayName,
    String model,
    String prompt,
    String size,
    List<ImageGenerationResponse.Item> items
) {
}
