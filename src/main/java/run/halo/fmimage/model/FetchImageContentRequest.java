package run.halo.fmimage.model;

import jakarta.validation.constraints.NotBlank;

public record FetchImageContentRequest(
    @NotBlank(message = "sourceUrl 不能为空") String sourceUrl,
    String mediaType
) {
}
