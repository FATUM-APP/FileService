package fatum.filestorage.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalog of every failure the storage service can report. Keeping the HTTP status next to the
 * message means the exception handler never has to guess.
 */
public enum StorageError {

    ROUTE_REQUIRED("A storage route is required", HttpStatus.BAD_REQUEST),
    ROUTE_NOT_FOUND("Unknown storage route", HttpStatus.NOT_FOUND),
    ROUTE_NOT_CONFIGURED("The storage route has no bucket configured", HttpStatus.SERVICE_UNAVAILABLE),
    OBJECT_KEY_REQUIRED("An object key is required", HttpStatus.BAD_REQUEST),
    OBJECT_KEY_OUT_OF_ROUTE("The object key does not belong to the requested route", HttpStatus.BAD_REQUEST),
    EMPTY_FILE("A non-empty file is required", HttpStatus.BAD_REQUEST),
    INVALID_FILENAME("The file name is invalid", HttpStatus.BAD_REQUEST),
    CONTENT_TYPE_NOT_ALLOWED("The content type is not allowed for this route", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_TOO_LARGE("The file exceeds the maximum size allowed for this route", HttpStatus.PAYLOAD_TOO_LARGE),
    UNAUTHORIZED("A valid storage key is required", HttpStatus.UNAUTHORIZED),
    STORAGE_UNAVAILABLE("The storage backend is not available", HttpStatus.BAD_GATEWAY);

    private final String message;
    private final HttpStatus status;

    StorageError(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
