package fatum.filestorage.controller;

import fatum.filestorage.dto.ApiError;
import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Translates every failure into the shared {@link ApiError} payload. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleStorage(
            FileStorageException exception,
            HttpServletRequest request) {
        StorageError error = exception.error();
        return build(error.status(), error.message(), request.getRequestURI(), Map.of(), exception.details());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return build(
                StorageError.FILE_TOO_LARGE.status(),
                StorageError.FILE_TOO_LARGE.message(),
                request.getRequestURI(),
                Map.of(),
                List.of("The multipart limit configured in the service is lower than the uploaded file"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingPart(
            Exception exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "The request is missing a required part or parameter",
                request.getRequestURI(),
                Map.of(),
                List.of(exception.getMessage() == null ? "" : exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unexpected error handling {}", request.getRequestURI(), exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error",
                request.getRequestURI(),
                Map.of(),
                List.of());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> validationErrors,
            List<String> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                validationErrors,
                details);
        return ResponseEntity.status(status).body(body);
    }
}
