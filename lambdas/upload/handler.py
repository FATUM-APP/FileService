import base64
import re
import uuid
from datetime import datetime, timezone

import boto3

from file_service.config import resolve_route, is_content_type_allowed
from file_service.responses import check_auth, success, error

s3 = boto3.client("s3")


def handler(event, context):
    if (event.get("requestContext", {}).get("http", {}).get("method") == "OPTIONS"
            or event.get("httpMethod") == "OPTIONS"):
        return success({})

    auth_err = check_auth(event)
    if auth_err:
        return auth_err

    params = event.get("queryStringParameters") or {}
    route_name = params.get("route")
    route, err = resolve_route(route_name)
    if err:
        return error(400, err)

    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    content_type_header = headers.get("content-type", "")

    body = event.get("body", "") or ""
    if event.get("isBase64Encoded", False):
        raw_body = base64.b64decode(body)
    else:
        raw_body = body.encode() if isinstance(body, str) else body

    if "multipart/form-data" in content_type_header:
        file_data, filename, file_ct = _parse_multipart(raw_body, content_type_header)
    else:
        file_data = raw_body
        filename = params.get("filename", "upload")
        file_ct = content_type_header or "application/octet-stream"

    if not file_data:
        return error(400, "No file provided")

    file_size = len(file_data)
    if file_size == 0:
        return error(400, "Empty file")

    if file_size > route["max_size"]:
        max_mb = route["max_size"] / (1024 * 1024)
        return error(413, f"File too large. Max: {max_mb:.0f}MB")

    if not is_content_type_allowed(file_ct, route["allowed_content_types"]):
        return error(
            415,
            f"Content type '{file_ct}' not allowed. "
            f"Allowed: {route['allowed_content_types']}",
        )

    filename = _sanitize_filename(filename)
    file_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)
    key = (
        f"{route['prefix']}/{now.year}/{now.month:02d}/{now.day:02d}"
        f"/{file_id}-{filename}"
    )

    try:
        resp = s3.put_object(
            Bucket=route["bucket"],
            Key=key,
            Body=file_data,
            ContentType=file_ct,
            Metadata={"route": route_name, "original-filename": filename},
        )
    except Exception as exc:
        return error(502, f"Storage error: {exc}")

    return success(
        {
            "id": file_id,
            "key": key,
            "bucket": route["bucket"],
            "route": route_name,
            "originalFilename": filename,
            "contentType": file_ct,
            "size": file_size,
            "etag": resp.get("ETag", ""),
            "createdAt": now.isoformat(),
        },
        201,
    )


def _parse_multipart(body, content_type):
    match = re.search(r"boundary=([^\s;]+)", content_type)
    if not match:
        return None, None, None

    boundary = match.group(1).encode()
    parts = body.split(b"--" + boundary)

    for part in parts:
        if b"Content-Disposition" not in part:
            continue

        segments = part.split(b"\r\n\r\n", 1)
        if len(segments) < 2:
            continue

        header_block, file_body = segments
        header_str = header_block.decode("utf-8", errors="replace")

        if 'filename="' not in header_str:
            continue

        fn_match = re.search(r'filename="([^"]*)"', header_str)
        filename = fn_match.group(1) if fn_match else "upload"

        ct_match = re.search(r"Content-Type:\s*(\S+)", header_str, re.IGNORECASE)
        file_ct = ct_match.group(1) if ct_match else "application/octet-stream"

        if file_body.endswith(b"\r\n"):
            file_body = file_body[:-2]

        return file_body, filename, file_ct

    return None, None, None


def _sanitize_filename(filename):
    if not filename:
        return "upload"
    filename = filename.replace("\\", "/").split("/")[-1]
    filename = filename.replace(" ", "_")
    filename = re.sub(r"[^\w.\-]", "", filename)
    if not filename or filename in (".", ".."):
        return "upload"
    if len(filename) > 180:
        filename = filename[-180:]
    return filename
