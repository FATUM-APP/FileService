import boto3

from file_service.config import resolve_route, validate_key_ownership
from file_service.responses import check_auth, no_content, error

s3 = boto3.client("s3")


def handler(event, context):
    if (event.get("requestContext", {}).get("http", {}).get("method") == "OPTIONS"
            or event.get("httpMethod") == "OPTIONS"):
        return no_content()

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
        s3.delete_object(Bucket=route["bucket"], Key=key)
    except Exception as exc:
        return error(502, f"Storage error: {exc}")

    return no_content()
