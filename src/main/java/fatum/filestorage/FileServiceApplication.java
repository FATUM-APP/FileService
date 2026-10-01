package fatum.filestorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the shared storage service.
 *
 * <p>Any Fatum microservice uploads, downloads and deletes files through this service instead of
 * embedding its own S3 integration. The destination bucket is not hard-coded: every caller sends a
 * route name ({@code service:purpose}) and the service resolves the bucket, prefix, content-type
 * allow list and size limit from configuration.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
