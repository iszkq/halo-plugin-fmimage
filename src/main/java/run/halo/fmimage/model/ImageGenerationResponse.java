package run.halo.fmimage.model;

import java.time.OffsetDateTime;
import java.util.List;

public record ImageGenerationResponse(
    String provider,
    String displayName,
    String model,
    String prompt,
    String size,
    OffsetDateTime createdAt,
    List<Item> items
) {
    public record Item(
        String previewUrl,
        String sourceType,
        String mediaType,
        String revisedPrompt,
        String remoteId
    ) {
    }
}
