package fatum.filestorage.dto;

import java.util.List;

/**
 * Description of a configured route. Exposed so any team can discover which purpose maps to which
 * bucket without reading the deployment manifests.
 */
public record RouteInfoResponse(
        String route,
        String bucket,
        String prefix,
        List<String> allowedContentTypes,
        long maxSizeBytes,
        long presignTtlSeconds,
        boolean configured
) {
}
