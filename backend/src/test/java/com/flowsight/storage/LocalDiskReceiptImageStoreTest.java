package com.flowsight.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskReceiptImageStoreTest {

    @TempDir Path tmp;
    private Path root;
    private LocalDiskReceiptImageStore store;

    @BeforeEach
    void setUp() {
        root = tmp.resolve("flowsight-receipts");
        store = new LocalDiskReceiptImageStore(root.toString());
        store.init();
    }

    @Test
    void putGetDelete_roundTrip() throws IOException {
        store.put("user/receipt.jpg", new byte[]{1, 2, 3});

        assertThat(Files.exists(root.resolve("user/receipt.jpg"))).isTrue();
        assertThat(store.get("user/receipt.jpg").orElseThrow()).containsExactly(1, 2, 3);
        assertThat(store.delete("user/receipt.jpg")).isTrue();
        assertThat(store.get("user/receipt.jpg")).isEmpty();
        assertThat(store.delete("user/receipt.jpg")).isFalse();
    }

    @Test
    void keysOutsideTheRoot_areRefused_soReportPdfsAreNeverTouched() throws IOException {
        Path reports = Files.createDirectories(tmp.resolve("flowsight-reports"));
        Path pdf = Files.write(reports.resolve("report.pdf"), new byte[]{9});

        assertThatThrownBy(() -> store.delete("../flowsight-reports/report.pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete(pdf.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("../flowsight-reports/report.pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("../../etc/x.jpg", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(pdf).exists();
    }

    @Test
    void absolutePathsUnderTheRoot_fromBeforeRetention_canBeDeleted() throws IOException {
        Path legacy = Files.createDirectories(root.resolve("user")).resolve("receipt.png");
        Files.write(legacy, new byte[]{1});

        assertThat(store.delete(legacy.toString())).isTrue();
        assertThat(legacy).doesNotExist();
    }
}
