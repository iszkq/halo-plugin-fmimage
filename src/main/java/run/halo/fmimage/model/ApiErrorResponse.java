package run.halo.fmimage.model;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
    int status,
    String error,
    String message,
    String detail,
    OffsetDateTime timestamp
) {
}
