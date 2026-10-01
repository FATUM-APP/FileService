package fatum.filestorage.service;

import fatum.filestorage.configuration.RouteProperties;

import java.time.Duration;
import java.util.List;

/**
 * A route that has been validated against the configuration: the destination bucket exists and the
 * limits are ready to be enforced.
 */
public record ResolvedRoute(String name, RouteProperties properties) {

    public String bucket() {
        return properties.getBucket();
    }

    public String prefix() {
        return properties.getPrefix() == null ? "" : properties.getPrefix().trim();
    }

    public List<String> allowedContentTypes() {
        return properties.getAllowedContentTypes() == null ? List.of() : properties.getAllowedContentTypes();
    }

    public long maxSizeBytes() {
        return properties.getMaxSize() == null ? 0L : properties.getMaxSize().toBytes();
    }

    public Duration presignTtl(Duration fallback) {
        return properties.getPresignTtl() == null ? fallback : properties.getPresignTtl();
    }

    /** True when the object key lives under the prefix of this route. */
    public boolean owns(String objectKey) {
        String prefix = prefix();
        if (prefix.isEmpty()) {
            return true;
        }
        return objectKey != null && objectKey.startsWith(prefix + "/");
    }
}
