package fatum.filestorage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import fatum.filestorage.configuration.InternalSecretFilter;
import fatum.filestorage.configuration.RouteProperties;
import fatum.filestorage.configuration.StorageProperties;
import fatum.filestorage.dto.FileUploadResponse;
import fatum.filestorage.dto.PresignedUrlResponse;
import fatum.filestorage.dto.RouteInfoResponse;
import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import fatum.filestorage.service.FileStorageService;
import fatum.filestorage.service.StorageRouteResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final StorageRouteResolver routeResolver = mock(StorageRouteResolver.class);

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new FileController(fileStorageService, routeResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadsAFile() throws Exception {
        when(fileStorageService.upload(any(), eq("user-service:document"))).thenReturn(new FileUploadResponse(
                "id-1", "documents/2026/10/01/id-1-front.png", "documents-bucket", "user-service:document",
                "front.png", "image/png", 3L, "etag-1", Instant.parse("2026-10-01T00:00:00Z")));

        mockMvc().perform(multipart("/files")
                        .file(new MockMultipartFile("file", "front.png", "image/png", new byte[]{1, 2, 3}))
                        .param("route", "user-service:document"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.key").value("documents/2026/10/01/id-1-front.png"))
                .andExpect(jsonPath("$.bucket").value("documents-bucket"))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void uploadWithoutFilePartIsRejected() throws Exception {
        mockMvc().perform(multipart("/files").param("route", "user-service:document"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request is missing a required part or parameter"));
    }

    @Test
    void unknownRouteIsReportedAsNotFound() throws Exception {
        when(fileStorageService.upload(any(), any()))
                .thenThrow(new FileStorageException(StorageError.ROUTE_NOT_FOUND, List.of("Unknown route: x")));

        mockMvc().perform(multipart("/files")
                        .file(new MockMultipartFile("file", "front.png", "image/png", new byte[]{1}))
                        .param("route", "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Unknown storage route"))
                .andExpect(jsonPath("$.details[0]").value("Unknown route: x"));
    }

    @Test
    void aRouteWithoutBucketIsReportedAsUnavailable() throws Exception {
        when(fileStorageService.upload(any(), any()))
                .thenThrow(new FileStorageException(StorageError.ROUTE_NOT_CONFIGURED));

        mockMvc().perform(multipart("/files")
                        .file(new MockMultipartFile("file", "front.png", "image/png", new byte[]{1}))
                        .param("route", "user-service:document"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void aDisallowedContentTypeIsReportedAsUnsupportedMediaType() throws Exception {
        when(fileStorageService.upload(any(), any()))
                .thenThrow(new FileStorageException(StorageError.CONTENT_TYPE_NOT_ALLOWED));

        mockMvc().perform(multipart("/files")
                        .file(new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[]{1}))
                        .param("route", "user-service:document"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void returnsAPresignedUrl() throws Exception {
        when(fileStorageService.presignedUrl("user-service:document", "documents/id.png"))
                .thenReturn(new PresignedUrlResponse(
                        "documents/id.png", "documents-bucket", "user-service:document",
                        "https://s3/documents/id.png", Instant.parse("2026-10-01T00:15:00Z")));

        mockMvc().perform(get("/files/url")
                        .param("route", "user-service:document")
                        .param("key", "documents/id.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3/documents/id.png"))
                .andExpect(jsonPath("$.bucket").value("documents-bucket"));
    }

    @Test
    void deletesAFile() throws Exception {
        mockMvc().perform(delete("/files")
                        .param("route", "user-service:document")
                        .param("key", "documents/id.png"))
                .andExpect(status().isNoContent());

        verify(fileStorageService).delete("user-service:document", "documents/id.png");
    }

    @Test
    void deletingAnUnknownObjectOfAnotherRouteIsRejected() throws Exception {
        doThrow(new FileStorageException(StorageError.OBJECT_KEY_OUT_OF_ROUTE))
                .when(fileStorageService).delete(any(), any());

        mockMvc().perform(delete("/files")
                        .param("route", "user-service:document")
                        .param("key", "profile-images/avatar.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The object key does not belong to the requested route"));
    }

    @Test
    void listsTheConfiguredRoutes() throws Exception {
        when(routeResolver.catalog()).thenReturn(List.of(new RouteInfoResponse(
                "user-service:document", "documents-bucket", "documents",
                List.of("image/jpeg"), DataSize.ofMegabytes(10).toBytes(), 900, true)));

        mockMvc().perform(get("/files/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].route").value("user-service:document"))
                .andExpect(jsonPath("$[0].configured").value(true));
    }

    @Test
    void theInternalSecretFilterRejectsARequestWithoutTheKey() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setInternalSecret("s3cr3t");
        RouteProperties route = new RouteProperties();
        route.setBucket("documents-bucket");
        route.setPrefix("documents");
        properties.getRoutes().put("user-service:document", route);

        MockMvc secured = MockMvcBuilders
                .standaloneSetup(new FileController(fileStorageService, new StorageRouteResolver(properties)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new InternalSecretFilter(properties, Jackson2ObjectMapperBuilder.json().build()))
                .build();

        secured.perform(get("/files/routes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("A valid storage key is required"));

        secured.perform(get("/files/routes").header(InternalSecretFilter.HEADER, "s3cr3t"))
                .andExpect(status().isOk());
    }

    @Test
    void theInternalSecretFilterDoesNotApplyWhenNoSecretIsConfigured() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setInternalSecret("");
        MockMvc open = MockMvcBuilders
                .standaloneSetup(new FileController(fileStorageService, new StorageRouteResolver(properties)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new InternalSecretFilter(properties, Jackson2ObjectMapperBuilder.json().build()))
                .build();

        when(routeResolver.catalog()).thenReturn(List.of());

        open.perform(get("/files/routes")).andExpect(status().isOk());
    }
}
