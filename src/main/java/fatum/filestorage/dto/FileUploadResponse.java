package fatum.filestorage.dto;

import java.time.Instant;

/**
 * Result of an upload. The caller persists {@code id} (and/or {@code key}) to relate the file to its
 * own entities; nothing else about the file has to be replicated outside the storage service.
 */
public record FileUploadResponse(
        String id,
        String key,
        String bucket,
        String route,
        String originalFilename,
        String contentType,
        long size,
        String etag,
        Instant createdAt
) {
}
