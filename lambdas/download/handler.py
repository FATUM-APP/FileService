import boto3
from botocore.config import Config

from file_service.config import resolve_route, validate_key_ownership, PRESIGN_TTL
from file_service.responses import check_auth, success, error

s3 = boto3.client("s3", config=Config(signature_version="s3v4"))


def handler(event, context):
    if (event.get("requestContext", {}).get("http", {}).get("method") == "OPTIONS"
            or event.get("httpMethod") == "OPTIONS"):
        return success({})

    auth_err = check_auth(event)
    if auth_err:
        return auth_err

    params = event.get("queryStringParameters") or {}
    route_name = params.get("route")
    key = params.get("key")

    route, err = resolve_route(route_name)
    if err:
        return error(400, err)

    if not key:
        return error(400, "key query parameter is required")

    if not validate_key_ownership(route, key):
        return error(400, f"key does not belong to route '{route_name}'")

    try:
        url = s3.generate_presigned_url(
            "get_object",
            Params={"Bucket": route["bucket"], "Key": key},
            ExpiresIn=PRESIGN_TTL,
        )
    except Exception as exc:
        return error(502, f"Storage error: {exc}")

    from datetime import datetime, timezone, timedelta

    expires_at = datetime.now(timezone.utc) + timedelta(seconds=PRESIGN_TTL)

    return success({
        "key": key,
        "bucket": route["bucket"],
        "route": route_name,
        "url": url,
        "expiresAt": expires_at.isoformat(),
    })
