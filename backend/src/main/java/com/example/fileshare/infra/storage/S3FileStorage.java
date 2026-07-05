package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorage(
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.bucket}") String bucket
    ) {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
        this.bucket = bucket;
    }

    @Override
    public StoredFile store(String originalFileName, InputStream inputStream) throws IOException {
        try {
            String objectName = UUID.randomUUID() + extensionOf(originalFileName);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(inputStream.readAllBytes()));
            return new StoredFile(objectName, objectName);
        } catch (Exception exception) {
            throw new IOException("Failed to store file in S3", exception);
        }
    }

    @Override
    public StorageObject load(String storagePath) throws IOException {
        try {
            HeadObjectResponse headObject = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(storagePath)
                    .build());
            InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storagePath)
                    .build());
            return new StorageObject(new InputStreamResource(inputStream), headObject.contentLength());
        } catch (Exception exception) {
            throw new IOException("Failed to load file from S3", exception);
        }
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return fileName.substring(dotIndex);
    }
}
