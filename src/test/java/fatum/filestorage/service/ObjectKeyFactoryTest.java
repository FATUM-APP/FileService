package fatum.filestorage.service;

import fatum.filestorage.exception.FileStorageException;
import fatum.filestorage.exception.StorageError;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeyFactoryTest {

    private final ObjectKeyFactory factory = new ObjectKeyFactory();

    @Test
    void buildsAKeyWithPrefixDateAndIdentifier() {
        ObjectKeyFactory.GeneratedKey generated =
                factory.create("documents", "id-front.png", LocalDate.of(2026, 10, 1));

        assertThat(generated.id()).isNotBlank();
        assertThat(generated.key()).isEqualTo("documents/2026/10/01/" + generated.id() + "-id-front.png");
    }

    @Test
    void buildsAKeyWithoutPrefixWhenTheRouteHasNone() {
        ObjectKeyFactory.GeneratedKey generated = factory.create("", "photo.png", LocalDate.of(2026, 1, 2));

        assertThat(generated.key()).startsWith("2026/01/02/").endsWith("-photo.png");
    }

    @Test
    void trimsSlashesAroundThePrefix() {
        ObjectKeyFactory.GeneratedKey generated = factory.create("/photos/", "photo.png", LocalDate.of(2026, 1, 2));

        assertThat(generated.key()).startsWith("photos/2026/01/02/");
    }

    @Test
    void sanitizesClientSuppliedNames() {
        assertThat(factory.sanitizeFilename("avatar.png")).isEqualTo("avatar.png");
        assertThat(factory.sanitizeFilename("/tmp/uploads/avatar.png")).isEqualTo("avatar.png");
        assertThat(factory.sanitizeFilename("folder/../avatar.png")).isEqualTo("avatar.png");
        assertThat(factory.sanitizeFilename("my photo.png")).isEqualTo("my_photo.png");
        assertThat(factory.sanitizeFilename("id\u0000card.png")).isEqualTo("idcard.png");
    }

    @Test
    void rejectsNamesThatCannotBeStored() {
        assertThatThrownBy(() -> factory.sanitizeFilename(null))
                .isInstanceOf(FileStorageException.class)
                .satisfies(exception -> assertThat(((FileStorageException) exception).error())
                        .isEqualTo(StorageError.INVALID_FILENAME));
        assertThatThrownBy(() -> factory.sanitizeFilename("   "))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> factory.sanitizeFilename(".."))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> factory.sanitizeFilename("folder/"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void shortensExtremelyLongNames() {
        String longName = "a".repeat(300) + ".png";

        String sanitized = factory.sanitizeFilename(longName);

        assertThat(sanitized).hasSize(180).endsWith(".png");
    }

    @Test
    void normalizesContentTypes() {
        assertThat(factory.normalizeContentType("IMAGE/PNG")).isEqualTo("image/png");
        assertThat(factory.normalizeContentType("image/jpeg; charset=utf-8")).isEqualTo("image/jpeg");
        assertThat(factory.normalizeContentType("  ")).isNull();
        assertThat(factory.normalizeContentType(null)).isNull();
    }
}
