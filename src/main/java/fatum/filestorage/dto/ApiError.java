package fatum.filestorage.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Uniform error payload, identical in shape to the one returned by the other Fatum microservices so
 * clients can parse failures the same way everywhere.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors,
        List<String> details
) {
}
