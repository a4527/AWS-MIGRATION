package com.example.fileshare.infra.storage;

import java.util.UUID;

final class ObjectNames {

    private ObjectNames() {
    }

    static String newObjectName(String originalFileName) {
        return UUID.randomUUID() + extensionOf(originalFileName);
    }

    private static String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return fileName.substring(dotIndex);
    }
}
