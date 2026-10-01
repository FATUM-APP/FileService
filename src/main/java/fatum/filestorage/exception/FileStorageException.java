package fatum.filestorage.exception;

import java.util.List;

/**
 * Runtime exception raised by the storage service. It carries the error from {@link StorageError}
 * plus optional details that help the caller fix the request (for example the list of known routes).
 */
public class FileStorageException extends RuntimeException {

    private final StorageError error;
    private final List<String> details;

    public FileStorageException(StorageError error) {
        this(error, List.of());
    }

    public FileStorageException(StorageError error, List<String> details) {
        super(error.message());
        this.error = error;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public FileStorageException(StorageError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
        this.details = List.of();
    }

    public StorageError error() {
        return error;
    }

    public List<String> details() {
        return details;
    }
}
