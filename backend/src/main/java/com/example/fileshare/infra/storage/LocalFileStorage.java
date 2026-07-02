package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
    public StoredFile store(String originalFileName, InputStream inputStream) throws IOException {
        Files.createDirectories(root);

        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;
        Path target = root.resolve(storedFileName).normalize();
        Files.copy(inputStream, target);

        return new StoredFile(storedFileName, target.toString());
    }

    @Override
    public StorageObject load(String storagePath) throws IOException {
        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Stored file is not readable");
        }
        return new StorageObject(resource, Files.size(path));
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return fileName.substring(dotIndex);
    }
}
