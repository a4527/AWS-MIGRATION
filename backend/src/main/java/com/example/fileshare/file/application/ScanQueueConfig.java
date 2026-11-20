package com.example.fileshare.file.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/** GuardDuty 검사 결과 큐 클라이언트(FILE-03). task role 자격 증명을 쓴다. */
@Configuration
@ConditionalOnProperty(name = "app.file.scan.mode", havingValue = "guardduty")
public class ScanQueueConfig {

    @Bean
    SqsClient scanResultSqsClient(@Value("${app.storage.s3.region:us-east-1}") String region) {
        return SqsClient.builder().region(Region.of(region)).build();
    }
}
