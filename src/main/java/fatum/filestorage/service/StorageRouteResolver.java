package fatum.filestorage.service;

import fatum.filestorage.configuration.RouteProperties;
import fatum.filestorage.configuration.StorageProperties;
import fatum.filestorage.dto.RouteInfoResponse;
import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a route name sent by a caller into the bucket, prefix and limits to apply.
 *
 * <p>This is the piece that makes the service reusable: the caller never knows a bucket name, it only
 * says "user-service:document" and the deployment decides where that goes.</p>
 */
@Component
public class StorageRouteResolver {

    private final StorageProperties properties;

    public StorageRouteResolver(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the requested route.
     *
     * @param routeName route such as {@code user-service:profile-image}; when blank the configured
     *                  default route is used
     * @throws FileStorageException when no route was requested, the route is unknown or it has no bucket
     */
    public ResolvedRoute resolve(String routeName) {
        String requested = routeName == null || routeName.isBlank() ? properties.getDefaultRoute() : routeName.trim();
        if (requested == null || requested.isBlank()) {
            throw new FileStorageException(StorageError.ROUTE_REQUIRED, knownRoutes());
        }
        RouteProperties route = properties.getRoutes().get(requested);
        if (route == null) {
            throw new FileStorageException(StorageError.ROUTE_NOT_FOUND, List.of("Unknown route: " + requested));
        }
        if (route.getBucket() == null || route.getBucket().isBlank()) {
            throw new FileStorageException(
                    StorageError.ROUTE_NOT_CONFIGURED,
                    List.of("Route " + requested + " has no bucket configured"));
        }
        return new ResolvedRoute(requested, route);
    }

    /** Catalogue of every configured route, useful for operations and for onboarding new teams. */
    public List<RouteInfoResponse> catalog() {
        List<RouteInfoResponse> catalog = new ArrayList<>();
        properties.getRoutes().forEach((name, route) -> catalog.add(new RouteInfoResponse(
                name,
                route.getBucket(),
                route.getPrefix(),
                route.getAllowedContentTypes() == null ? List.of() : route.getAllowedContentTypes(),
                route.getMaxSize() == null ? 0L : route.getMaxSize().toBytes(),
                (route.getPresignTtl() == null ? properties.getPresignTtl() : route.getPresignTtl()).toSeconds(),
                route.getBucket() != null && !route.getBucket().isBlank())));
        return catalog;
    }

    /** Names of the configured routes, used as the detail of a "route not found" error. */
    public List<String> knownRoutes() {
        return List.copyOf(properties.getRoutes().keySet());
    }

    /** Lifetime to use for a route, falling back to the global configuration. */
    public Duration presignTtl(ResolvedRoute route) {
        return route.presignTtl(properties.getPresignTtl());
    }
}
