package fatum.filestorage.dto;

import java.time.Instant;

/** Temporary download URL for an object that belongs to a route. */
public record PresignedUrlResponse(
        String key,
        String bucket,
        String route,
        String url,
        Instant expiresAt
) {
}
