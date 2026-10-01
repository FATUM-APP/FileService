package fatum.filestorage.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration of the storage service.
 *
 * <p>Binding is relaxed, so any value can be supplied from the environment. Examples:</p>
 * <pre>
 * FATUM_STORAGE_INTERNAL_SECRET=...
 * FATUM_STORAGE_PRESIGN_TTL=15m
 * FATUM_STORAGE_ROUTES_USER-SERVICE:PROFILE-IMAGE_BUCKET=my-bucket
 * </pre>
 * <p>The last form is awkward in shell environments; the recommended way to onboard a new route is
 * the {@code application.yaml} block, which keeps the bucket itself parameterised with an
 * environment variable:</p>
 * <pre>
 * fatum:
 *   storage:
 *     routes:
 *       "ratings-service:photo":
 *         bucket: ${S3_RATINGS_PHOTOS_BUCKET}
 * </pre>
 */
@ConfigurationProperties(prefix = "fatum.storage")
public class StorageProperties {

    /** Shared secret required in the {@code X-Storage-Key} header. Blank disables the check. */
    private String internalSecret = "";

    /** Lifetime of the pre-signed download URLs when a route does not override it. */
    private Duration presignTtl = Duration.ofMinutes(15);

    /** Route used when the caller does not send one. Optional. */
    private String defaultRoute;

    /** Region used by the S3 client when {@code aws.region} is not set. */
    private String region = "us-east-1";

    /** Known routes keyed by {@code microservice:purpose}. */
    private Map<String, RouteProperties> routes = new LinkedHashMap<>();

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public String getDefaultRoute() {
        return defaultRoute;
    }

    public void setDefaultRoute(String defaultRoute) {
        this.defaultRoute = defaultRoute;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Map<String, RouteProperties> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RouteProperties> routes) {
        this.routes = routes;
    }
}
