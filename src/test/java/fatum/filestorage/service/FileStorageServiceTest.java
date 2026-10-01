package fatum.filestorage.service;

import fatum.filestorage.configuration.RouteProperties;
import fatum.filestorage.configuration.StorageProperties;
import fatum.filestorage.dto.FileUploadResponse;
import fatum.filestorage.dto.PresignedUrlResponse;
import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);
        StorageProperties properties = new StorageProperties();
        properties.setPresignTtl(Duration.ofMinutes(15));
        properties.getRoutes().put("user-service:document", route("documents-bucket", "documents", List.of("image/*")));
        properties.getRoutes().put("user-service:profile-image", route("profile-bucket", "profile-images", List.of("image/jpeg", "image/png")));
        service = new FileStorageService(
                s3Client,
                s3Presigner,
                new StorageRouteResolver(properties),
                new ObjectKeyFactory(),
                properties);
    }

    @Test
    void uploadsUnderTheBucketAndPrefixOfTheRoute() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        FileUploadResponse response = service.upload(image("id-front.png", "image/png"), "user-service:document");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("documents-bucket");
        assertThat(request.getValue().key()).startsWith("documents/").endsWith("-id-front.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().metadata()).containsEntry("route", "user-service:document");
        assertThat(request.getValue().metadata()).containsEntry("original-filename", "id-front.png");

        assertThat(response.id()).isNotBlank();
        assertThat(response.key()).isEqualTo(request.getValue().key());
        assertThat(response.bucket()).isEqualTo("documents-bucket");
        assertThat(response.route()).isEqualTo("user-service:document");
        assertThat(response.originalFilename()).isEqualTo("id-front.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.size()).isEqualTo(3L);
        assertThat(response.etag()).isEqualTo("etag-1");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void guessesTheContentTypeFromTheExtensionWhenItIsMissing() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.upload(new MockMultipartFile("file", "photo.png", null, new byte[]{1, 2, 3}), "user-service:document");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(empty, "user-service:document"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.EMPTY_FILE));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsAFileAboveTheRouteLimit() {
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> service.upload(big, "user-service:document"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.FILE_TOO_LARGE));
    }

    @Test
    void rejectsAContentTypeOutsideTheAllowListOfTheRoute() {
        MockMultipartFile pdf = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(pdf, "user-service:profile-image"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.CONTENT_TYPE_NOT_ALLOWED));
    }

    @Test
    void acceptsAFamilyWildcard() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        FileUploadResponse response = service.upload(image("webp.webp", "image/webp"), "user-service:document");

        assertThat(response.contentType()).isEqualTo("image/webp");
    }

    @Test
    void reportsTheStorageBackendFailure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> service.upload(image("id.png", "image/png"), "user-service:document"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.STORAGE_UNAVAILABLE));
    }

    @Test
    void rejectsAnUploadWithAnInvalidFilename() {
        MockMultipartFile invalid = new MockMultipartFile("file", "..", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.upload(invalid, "user-service:document"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.INVALID_FILENAME));
    }

    @Test
    void deletesAnObjectOfTheRoute() {
        service.delete("user-service:document", "documents/2026/10/01/id.png");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("documents-bucket");
        assertThat(request.getValue().key()).isEqualTo("documents/2026/10/01/id.png");
    }

    @Test
    void refusesToDeleteAnObjectOfAnotherRoute() {
        assertThatThrownBy(() -> service.delete("user-service:document", "profile-images/avatar.png"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.OBJECT_KEY_OUT_OF_ROUTE));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void refusesToDeleteWithoutAKey() {
        assertThatThrownBy(() -> service.delete("user-service:document", "  "))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.OBJECT_KEY_REQUIRED));
    }

    @Test
    void reportsTheStorageFailureWhenDeleting() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> service.delete("user-service:document", "documents/id.png"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.STORAGE_UNAVAILABLE));
    }

    @Test
    void presignsADownloadUrl() throws MalformedURLException {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://documents-bucket.s3.amazonaws.com/documents/id.png").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUrlResponse response = service.presignedUrl("user-service:document", "documents/id.png");

        assertThat(response.url()).isEqualTo("https://documents-bucket.s3.amazonaws.com/documents/id.png");
        assertThat(response.bucket()).isEqualTo("documents-bucket");
        assertThat(response.key()).isEqualTo("documents/id.png");
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void refusesToPresignAnObjectOfAnotherRoute() {
        assertThatThrownBy(() -> service.presignedUrl("user-service:document", "other/id.png"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.OBJECT_KEY_OUT_OF_ROUTE));
    }

    @Test
    void reportsTheStorageFailureWhenPresigning() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> service.presignedUrl("user-service:document", "documents/id.png"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.STORAGE_UNAVAILABLE));
    }

    @Test
    void exposesTheGlobalPresignLifetime() {
        assertThat(service.globalPresignTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    private MockMultipartFile image(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, new byte[]{1, 2, 3});
    }

    private RouteProperties route(String bucket, String prefix, List<String> allowedContentTypes) {
        RouteProperties route = new RouteProperties();
        route.setBucket(bucket);
        route.setPrefix(prefix);
        route.setAllowedContentTypes(allowedContentTypes);
        route.setMaxSize(DataSize.ofMegabytes(10));
        return route;
    }
}
