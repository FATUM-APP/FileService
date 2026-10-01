package fatum.filestorage.service;

import fatum.filestorage.configuration.RouteProperties;
import fatum.filestorage.configuration.StorageProperties;
import fatum.filestorage.dto.RouteInfoResponse;
import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageRouteResolverTest {

    @Test
    void resolvesARouteToItsBucketAndLimits() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("documents-bucket"));

        ResolvedRoute route = resolver.resolve("user-service:document");

        assertThat(route.name()).isEqualTo("user-service:document");
        assertThat(route.bucket()).isEqualTo("documents-bucket");
        assertThat(route.prefix()).isEqualTo("documents");
        assertThat(route.maxSizeBytes()).isEqualTo(DataSize.ofMegabytes(10).toBytes());
        assertThat(route.presignTtl(Duration.ofMinutes(15))).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void trimsTheRequestedRouteName() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("documents-bucket"));

        assertThat(resolver.resolve("  user-service:document  ").name()).isEqualTo("user-service:document");
    }

    @Test
    void fallsBackToTheDefaultRoute() {
        StorageProperties properties = properties("documents-bucket");
        properties.setDefaultRoute("user-service:document");
        StorageRouteResolver resolver = new StorageRouteResolver(properties);

        assertThat(resolver.resolve(null).name()).isEqualTo("user-service:document");
        assertThat(resolver.resolve("  ").name()).isEqualTo("user-service:document");
    }

    @Test
    void failsWhenNoRouteIsRequestedAndThereIsNoDefault() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("documents-bucket"));

        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> {
                    FileStorageException failure = (FileStorageException) exception;
                    assertThat(failure.error()).isEqualTo(StorageError.ROUTE_REQUIRED);
                    assertThat(failure.details()).containsExactly("user-service:document");
                });
    }

    @Test
    void failsWhenTheRouteIsUnknown() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("documents-bucket"));

        assertThatThrownBy(() -> resolver.resolve("ratings-service:photo"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.ROUTE_NOT_FOUND));
    }

    @Test
    void failsWhenTheRouteHasNoBucket() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("  "));

        assertThatThrownBy(() -> resolver.resolve("user-service:document"))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.ROUTE_NOT_CONFIGURED));
    }

    @Test
    void describesTheCatalog() {
        StorageProperties properties = properties("documents-bucket");
        properties.setPresignTtl(Duration.ofMinutes(5));
        StorageRouteResolver resolver = new StorageRouteResolver(properties);

        List<RouteInfoResponse> catalog = resolver.catalog();

        assertThat(catalog).hasSize(1);
        RouteInfoResponse route = catalog.get(0);
        assertThat(route.route()).isEqualTo("user-service:document");
        assertThat(route.bucket()).isEqualTo("documents-bucket");
        assertThat(route.configured()).isTrue();
        assertThat(route.presignTtlSeconds()).isEqualTo(300);
        assertThat(route.maxSizeBytes()).isEqualTo(DataSize.ofMegabytes(10).toBytes());
        assertThat(route.allowedContentTypes()).containsExactly("image/jpeg", "image/png", "image/webp");
    }

    @Test
    void marksRoutesWithoutBucketAsNotConfigured() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties(null));

        assertThat(resolver.catalog()).singleElement()
                .satisfies(route -> assertThat(route.configured()).isFalse());
    }

    @Test
    void anObjectBelongsToTheRouteOnlyUnderItsPrefix() {
        StorageRouteResolver resolver = new StorageRouteResolver(properties("documents-bucket"));
        ResolvedRoute route = resolver.resolve("user-service:document");

        assertThat(route.owns("documents/2026/10/01/id.png")).isTrue();
        assertThat(route.owns("liveness/2026/10/01/id.png")).isFalse();
        assertThat(route.owns(null)).isFalse();
    }

    @Test
    void aRouteWithoutPrefixAcceptsAnyKeyOfItsBucket() {
        StorageProperties properties = properties("documents-bucket");
        properties.getRoutes().get("user-service:document").setPrefix("");
        ResolvedRoute route = new StorageRouteResolver(properties).resolve("user-service:document");

        assertThat(route.owns("anything.png")).isTrue();
        assertThat(route.prefix()).isEmpty();
    }

    @Test
    void aRouteCanOverrideTheGlobalPresignLifetime() {
        StorageProperties properties = properties("documents-bucket");
        properties.getRoutes().get("user-service:document").setPresignTtl(Duration.ofMinutes(2));
        StorageRouteResolver resolver = new StorageRouteResolver(properties);

        ResolvedRoute route = resolver.resolve("user-service:document");

        assertThat(resolver.presignTtl(route)).isEqualTo(Duration.ofMinutes(2));
        assertThat(resolver.knownRoutes()).containsExactly("user-service:document");
    }

    private StorageProperties properties(String bucket) {
        RouteProperties route = new RouteProperties();
        route.setBucket(bucket);
        route.setPrefix("documents");
        route.setMaxSize(DataSize.ofMegabytes(10));

        StorageProperties properties = new StorageProperties();
        properties.getRoutes().put("user-service:document", route);
        return properties;
    }
}
