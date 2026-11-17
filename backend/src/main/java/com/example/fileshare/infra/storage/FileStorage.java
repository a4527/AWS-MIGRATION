package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 파일 바이너리 저장소 포트다.
 *
 * <p>저장 위치는 {@link #allocate(String)}로 먼저 정한다. 쓰기 응답이 유실되어도 호출자가 위치를 알고 있어야
 * 보상 삭제와 정합성 점검을 할 수 있기 때문이다.
 */
public interface FileStorage {

    /** 새 객체의 저장 위치를 정한다. 저장소 입출력은 하지 않는다. */
    StoredFile allocate(String originalFileName);

    /** 정해진 위치에 크기를 아는 스트림을 그대로 쓴다. 전체를 메모리에 올리지 않는다. */
    void write(StoredFile target, InputStream inputStream, long size, String contentType) throws IOException;

    StorageObject load(String storagePath) throws IOException;

    /** 객체를 삭제한다. 이미 없으면 성공으로 본다. */
    void delete(String storagePath) throws IOException;

    /** 저장소의 모든 파일 객체를 나열한다. 정합성 점검용이다. */
    List<StorageEntry> list() throws IOException;
}
