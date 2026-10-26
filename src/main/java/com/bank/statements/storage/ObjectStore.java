package com.bank.statements.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ObjectStore extends AutoCloseable{
    void put(String key, Path file, Map<String, String> metadata) throws IOException;

    @Override
    default void close() {

    }
}
