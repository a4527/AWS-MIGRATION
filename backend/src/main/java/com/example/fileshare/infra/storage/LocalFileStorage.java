package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.local-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile allocate(String originalFileName) {
        String storedFileName = ObjectNames.newObjectName(originalFileName);
        return new StoredFile(storedFileName, root.resolve(storedFileName).normalize().toString());
    }

    @Override
    public void write(StoredFile target, InputStream inputStream, long size, String contentType) throws IOException {
        Files.createDirectories(root);
        Files.copy(inputStream, Path.of(target.storagePath()));
    }

    @Override
    public StorageObject load(String storagePath) throws IOException {
        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            throw new StorageObjectNotFoundException(storagePath, null);
        }
        if (!resource.isReadable()) {
            throw new IOException("Stored file is not readable");
        }
        return new StorageObject(resource, Files.size(path));
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(Path.of(storagePath));
    }

    @Override
    public List<StorageEntry> list() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(this::toEntry)
                    .toList();
        }
    }

    private StorageEntry toEntry(Path path) {
        try {
            return new StorageEntry(path.toString(), Files.size(path), Files.getLastModifiedTime(path).toInstant());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local object attributes: " + path, exception);
        }
    }
}
