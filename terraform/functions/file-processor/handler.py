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

    # FILE-03: this is only an extension guard and metadata extraction. The
    # real malware verdict comes from GuardDuty and is stored in the database,
    # so this function must not write a "scan-status" that looks like one.
    extension_check = "BLOCKED" if extension in BLOCKED_EXTENSIONS else "PASSED"
    processing_status = "COMPLETED"
    reason = "blocked_extension" if extension_check == "BLOCKED" else "basic_metadata_extracted"

    own_tags = {
        "extension-check": extension_check,
        "processing-status": processing_status,
        "metadata-size": str(size),
        "metadata-content-type": content_type[:128],
        "processor": "lambda-file-processor",
    }

    # put_object_tagging replaces the whole tag set. Keep tags written by
    # others (GuardDutyMalwareScanStatus) and drop the legacy scan-status tag.
    existing = s3.get_object_tagging(Bucket=bucket, Key=key).get("TagSet", [])
    kept = [t for t in existing if t["Key"] not in own_tags and t["Key"] != "scan-status"]
    tag_set = kept + [{"Key": k, "Value": v} for k, v in own_tags.items()]

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
        "extensionCheck": extension_check,
        "processingStatus": processing_status,
        "reason": reason,
    }
