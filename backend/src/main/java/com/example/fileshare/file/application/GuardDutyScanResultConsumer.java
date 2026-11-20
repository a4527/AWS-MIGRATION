package com.example.fileshare.file.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * GuardDuty Malware Protection for S3 결과(EventBridge → SQS)를 읽어 반영한다(FILE-03).
 *
 * <p>처리했거나 무시해도 되는 메시지만 지운다. 파일 행이 아직 없거나 형식이 틀린 메시지는 지우지 않아 visibility timeout 뒤
 * 다시 받고, 정해진 횟수를 넘으면 SQS가 DLQ로 옮긴다. 다른 bucket의 결과는 지운다.
 */
@Component
@ConditionalOnProperty(name = "app.file.scan.mode", havingValue = "guardduty")
public class GuardDutyScanResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(GuardDutyScanResultConsumer.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final ScanResultService scanResultService;
    private final String queueUrl;
    private final String bucket;

    public GuardDutyScanResultConsumer(
            SqsClient sqs,
            ObjectMapper objectMapper,
            ScanResultService scanResultService,
            @Value("${app.file.scan.queue-url}") String queueUrl,
            @Value("${app.storage.s3.bucket}") String bucket
    ) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.scanResultService = scanResultService;
        this.queueUrl = queueUrl;
        this.bucket = bucket;
    }

    @Scheduled(fixedDelayString = "${app.file.scan.poll-delay:PT1S}")
    public void poll() {
        try {
            for (Message message : sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
                    .build()).messages()) {
                if (handle(message.body())) {
                    sqs.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                }
            }
        } catch (RuntimeException exception) {
            log.error("Scan result polling failed. queueUrl={}", queueUrl, exception);
        }
    }

    /** 메시지를 지워도 되면 true. */
    boolean handle(String body) {
        JsonNode detail;
        try {
            detail = objectMapper.readTree(body).path("detail");
        } catch (Exception exception) {
            log.error("Malformed scan result message; leaving it for the DLQ.", exception);
            return false;
        }
        JsonNode object = detail.path("s3ObjectDetails");
        String eventBucket = object.path("bucketName").asText("");
        String key = object.path("objectKey").asText("");
        String resultStatus = detail.path("scanResultDetails").path("scanResultStatus").asText("");
        if (key.isEmpty() || resultStatus.isEmpty()) {
            log.error("Scan result without object key or result status; leaving it for the DLQ.");
            return false;
        }
        if (!bucket.equals(eventBucket)) {
            log.warn("Scan result for another bucket ignored. bucket={}", eventBucket);
            return true;
        }
        ScanOutcome outcome = ScanOutcome.fromGuardDuty(resultStatus);
        ScanResultService.Result result = scanResultService.apply(key, outcome, resultStatus);
        return result != ScanResultService.Result.NOT_FOUND;
    }
}
