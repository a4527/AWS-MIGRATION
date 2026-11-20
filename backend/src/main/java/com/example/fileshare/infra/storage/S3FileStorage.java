package com.example.fileshare.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String objectPrefix;

    @Autowired
    public S3FileStorage(
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.object-prefix:files/}") String objectPrefix
    ) {
        this(S3Client.builder().region(Region.of(region)).build(), bucket, objectPrefix);
    }

    S3FileStorage(S3Client s3Client, String bucket, String objectPrefix) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.objectPrefix = normalizePrefix(objectPrefix);
    }

    @Override
    public StoredFile allocate(String originalFileName) {
        String objectName = objectPrefix + ObjectNames.newObjectName(originalFileName);
        return new StoredFile(objectName, objectName);
    }

    @Override
    public void write(StoredFile target, InputStream inputStream, long size, String contentType) throws IOException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(target.storagePath())
                    .contentLength(size)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
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
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException(storagePath, exception);
        } catch (S3Exception exception) {
            // HeadObject reports a missing key as a bare 404 (no NoSuchKey body).
            if (exception.statusCode() == 404) {
                throw new StorageObjectNotFoundException(storagePath, exception);
            }
            throw new IOException("Failed to load file from S3", exception);
        } catch (Exception exception) {
            throw new IOException("Failed to load file from S3", exception);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storagePath)
                    .build());
        } catch (Exception exception) {
            throw new IOException("Failed to delete file from S3", exception);
        }
    }

    @Override
    public List<StorageEntry> list() throws IOException {
        try {
            return s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(objectPrefix)
                            .build())
                    .contents()
                    .stream()
                    .map(object -> new StorageEntry(object.key(), object.size(), object.lastModified()))
                    .toList();
        } catch (Exception exception) {
            throw new IOException("Failed to list files in S3", exception);
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
