package fatum.filestorage.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import fatum.filestorage.dto.ApiError;
import fatum.filestorage.exception.StorageError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Protects the storage API with a shared secret.
 *
 * <p>The service is meant to be reachable only from the Fatum network, so a lightweight filter is
 * enough: no OAuth2 round trip per upload, and no dependency on the user pool. When the secret is
 * blank the filter stays out of the way, which keeps local development simple.</p>
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Storage-Key";

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;

    public InternalSecretFilter(StorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean healthOrDocs = path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs");
        return healthOrDocs || properties.getInternalSecret() == null || properties.getInternalSecret().isBlank();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (!properties.getInternalSecret().equals(provided)) {
            response.setStatus(StorageError.UNAUTHORIZED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiError body = new ApiError(
                    Instant.now(),
                    StorageError.UNAUTHORIZED.status().value(),
                    StorageError.UNAUTHORIZED.status().getReasonPhrase(),
                    StorageError.UNAUTHORIZED.message(),
                    request.getRequestURI(),
                    Map.of(),
                    java.util.List.of("Send the shared secret in the " + HEADER + " header"));
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
