package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.List;

/** 목록 한 페이지와 다음 페이지 커서. 마지막 페이지면 {@code nextCursor}가 null이다(PERF-02). */
public record FilePage(List<FileMetadata> items, Long nextCursor) {
}
