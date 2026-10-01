package fatum.filestorage.controller;

import fatum.filestorage.dto.FileUploadResponse;
import fatum.filestorage.dto.PresignedUrlResponse;
import fatum.filestorage.dto.RouteInfoResponse;
import fatum.filestorage.service.FileStorageService;
import fatum.filestorage.service.StorageRouteResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Storage API consumed by the other Fatum microservices.
 *
 * <p>Every call carries a route ({@code microservice:purpose}); the caller never names a bucket.</p>
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final StorageRouteResolver routeResolver;

    public FileController(FileStorageService fileStorageService, StorageRouteResolver routeResolver) {
        this.fileStorageService = fileStorageService;
        this.routeResolver = routeResolver;
    }

    /**
     * Stores a file and returns the identifier the caller has to relate to its own entities.
     *
     * @param file  multipart part named {@code file}
     * @param route destination route, for example {@code user-service:document}
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "route", required = false) String route) {
        FileUploadResponse response = fileStorageService.upload(file, route);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Temporary download URL for an object previously stored under the route. */
    @GetMapping("/url")
    public ResponseEntity<PresignedUrlResponse> presignedUrl(
            @RequestParam("route") String route,
            @RequestParam("key") String key) {
        return ResponseEntity.ok(fileStorageService.presignedUrl(route, key));
    }

    /** Removes an object. Idempotent: deleting an unknown key succeeds. */
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestParam("route") String route,
            @RequestParam("key") String key) {
        fileStorageService.delete(route, key);
        return ResponseEntity.noContent().build();
    }

    /** Routes available in this deployment, so any team can discover the mapping to buckets. */
    @GetMapping("/routes")
    public ResponseEntity<List<RouteInfoResponse>> routes() {
        return ResponseEntity.ok(routeResolver.catalog());
    }
}
