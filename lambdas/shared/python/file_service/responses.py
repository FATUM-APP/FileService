import json
from datetime import datetime, timezone


def _response(status_code, body):
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,X-Storage-Key",
            "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
        },
        "body": json.dumps(body, default=str),
    }


def success(body, status_code=200):
    return _response(status_code, body)


def no_content():
    return {
        "statusCode": 204,
        "headers": {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,X-Storage-Key",
            "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
        },
        "body": "",
    }


def error(status_code, message):
    return _response(status_code, {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status_code,
        "message": message,
    })


def check_auth(event):
    from file_service.config import INTERNAL_SECRET

    if not INTERNAL_SECRET:
        return None
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    if headers.get("x-storage-key") != INTERNAL_SECRET:
        return error(401, "Unauthorized")
    return None
