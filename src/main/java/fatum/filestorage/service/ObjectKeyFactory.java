package fatum.filestorage.service;

import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the object keys. The layout is
 * {@code <prefix>/<yyyy>/<MM>/<dd>/<uuid>-<safe-name>}, which keeps the buckets browsable by date and
 * makes every key globally unique without a database.
 */
@Component
public class ObjectKeyFactory {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MAX_FILENAME_LENGTH = 180;

    /** Generated identifier and the key it produced. */
    public record GeneratedKey(String id, String key) {
    }

    public GeneratedKey create(String prefix, String safeFilename) {
        return create(prefix, safeFilename, LocalDate.now(ZoneOffset.UTC));
    }

    public GeneratedKey create(String prefix, String safeFilename, LocalDate date) {
        String id = UUID.randomUUID().toString();
        String normalizedPrefix = prefix == null ? "" : prefix.trim().replaceAll("^/+|/+$", "");
        StringBuilder key = new StringBuilder();
        if (!normalizedPrefix.isEmpty()) {
            key.append(normalizedPrefix).append('/');
        }
        key.append(DATE_PATH.format(date)).append('/').append(id).append('-').append(safeFilename);
        return new GeneratedKey(id, key.toString());
    }

    /**
     * Reduces a client supplied name to a single, safe path segment.
     *
     * @throws FileStorageException when the name is empty, is a directory reference or only contains
     *                              characters that cannot be stored
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileStorageException(StorageError.INVALID_FILENAME, java.util.List.of("The file name is empty"));
        }
        String cleaned = StringUtils.cleanPath(originalFilename);
        String filename = StringUtils.getFilename(cleaned);
        if (filename == null || filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new FileStorageException(StorageError.INVALID_FILENAME, java.util.List.of("Invalid file name"));
        }
        String safe = filename.replaceAll("[\\p{Cntrl}]", "").replace(' ', '_');
        if (safe.isBlank()) {
            throw new FileStorageException(StorageError.INVALID_FILENAME, java.util.List.of("Invalid file name"));
        }
        if (safe.length() > MAX_FILENAME_LENGTH) {
            safe = safe.substring(safe.length() - MAX_FILENAME_LENGTH);
        }
        return safe;
    }

    /** Normalizes a content type: lower case and without parameters such as {@code ; charset=utf-8}. */
    public String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        int separator = normalized.indexOf(';');
        return separator < 0 ? normalized : normalized.substring(0, separator).trim();
    }
}
