package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {

    StoredFile store(String originalFileName, InputStream inputStream) throws IOException;

    StorageObject load(String storagePath) throws IOException;
}
