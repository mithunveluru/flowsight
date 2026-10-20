package com.flowsight.storage;

import com.flowsight.repository.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Swapping the store is configuration only: ReceiptImageService works against whichever one is selected.
class ReceiptImageStoreSelectionTest {

    @TempDir Path uploadDir;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withPropertyValues("application.storage.upload-dir=" + uploadDir)
            .withBean(ReceiptRepository.class, () -> mock(ReceiptRepository.class))
            .withUserConfiguration(LocalDiskReceiptImageStore.class, InMemoryStore.class, ReceiptImageService.class);
    }

    @Test
    void localDiskIsTheDefault() {
        runner().run(ctx -> assertThat(ctx.getBean(ReceiptImageStore.class)).isInstanceOf(LocalDiskReceiptImageStore.class));
    }

    @Test
    void anotherStore_isPickedByConfig_withoutChangingTheService() {
        runner().withPropertyValues("application.storage.receipt-images.store=memory").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(LocalDiskReceiptImageStore.class);
            InMemoryStore store = ctx.getBean(InMemoryStore.class);

            String key = ctx.getBean(ReceiptImageService.class).keep(png(), UUID.randomUUID(), UUID.randomUUID()).orElseThrow();

            assertThat(store.images).containsKey(key);
            assertThat(uploadDir.toFile().list()).isEmpty();
        });
    }

    @Component
    @ConditionalOnProperty(prefix = "application.storage.receipt-images", name = "store", havingValue = "memory")
    static class InMemoryStore implements ReceiptImageStore {
        final Map<String, byte[]> images = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] image) {
            images.put(key, image);
        }

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(images.get(key));
        }

        @Override
        public boolean delete(String key) {
            return images.remove(key) != null;
        }
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
