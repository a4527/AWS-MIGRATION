package com.example.fileshare.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.FileMetadataRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** FILE-03: JPA 조건부 갱신이 PENDING이고 삭제되지 않은 행만 바꾸는지 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fileshare-scan-result;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("postgres")
class ScanResultJpaIntegrationTests {

    @Autowired
    private FileMetadataRepository files;

    private FileMetadata save(String path, FileStatus status, ScanStatus scan, Instant createdAt) {
        return files.save(new FileMetadata(null, 1L, "a.txt", path, path, 1, "text/plain", status, scan, null, createdAt, createdAt));
    }

    @Test
    void updatesOnlyPendingNonDeletedRows() {
        Instant t0 = Instant.parse("2026-11-20T00:00:00Z");
        FileMetadata pending = save("files/p.txt", FileStatus.SCANNING, ScanStatus.PENDING, t0);
        save("files/done.txt", FileStatus.QUARANTINED, ScanStatus.INFECTED, t0);
        FileMetadata deleted = save("files/del.txt", FileStatus.SCANNING, ScanStatus.PENDING, t0);
        deleted.markDeleted();
        files.save(deleted);

        assertThat(files.applyScanResult("files/p.txt", ScanStatus.CLEAN, FileStatus.AVAILABLE, t0.plusSeconds(5))).isEqualTo(1);
        assertThat(files.applyScanResult("files/p.txt", ScanStatus.INFECTED, FileStatus.QUARANTINED, t0.plusSeconds(6))).isZero();
        assertThat(files.applyScanResult("files/done.txt", ScanStatus.CLEAN, FileStatus.AVAILABLE, t0.plusSeconds(5))).isZero();
        assertThat(files.applyScanResult("files/del.txt", ScanStatus.CLEAN, FileStatus.AVAILABLE, t0.plusSeconds(5))).isZero();

        FileMetadata after = files.findById(pending.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(after.scanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(files.findById(deleted.id()).orElseThrow().status()).isEqualTo(FileStatus.DELETED);
        assertThat(files.findPendingScanBefore(t0.plusSeconds(60))).isEmpty();
    }
}
