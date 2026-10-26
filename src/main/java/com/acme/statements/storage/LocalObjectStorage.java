package com.acme.statements.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class LocalObjectStorage implements ObjectStore{

    private final Path root;

    public LocalObjectStorage(Path root) {
        try {
            this.root = Files.createDirectories(root).toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
    }

    @Override
    public void put(String key, Path file, Map<String, String> metadata) throws IOException {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Object key escapes the storage root: " + key);
        }
        Files.createDirectories(target.getParent());
// Copy next to the target, then rename: readers never see half a file.
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(file, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
