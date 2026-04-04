package run.halo.fmimage.controller;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import run.halo.fmimage.model.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = FmImageController.class)
public class FmImageExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        var status = HttpStatus.resolve(exception.getStatusCode().value());
        var resolvedStatus = status == null ? HttpStatus.BAD_GATEWAY : status;
        var detail = StringUtils.hasText(exception.getReason())
            ? exception.getReason()
            : rootMessage(exception);
        return ResponseEntity.status(resolvedStatus)
            .body(new ApiErrorResponse(
                resolvedStatus.value(),
                resolvedStatus.getReasonPhrase(),
                detail,
                detail,
                OffsetDateTime.now()
            ));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiErrorResponse> handleThrowable(Throwable throwable) {
        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        var detail = rootMessage(throwable);
        if (!StringUtils.hasText(detail)) {
            detail = "服务器内部发生错误，请稍后再试。";
        }
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                detail,
                detail,
                OffsetDateTime.now()
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
        return throwable.getMessage();
    }
}
