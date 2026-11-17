package com.example.fileshare.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3FileStorageTests {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3FileStorage storage = new S3FileStorage(s3Client, "bucket", "files");

    @Test
    void allocatesKeyUnderPrefixWithoutCallingS3() {
        StoredFile target = storage.allocate("report.pdf");

        assertThat(target.storagePath()).startsWith("files/").endsWith(".pdf");
        assertThat(target.storedFileName()).isEqualTo(target.storagePath());
    }

    @Test
    void writeStreamsWithKnownLengthInsteadOfBufferingWholeFile() throws IOException {
        byte[] content = new byte[4096];
        CountingInputStream input = new CountingInputStream(new ByteArrayInputStream(content));
        StoredFile target = storage.allocate("note.txt");

        storage.write(target, input, content.length, "text/plain");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().key()).isEqualTo(target.storagePath());
        assertThat(request.getValue().contentLength()).isEqualTo(content.length);
        assertThat(request.getValue().contentType()).isEqualTo("text/plain");
        assertThat(body.getValue().optionalContentLength()).contains((long) content.length);
        // 업로드 전에 애플리케이션이 스트림을 미리 읽지 않는다. 읽기는 SDK가 전송하면서 수행한다.
        assertThat(input.bytesRead).isZero();
    }

    @Test
    void deleteRemovesObjectByKey() throws IOException {
        storage.delete("files/a.txt");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("files/a.txt");
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
    }

    private static final class CountingInputStream extends FilterInputStream {

        long bytesRead;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }
    }
}
