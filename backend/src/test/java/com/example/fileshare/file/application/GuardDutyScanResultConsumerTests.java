package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;

/** FILE-03: GuardDuty 결과 메시지 해석과 삭제 여부. */
class GuardDutyScanResultConsumerTests {

    private final InMemoryFileMetadataRepository files = new InMemoryFileMetadataRepository();
    private final ScanPolicy policy = new ScanPolicy("guardduty", Duration.ofMinutes(15));
    private final GuardDutyScanResultConsumer consumer = new GuardDutyScanResultConsumer(
            mock(SqsClient.class), new ObjectMapper(),
            new ScanResultService(files, new NoOpFileMetadataCache(), policy, Clock.systemUTC()),
            "https://sqs.example/queue", "my-bucket");

    private static String event(String bucket, String key, String status) {
        return """
                {"detail-type":"GuardDuty Malware Protection Object Scan Result","source":"aws.guardduty",
                 "detail":{"scanStatus":"COMPLETED","resourceType":"S3_OBJECT",
                   "s3ObjectDetails":{"bucketName":"%s","objectKey":"%s","eTag":"e","versionId":"v"},
                   "scanResultDetails":{"scanResultStatus":"%s","threats":null}}}
                """.formatted(bucket, key, status);
    }

    private FileMetadata pending(String path) {
        Instant now = Instant.now();
        return files.save(new FileMetadata(null, 1L, "a.txt", path, path, 1, "text/plain",
                policy.initialStatus(), policy.initialScanStatus(), null, now, now));
    }

    @Test
    void appliesResultAndDeletesMessage() {
        FileMetadata file = pending("files/a.txt");
        assertThat(consumer.handle(event("my-bucket", "files/a.txt", "THREATS_FOUND"))).isTrue();
        assertThat(files.findById(file.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.INFECTED);
    }

    @Test
    void keepsMessageWhenFileRowDoesNotExistYet() {
        assertThat(consumer.handle(event("my-bucket", "files/not-yet.txt", "NO_THREATS_FOUND"))).isFalse();
    }

    @Test
    void keepsMalformedMessageForTheDlq() {
        assertThat(consumer.handle("not json")).isFalse();
        assertThat(consumer.handle("{\"detail\":{}}")).isFalse();
    }

    @Test
    void dropsResultsForOtherBuckets() {
        FileMetadata file = pending("files/b.txt");
        assertThat(consumer.handle(event("someone-else", "files/b.txt", "NO_THREATS_FOUND"))).isTrue();
        assertThat(files.findById(file.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.PENDING);
    }
}
