package com.flowsight.storage;

import java.io.IOException;
import java.util.Optional;

/**
 * Where reviewed receipt images live. Keys are chosen by the server ({userId}/{receiptId}.jpg),
 * never by the client. The implementation is picked by application.storage.receipt-images.store.
 */
public interface ReceiptImageStore {

    void put(String key, byte[] image) throws IOException;

    /** Empty when nothing is stored under the key (expired, deleted, or lost with the disk). */
    Optional<byte[]> get(String key) throws IOException;

    /** True when something was removed. */
    boolean delete(String key) throws IOException;
}
