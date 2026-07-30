from __future__ import annotations

import json
import re
from typing import Any

from _python_guardrails import fail


USER_PROFILE_PROVIDER_TYPE = "org.keycloak.userprofile.UserProfileProvider"
USER_PROFILE_CONFIG_KEY = "kc.user.profile.config"
TENANT_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"


def validate_portal_user_profile(
    realm: dict[str, Any],
    *,
    label: str,
    require_seeded_tenants: bool,
) -> None:
    components = realm.get("components") or {}
    providers = components.get(USER_PROFILE_PROVIDER_TYPE) or []
    if len(providers) != 1:
        fail(f"{label} must define exactly one declarative user-profile provider.")

    provider = providers[0]
    if provider.get("providerId") != "declarative-user-profile":
        fail(f"{label} user-profile provider must use declarative-user-profile.")

    raw_configs = (provider.get("config") or {}).get(USER_PROFILE_CONFIG_KEY) or []
    if len(raw_configs) != 1 or not isinstance(raw_configs[0], str):
        fail(f"{label} user-profile provider must contain one serialized profile configuration.")

    try:
        profile = json.loads(raw_configs[0])
    except json.JSONDecodeError as exc:
        fail(f"{label} user-profile configuration is not valid JSON: {exc}")

    tenant_attributes = [
        attribute
        for attribute in profile.get("attributes") or []
        if attribute.get("name") == "tenantId"
    ]
    if len(tenant_attributes) != 1:
        fail(f"{label} must define tenantId exactly once as a managed user-profile attribute.")

    tenant_attribute = tenant_attributes[0]
    permissions = tenant_attribute.get("permissions") or {}
    if permissions.get("view") != ["admin"] or permissions.get("edit") != ["admin"]:
        fail(f"{label} tenantId must be visible and editable only in the administrative context.")

    if tenant_attribute.get("required") != {"roles": ["admin"]}:
        fail(f"{label} tenantId must be required when administrators create or edit portal users.")

    if tenant_attribute.get("multivalued") is not False:
        fail(f"{label} tenantId must be single-valued.")

    validations = tenant_attribute.get("validations") or {}
    if validations.get("length") != {"min": 1, "max": 64}:
        fail(f"{label} tenantId must enforce the approved 1..64 character length.")

    pattern = (validations.get("pattern") or {}).get("pattern")
    if pattern != TENANT_ID_PATTERN:
        fail(f"{label} tenantId must enforce the approved tenant identifier pattern.")

    if require_seeded_tenants:
        for user in realm.get("users") or []:
            username = str(user.get("username") or "<unknown>")
            tenant_values = (user.get("attributes") or {}).get("tenantId") or []
            if len(tenant_values) != 1 or re.fullmatch(TENANT_ID_PATTERN, str(tenant_values[0])) is None:
                fail(f"{label} seeded user '{username}' must have exactly one valid tenantId.")
