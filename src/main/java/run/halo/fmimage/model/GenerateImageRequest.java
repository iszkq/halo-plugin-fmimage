package run.halo.fmimage.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record GenerateImageRequest(
    @NotBlank(message = "provider 不能为空") String provider,
    String model,
    @NotBlank(message = "prompt 不能为空") String prompt,
    String negativePrompt,
    String size,
    @Min(value = 1, message = "count 不能小于 1")
    @Max(value = 8, message = "count 不能大于 8")
    Integer count,
    String quality,
    String style,
    String responseFormat,
    Map<String, Object> extra
) {
}
