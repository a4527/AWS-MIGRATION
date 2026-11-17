package com.example.fileshare.infra.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorage(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @Override
    public StoredFile allocate(String originalFileName) {
        String objectName = ObjectNames.newObjectName(originalFileName);
        return new StoredFile(objectName, objectName);
    }

    @Override
    public void write(StoredFile target, InputStream inputStream, long size, String contentType) throws IOException {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(target.storagePath())
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new IOException("Failed to store file in MinIO", exception);
        }
    }

    @Override
    public StorageObject load(String storagePath) throws IOException {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(storagePath)
                    .build());
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storagePath)
                    .build());
            return new StorageObject(new InputStreamResource(inputStream), stat.size());
        } catch (Exception exception) {
            throw new IOException("Failed to load file from MinIO", exception);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storagePath)
                    .build());
        } catch (Exception exception) {
            throw new IOException("Failed to delete file from MinIO", exception);
        }
    }

    @Override
    public List<StorageEntry> list() throws IOException {
        try {
            List<StorageEntry> entries = new ArrayList<>();
            for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .recursive(true)
                    .build())) {
                Item item = result.get();
                if (!item.isDir()) {
                    entries.add(new StorageEntry(item.objectName(), item.size(), item.lastModified().toInstant()));
                }
            }
            return entries;
        } catch (Exception exception) {
            throw new IOException("Failed to list files in MinIO", exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build());
        }
    }
}
