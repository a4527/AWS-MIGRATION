import json
import logging
import os
import urllib.parse

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")

BLOCKED_EXTENSIONS = {
    extension.strip().lower()
    for extension in os.environ.get("BLOCKED_EXTENSIONS", ".exe,.bat,.cmd,.sh").split(",")
    if extension.strip()
}


def lambda_handler(event, context):
    processed = []

    for record in event.get("Records", []):
        if not record.get("eventName", "").startswith("ObjectCreated:"):
            continue

        bucket = record["s3"]["bucket"]["name"]
        key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])
        result = process_object(bucket, key)
        processed.append(result)

    logger.info("processed_objects=%s", json.dumps(processed, ensure_ascii=False))
    return {"processed": processed}


def process_object(bucket, key):
    head = s3.head_object(Bucket=bucket, Key=key)
    size = head.get("ContentLength", 0)
    content_type = head.get("ContentType", "application/octet-stream")
    extension = os.path.splitext(key)[1].lower()

    scan_status = "CLEAN"
    processing_status = "COMPLETED"
    reason = "basic_metadata_extracted"

    if extension in BLOCKED_EXTENSIONS:
        scan_status = "FAILED"
        processing_status = "QUARANTINED"
        reason = "blocked_extension"

    tag_set = [
        {"Key": "scan-status", "Value": scan_status},
        {"Key": "processing-status", "Value": processing_status},
        {"Key": "metadata-size", "Value": str(size)},
        {"Key": "metadata-content-type", "Value": content_type[:128]},
        {"Key": "processor", "Value": "lambda-file-processor"},
    ]

    s3.put_object_tagging(
        Bucket=bucket,
        Key=key,
        Tagging={"TagSet": tag_set},
    )

    return {
        "bucket": bucket,
        "key": key,
        "size": size,
        "contentType": content_type,
        "scanStatus": scan_status,
        "processingStatus": processing_status,
        "reason": reason,
    }
