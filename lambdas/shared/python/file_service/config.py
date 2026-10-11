import os

ROUTES = {
    "user-service:profile-image": {
        "bucket_env": "S3_USER_PROFILE_IMAGES_BUCKET",
        "prefix": "profile-images",
        "allowed_content_types": ["image/jpeg", "image/png", "image/webp"],
        "max_size": 5 * 1024 * 1024,
    },
    "user-service:document": {
        "bucket_env": "S3_USER_DOCUMENTS_BUCKET",
        "prefix": "documents",
        "allowed_content_types": [
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
        ],
        "max_size": 10 * 1024 * 1024,
    },
}

PRESIGN_TTL = int(os.environ.get("PRESIGN_TTL_SECONDS", "900"))
INTERNAL_SECRET = os.environ.get("STORAGE_INTERNAL_SECRET", "")


def resolve_route(route_name):
    if not route_name:
        available = list(ROUTES.keys())
        return None, f"route query parameter is required. Available: {available}"

    route_def = ROUTES.get(route_name)
    if not route_def:
        available = list(ROUTES.keys())
        return None, f"route '{route_name}' not found. Available: {available}"

    bucket = os.environ.get(route_def["bucket_env"], "")
    if not bucket:
        return None, f"route '{route_name}' has no bucket configured"

    return {**route_def, "bucket": bucket}, None


def validate_key_ownership(route, key):
    if not key:
        return False
    prefix = route["prefix"]
    return key.startswith(f"{prefix}/")


def is_content_type_allowed(content_type, allowed):
    if not content_type:
        return False
    for pattern in allowed:
        if pattern == content_type:
            return True
        if pattern.endswith("/*") and content_type.startswith(pattern[:-1]):
            return True
    return False
