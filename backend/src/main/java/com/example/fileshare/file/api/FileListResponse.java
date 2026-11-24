package com.example.fileshare.file.api;

import com.example.fileshare.file.application.FilePage;
import java.util.List;

/** {@code GET /api/files} 응답. {@code nextCursor}는 다음 요청의 {@code cursor}에 그대로 넘기는 불투명 값이다. */
public record FileListResponse(List<FileResponse> items, String nextCursor) {

    public static FileListResponse from(FilePage page) {
        return new FileListResponse(
                page.items().stream().map(FileResponse::from).toList(),
                page.nextCursor() == null ? null : String.valueOf(page.nextCursor())
        );
    }
}
