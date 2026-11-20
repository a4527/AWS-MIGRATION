package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.config.security.AuthUserPrincipal;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import com.example.fileshare.file.repository.InMemoryUploadRequestRepository;
import com.example.fileshare.infra.storage.LocalFileStorage;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileServiceAccessTests {

    @TempDir
    Path storageRoot;

    private FileService fileService;
    private FileUploadService uploadService;

    @BeforeEach
    void setUp() {
        InMemoryFileMetadataRepository files = new InMemoryFileMetadataRepository();
        InMemoryUploadRequestRepository requests = new InMemoryUploadRequestRepository();
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        NoOpFileMetadataCache cache = new NoOpFileMetadataCache();
        fileService = new FileService(files, storage, cache);
        uploadService = new FileUploadService(files, requests, new UploadRecorder(files, requests), storage, cache,
                TestFixtures.policy(), ScanPolicy.none(), Clock.systemUTC());
    }

    @Test
    void principalRoleIsConvertedToActor() {
        assertThat(new AuthUserPrincipal(1L, "admin@example.com", "ADMIN").toActor())
                .isEqualTo(new Actor(1L, true));
        assertThat(new AuthUserPrincipal(2L, "user@example.com", "USER").toActor())
                .isEqualTo(new Actor(2L, false));
    }

    @Test
    void ownerAndAdminCanReadButOtherUserCannot() {
        Actor owner = new Actor(1L, false);
        FileMetadata uploaded = uploadService.upload(owner, textFile(), null, null).metadata();

        assertThat(fileService.get(owner, uploaded.id()).id()).isEqualTo(uploaded.id());
        assertThat(fileService.get(new Actor(99L, true), uploaded.id()).id()).isEqualTo(uploaded.id());
        assertThatThrownBy(() -> fileService.get(new Actor(2L, false), uploaded.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AUTH_ACCESS_DENIED));
    }

    @Test
    void otherUserCannotDeleteButAdminCan() {
        FileMetadata uploaded = uploadService.upload(new Actor(1L, false), textFile(), null, null).metadata();

        assertThatThrownBy(() -> fileService.delete(new Actor(2L, false), uploaded.id()))
                .isInstanceOf(BusinessException.class);
        fileService.delete(new Actor(99L, true), uploaded.id());
        assertThatThrownBy(() -> fileService.get(new Actor(1L, false), uploaded.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
    }
}
