package com.example.fileshare.infra.storage;

import java.io.IOException;

/**
 * 저장소에 객체가 없다. 접근 거부·장애 같은 다른 저장소 오류({@link IOException})와 구분해, API가 저장소 장애를
 * "파일 없음"으로 잘못 알리지 않게 한다(OPS-01 T3에서 발견).
 */
public class StorageObjectNotFoundException extends IOException {

    public StorageObjectNotFoundException(String storagePath, Throwable cause) {
        super("Stored object not found: " + storagePath, cause);
    }
}
