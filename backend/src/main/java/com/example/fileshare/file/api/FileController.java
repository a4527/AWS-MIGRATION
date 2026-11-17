package com.example.fileshare.file.api;

import com.example.fileshare.common.api.ApiResponse;
import com.example.fileshare.config.security.AuthUserPrincipal;
import com.example.fileshare.file.application.FileDownload;
import com.example.fileshare.file.application.FileService;
import com.example.fileshare.file.application.FileUploadService;
import com.example.fileshare.file.application.UploadOutcome;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final FileService fileService;
    private final FileUploadService fileUploadService;

    public FileController(FileService fileService, FileUploadService fileUploadService) {
        this.fileService = fileService;
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        UploadOutcome outcome = fileUploadService.upload(principal.toActor(), file, description, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (outcome.replayed()) {
            response.header(IDEMPOTENT_REPLAYED_HEADER, "true");
        }
        return response.body(ApiResponse.ok(FileResponse.from(outcome.metadata()), "File uploaded"));
    }

    @GetMapping
    public ApiResponse<List<FileResponse>> listMine(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(fileService.listMine(principal.toActor()).stream()
                .map(FileResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<FileResponse> get(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(FileResponse.from(fileService.get(principal.toActor(), id)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable Long id
    ) {
        FileDownload download = fileService.download(principal.toActor(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.metadata().originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().mimeType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<FileDeleteResponse> delete(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(FileDeleteResponse.from(fileService.delete(principal.toActor(), id)), "File deleted");
    }
}
