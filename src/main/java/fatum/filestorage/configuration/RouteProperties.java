package fatum.filestorage.configuration;

import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of a single storage route.
 *
 * <p>A route is the pair {@code microservice:purpose} (for example {@code user-service:profile-image})
 * and describes where and how the files of that purpose are stored. Everything is bound from
 * {@code application.yaml} or from environment variables, so a new purpose can be onboarded without
 * touching the code.</p>
 */
public class RouteProperties {

    /** Destination bucket. Required: a route without a bucket fails with 503. */
    private String bucket;

    /** Key prefix inside the bucket. Empty means files are stored at the bucket root. */
    private String prefix = "";

    /** Content types accepted for this route. Empty means every content type is accepted. */
    private List<String> allowedContentTypes = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));

    /** Maximum accepted file size for this route. */
    private DataSize maxSize = DataSize.ofMegabytes(10);

    /** Optional override of the global pre-signed URL lifetime. */
    private Duration presignTtl;

    /** Extra metadata stored next to every object of this route. */
    private Map<String, String> metadata = new LinkedHashMap<>();

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public DataSize getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(DataSize maxSize) {
        this.maxSize = maxSize;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
