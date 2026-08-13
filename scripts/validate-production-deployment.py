from __future__ import annotations

import argparse
import ipaddress
import json
import os
import socket
import ssl
import stat
import sys
import time
from pathlib import Path
from typing import Callable, Mapping
from urllib.parse import SplitResult, urlsplit


class DeploymentValidationError(RuntimeError):
    """Raised when the operator-provided production configuration is unsafe."""


REQUIRED_PORTAL_URLS = (
    "APP_FRONTEND_ORIGIN",
    "APP_OPENAPI_SERVER_URL",
    "APP_SECURITY_JWT_ISSUER",
    "APP_MEETINGS_TOKEN_ISSUER",
)

RFC1918_NETWORKS = tuple(
    ipaddress.ip_network(cidr)
    for cidr in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
)

REQUIRED_SECRET_FILES = (
    "BACKEND_VAULT_ROLE_ID_PATH",
    "BACKEND_VAULT_WRAPPED_SECRET_ID_PATH",
    "POSTGRES_VAULT_ENV_FILE_PATH",
    "REDIS_VAULT_ENV_FILE_PATH",
    "KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH",
    "KEYCLOAK_VAULT_ENV_FILE_PATH",
    "JITSI_WEB_VAULT_ENV_FILE_PATH",
    "JITSI_PROSODY_VAULT_ENV_FILE_PATH",
    "JITSI_JICOFO_VAULT_ENV_FILE_PATH",
    "JITSI_JVB_VAULT_ENV_FILE_PATH",
)

SERVICE_ENV_REQUIREMENTS = {
    "POSTGRES_VAULT_ENV_FILE_PATH": ("POSTGRES_PASSWORD",),
    "REDIS_VAULT_ENV_FILE_PATH": ("REDIS_PASSWORD",),
    "KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH": ("POSTGRES_PASSWORD",),
    "KEYCLOAK_VAULT_ENV_FILE_PATH": (
        "KC_BOOTSTRAP_ADMIN_PASSWORD",
        "KC_DB_PASSWORD",
        "SSO_CLIENT_SECRET",
    ),
    "JITSI_WEB_VAULT_ENV_FILE_PATH": (
        "JWT_APP_SECRET",
        "JICOFO_AUTH_PASSWORD",
        "JVB_AUTH_PASSWORD",
    ),
    "JITSI_PROSODY_VAULT_ENV_FILE_PATH": (
        "JWT_APP_SECRET",
        "JICOFO_AUTH_PASSWORD",
        "JVB_AUTH_PASSWORD",
    ),
    "JITSI_JICOFO_VAULT_ENV_FILE_PATH": (
        "JICOFO_AUTH_PASSWORD",
        "JICOFO_COMPONENT_SECRET",
    ),
    "JITSI_JVB_VAULT_ENV_FILE_PATH": ("JVB_AUTH_PASSWORD",),
}

OIDC_INTERNAL_ENDPOINTS = {
    "SSO_TOKEN_URI": "token",
    "SSO_JWK_SET_URI": "certs",
    "SSO_USER_INFO_URI": "userinfo",
}

PLACEHOLDER_MARKERS = (
    "${",
    "change-me",
    "changeme",
    "example.com",
    "placeholder",
    "replace-with",
    "set-in-vault",
    "set_in_vault",
    "your-",
    "your_",
    "192.0.2.",
    "198.51.100.",
    "203.0.113.",
)

PLACEHOLDER_HOST_PARTS = (
    "example",
    "localhost",
)

DnsResolver = Callable[[str], set[str]]


def parse_env_file(env_file: Path) -> dict[str, str]:
    if not env_file.is_file():
        raise DeploymentValidationError("Production env file is missing or is not a regular file.")

    values: dict[str, str] = {}
    try:
        lines = env_file.read_text(encoding="utf-8-sig").splitlines()
    except (OSError, UnicodeError) as exc:
        raise DeploymentValidationError("Production env file cannot be read as UTF-8 text.") from exc

    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line.removeprefix("export ").lstrip()
        if "=" not in line:
            raise DeploymentValidationError(
                f"Production env file contains an invalid assignment at line {line_number}."
            )

        key, raw_value = line.split("=", 1)
        key = key.strip()
        if not key or not key.replace("_", "A").isalnum() or not (key[0].isalpha() or key[0] == "_"):
            raise DeploymentValidationError(
                f"Production env file contains an invalid variable name at line {line_number}."
            )
        if key in values:
            raise DeploymentValidationError(f"Production env file defines {key} more than once.")

        values[key] = _unquote_env_value(raw_value.strip(), line_number)

    return values


def _unquote_env_value(raw_value: str, line_number: int) -> str:
    if not raw_value:
        return ""
    if raw_value[0] not in {'"', "'"}:
        return raw_value
    if len(raw_value) < 2 or raw_value[-1] != raw_value[0]:
        raise DeploymentValidationError(
            f"Production env file contains an unterminated quoted value at line {line_number}."
        )
    return raw_value[1:-1]


def validate_deployment(
    env_file: Path,
    *,
    skip_dns: bool = False,
    resolver: DnsResolver | None = None,
    expected_realm: str | None = None,
    enforce_posix_permissions: bool | None = None,
    validate_certificates: bool = True,
) -> None:
    env_file = env_file.resolve()
    env = parse_env_file(env_file)
    realm = expected_realm or read_production_realm()

    _reject_example_env_file(env_file)
    portal_host = _validate_portal_urls(env)
    auth_host = _validate_oidc_urls(env, realm)
    meet_host = _validate_meeting_urls(env)
    _validate_distinct_hosts(portal_host, auth_host, meet_host)

    public_ip = _validate_jvb_advertise_ips(_required(env, "JVB_ADVERTISE_IPS"))
    _validate_operator_files(
        env,
        env_file.parent,
        portal_host,
        auth_host,
        meet_host,
        enforce_posix_permissions=enforce_posix_permissions,
        validate_certificates=validate_certificates,
    )
    _validate_service_secret_contracts(env, env_file.parent)
    _validate_realm_import(
        env,
        env_file.parent,
        realm,
        enforce_posix_permissions=enforce_posix_permissions,
    )

    if not skip_dns:
        dns_resolver = resolver or resolve_ipv4_addresses
        _validate_dns((portal_host, auth_host, meet_host), public_ip, dns_resolver)


def read_production_realm() -> str:
    realm_file = Path(__file__).resolve().parent.parent / "pilot" / "keycloak" / "realm" / "production" / "jitsi-realm.json"
    try:
        payload = json.loads(realm_file.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise DeploymentValidationError("Production Keycloak realm definition cannot be read.") from exc
    realm = payload.get("realm")
    if not isinstance(realm, str) or not realm.strip():
        raise DeploymentValidationError("Production Keycloak realm definition has no realm name.")
    return realm.strip()


def _reject_example_env_file(env_file: Path) -> None:
    if env_file.name.endswith(".example"):
        raise DeploymentValidationError("Example env files cannot be used for a production deployment.")


def _validate_portal_urls(env: Mapping[str, str]) -> str:
    parsed_urls = {
        key: _parse_public_origin(key, _required(env, key))
        for key in REQUIRED_PORTAL_URLS
    }
    canonical = _origin(parsed_urls["APP_FRONTEND_ORIGIN"])
    if any(_origin(parsed) != canonical for parsed in parsed_urls.values()):
        raise DeploymentValidationError("Portal public URL variables must use one identical HTTPS origin.")

    meetings_service = _parse_https_url(
        "APP_MEETINGS_SERVICE_URL",
        _required(env, "APP_MEETINGS_SERVICE_URL"),
    )
    if (
        _origin(meetings_service) != canonical
        or meetings_service.path.rstrip("/") != "/api/v1"
        or meetings_service.query
        or meetings_service.fragment
    ):
        raise DeploymentValidationError(
            "APP_MEETINGS_SERVICE_URL must use the portal HTTPS origin and the /api/v1 path."
        )
    return _hostname(parsed_urls["APP_FRONTEND_ORIGIN"])


def _validate_oidc_urls(env: Mapping[str, str], expected_realm: str) -> str:
    hostname_url = _parse_public_origin("KC_HOSTNAME", _required(env, "KC_HOSTNAME"))
    auth_host = _hostname(hostname_url)
    auth_origin = _origin(hostname_url)

    authorization = _parse_https_url("SSO_AUTHORIZATION_URI", _required(env, "SSO_AUTHORIZATION_URI"))
    expected_issuer = _parse_https_url(
        "APP_SECURITY_SSO_EXPECTED_ISSUER",
        _required(env, "APP_SECURITY_SSO_EXPECTED_ISSUER"),
    )
    expected_authorization_path = f"/realms/{expected_realm}/protocol/openid-connect/auth"
    expected_issuer_path = f"/realms/{expected_realm}"

    if _origin(authorization) != auth_origin or authorization.path.rstrip("/") != expected_authorization_path:
        raise DeploymentValidationError("SSO authorization URL does not match the production Keycloak hostname and realm.")
    if _origin(expected_issuer) != auth_origin or expected_issuer.path.rstrip("/") != expected_issuer_path:
        raise DeploymentValidationError("Expected SSO issuer does not match the production Keycloak hostname and realm.")
    if authorization.query or authorization.fragment or expected_issuer.query or expected_issuer.fragment:
        raise DeploymentValidationError("Production OIDC public URLs must not contain query strings or fragments.")

    for variable, endpoint in OIDC_INTERNAL_ENDPOINTS.items():
        internal_url = _parse_internal_oidc_url(variable, _required(env, variable))
        expected_path = f"/realms/{expected_realm}/protocol/openid-connect/{endpoint}"
        if internal_url.path.rstrip("/") != expected_path:
            raise DeploymentValidationError(f"{variable} does not use the production Keycloak realm.")

    return auth_host


def _validate_meeting_urls(env: Mapping[str, str]) -> str:
    public_url = _parse_public_origin("PUBLIC_URL", _required(env, "PUBLIC_URL"))
    template = _required(env, "APP_MEETINGS_TOKEN_JOIN_URL_TEMPLATE")
    _reject_placeholder("APP_MEETINGS_TOKEN_JOIN_URL_TEMPLATE", template, allow_format_placeholders=True)
    if template.count("%s") != 2 or "#jwt=%s" not in template or "?jwt=%s" in template:
        raise DeploymentValidationError(
            "APP_MEETINGS_TOKEN_JOIN_URL_TEMPLATE must contain one room placeholder and a JWT URL-fragment placeholder."
        )
    resolved_template = template.replace("%s", "preflight", 1).replace("%s", "preflight-token", 1)
    join_url = _parse_https_url("APP_MEETINGS_TOKEN_JOIN_URL_TEMPLATE", resolved_template)
    if join_url.query or join_url.fragment != "jwt=preflight-token":
        raise DeploymentValidationError("Meeting join URL template must place only the JWT in the URL fragment.")
    if _origin(join_url) != _origin(public_url):
        raise DeploymentValidationError("Jitsi public URL and meeting join URL template must use one HTTPS origin.")
    return _hostname(public_url)


def _validate_distinct_hosts(portal_host: str, auth_host: str, meet_host: str) -> None:
    if len({portal_host, auth_host, meet_host}) != 3:
        raise DeploymentValidationError("Portal, Keycloak and Jitsi must use three distinct public hostnames.")


def _validate_jvb_advertise_ips(raw_value: str) -> str:
    _reject_placeholder("JVB_ADVERTISE_IPS", raw_value)
    values = [value.strip() for value in raw_value.split(",") if value.strip()]
    if not values or len(values) > 2 or len(values) != len(set(values)):
        raise DeploymentValidationError(
            "JVB_ADVERTISE_IPS must contain one public IPv4 and at most one distinct RFC1918 LAN IPv4."
        )

    addresses: list[ipaddress.IPv4Address] = []
    for value in values:
        try:
            address = ipaddress.ip_address(value)
        except ValueError as exc:
            raise DeploymentValidationError(
                "JVB_ADVERTISE_IPS must contain valid IPv4 addresses."
            ) from exc
        if not isinstance(address, ipaddress.IPv4Address):
            raise DeploymentValidationError("JVB_ADVERTISE_IPS must contain IPv4 addresses only.")
        addresses.append(address)

    public_addresses = [address for address in addresses if address.is_global]
    lan_addresses = [
        address
        for address in addresses
        if any(address in network for network in RFC1918_NETWORKS)
    ]
    if len(public_addresses) != 1 or len(lan_addresses) > 1:
        raise DeploymentValidationError(
            "JVB_ADVERTISE_IPS must contain exactly one globally routable public IPv4 and at most one RFC1918 LAN IPv4."
        )
    if len(public_addresses) + len(lan_addresses) != len(addresses):
        raise DeploymentValidationError(
            "JVB_ADVERTISE_IPS contains an unsupported non-public address outside RFC1918 LAN ranges."
        )
    return str(public_addresses[0])


def _validate_operator_files(
    env: Mapping[str, str],
    base_dir: Path,
    portal_host: str,
    auth_host: str,
    meet_host: str,
    *,
    enforce_posix_permissions: bool | None,
    validate_certificates: bool,
) -> None:
    for variable in REQUIRED_SECRET_FILES:
        raw_path = _required(env, variable)
        _reject_placeholder(variable, raw_path)
        path = _resolve_operator_path(base_dir, raw_path)
        _reject_example_secret_path(variable, path)
        _validate_non_empty_file(path, variable)
        _validate_private_file_permissions(path, variable, enforce_posix_permissions)

    vault_tls_dir_value = _required(env, "VAULT_TLS_DIR")
    _reject_placeholder("VAULT_TLS_DIR", vault_tls_dir_value)
    vault_tls_dir = _resolve_operator_path(base_dir, vault_tls_dir_value)
    forbidden_ca_key = vault_tls_dir / "ca.key"
    if forbidden_ca_key.exists() or forbidden_ca_key.is_symlink():
        raise DeploymentValidationError(
            "Vault runtime TLS directory must not contain the CA private key."
        )
    for filename in ("ca.crt", "server.crt"):
        _validate_non_empty_file(vault_tls_dir / filename, f"Vault TLS {filename}")
    vault_private_key = vault_tls_dir / "server.key"
    _validate_non_empty_file(vault_private_key, "Vault TLS private key")
    _validate_private_file_permissions(
        vault_private_key,
        "Vault TLS private key",
        enforce_posix_permissions,
    )
    if validate_certificates:
        _validate_certificate(vault_tls_dir / "server.crt", {"vault"}, "Vault server certificate")

    certs_path_value = _required(env, "NGINX_CERTS_PATH")
    _reject_placeholder("NGINX_CERTS_PATH", certs_path_value)
    certs_path = _resolve_operator_path(base_dir, certs_path_value)
    certificate_name = _required(env, "TLS_CERT_NAME")
    if certificate_name in {".", ".."} or "/" in certificate_name or "\\" in certificate_name:
        raise DeploymentValidationError("TLS_CERT_NAME must be one certificate directory name.")
    certificate_dir = certs_path / "live" / certificate_name
    _validate_non_empty_file(
        certificate_dir / "fullchain.pem",
        "TLS fullchain for the portal, identity and meeting hosts",
    )
    private_key = certificate_dir / "privkey.pem"
    _validate_non_empty_file(
        private_key,
        "TLS private key for the portal, identity and meeting hosts",
    )
    _validate_private_file_permissions(
        private_key,
        "TLS private key for the portal, identity and meeting hosts",
        enforce_posix_permissions,
    )
    if validate_certificates:
        _validate_certificate(
            certificate_dir / "fullchain.pem",
            {portal_host, auth_host, meet_host},
            "public edge certificate",
        )


def _validate_certificate(path: Path, required_dns_names: set[str], label: str) -> None:
    try:
        decoded = ssl._ssl._test_decode_cert(str(path))  # type: ignore[attr-defined]
    except (OSError, ssl.SSLError, ValueError) as exc:
        raise DeploymentValidationError(f"{label} cannot be decoded as an X.509 certificate.") from exc
    subject_alt_names = {
        value.lower().rstrip(".")
        for kind, value in decoded.get("subjectAltName", ())
        if kind == "DNS"
    }
    if not required_dns_names.issubset(subject_alt_names):
        raise DeploymentValidationError(f"{label} does not cover every required DNS name.")
    not_after = decoded.get("notAfter")
    if not isinstance(not_after, str):
        raise DeploymentValidationError(f"{label} has no readable expiry time.")
    try:
        expiry = ssl.cert_time_to_seconds(not_after)
    except ValueError as exc:
        raise DeploymentValidationError(f"{label} has an invalid expiry time.") from exc
    if expiry <= time.time() + 30 * 24 * 60 * 60:
        raise DeploymentValidationError(f"{label} expires in less than 30 days.")


def _validate_service_secret_contracts(env: Mapping[str, str], base_dir: Path) -> None:
    parsed_files: dict[str, dict[str, str]] = {}
    for path_variable, required_variables in SERVICE_ENV_REQUIREMENTS.items():
        secret_path = _resolve_operator_path(base_dir, _required(env, path_variable))
        values = parse_env_file(secret_path)
        for secret_variable in required_variables:
            value = _required(values, secret_variable)
            _reject_placeholder(secret_variable, value)
            if len(value.encode("utf-8")) < 24:
                raise DeploymentValidationError(
                    f"{path_variable} contains secret material shorter than the production minimum."
                )
        parsed_files[path_variable] = values

    keycloak_password = parsed_files["KEYCLOAK_VAULT_ENV_FILE_PATH"]["KC_DB_PASSWORD"]
    database_password = parsed_files["KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH"]["POSTGRES_PASSWORD"]
    if keycloak_password != database_password:
        raise DeploymentValidationError(
            "Keycloak and its PostgreSQL service do not share the same database credential."
        )

    web = parsed_files["JITSI_WEB_VAULT_ENV_FILE_PATH"]
    prosody = parsed_files["JITSI_PROSODY_VAULT_ENV_FILE_PATH"]
    jicofo = parsed_files["JITSI_JICOFO_VAULT_ENV_FILE_PATH"]
    jvb = parsed_files["JITSI_JVB_VAULT_ENV_FILE_PATH"]
    if web["JWT_APP_SECRET"] != prosody["JWT_APP_SECRET"]:
        raise DeploymentValidationError("Jitsi Web and Prosody JWT secrets do not match.")
    if len(web["JWT_APP_SECRET"].encode("utf-8")) < 32:
        raise DeploymentValidationError("Jitsi JWT signing secret must be at least 32 bytes.")
    if len({web["JICOFO_AUTH_PASSWORD"], prosody["JICOFO_AUTH_PASSWORD"], jicofo["JICOFO_AUTH_PASSWORD"]}) != 1:
        raise DeploymentValidationError("Jicofo authentication credentials are inconsistent across Jitsi services.")
    if len({web["JVB_AUTH_PASSWORD"], prosody["JVB_AUTH_PASSWORD"], jvb["JVB_AUTH_PASSWORD"]}) != 1:
        raise DeploymentValidationError("JVB authentication credentials are inconsistent across Jitsi services.")


def _validate_realm_import(
    env: Mapping[str, str],
    base_dir: Path,
    expected_realm: str,
    *,
    enforce_posix_permissions: bool | None,
) -> None:
    import_dir = _resolve_operator_path(base_dir, _required(env, "KEYCLOAK_REALM_IMPORT_DIR"))
    realm_file = import_dir / "jitsi-realm.json"
    _validate_non_empty_file(
        realm_file,
        "private production Keycloak realm import",
        check_placeholders=False,
    )
    _validate_private_file_permissions(
        realm_file,
        "private production Keycloak realm import",
        enforce_posix_permissions,
    )
    try:
        realm = json.loads(realm_file.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise DeploymentValidationError("Private production Keycloak realm import is invalid JSON.") from exc
    if not isinstance(realm, dict) or realm.get("realm") != expected_realm:
        raise DeploymentValidationError("Private Keycloak realm import does not target the production realm.")

    clients = realm.get("clients")
    if not isinstance(clients, list):
        raise DeploymentValidationError("Private Keycloak realm import has no client configuration.")
    backend_client = next(
        (client for client in clients if isinstance(client, dict) and client.get("clientId") == "jitsi-backend"),
        None,
    )
    if backend_client is None:
        raise DeploymentValidationError("Private Keycloak realm import has no jitsi-backend client.")
    if backend_client.get("secret") != "${SSO_CLIENT_SECRET}":
        raise DeploymentValidationError("Private Keycloak realm import must retain the runtime OIDC secret placeholder.")
    if backend_client.get("redirectUris") != ["${OIDC_REDIRECT_BASE_URI}/login/oauth2/code/keycloak"]:
        raise DeploymentValidationError("Private Keycloak realm import has an unexpected redirect URI policy.")
    if backend_client.get("webOrigins") != ["${APP_FRONTEND_ORIGIN}"]:
        raise DeploymentValidationError("Private Keycloak realm import has an unexpected web-origin policy.")
    attributes = backend_client.get("attributes")
    if not isinstance(attributes, dict) or attributes.get("post.logout.redirect.uris") != "${APP_FRONTEND_ORIGIN}/auth":
        raise DeploymentValidationError("Private Keycloak realm import has an unexpected post-logout redirect policy.")

    users = realm.get("users")
    if not isinstance(users, list) or not users:
        raise DeploymentValidationError("Private production Keycloak realm import contains no portal users.")
    if not any(
        isinstance(user, dict)
        and user.get("enabled", True)
        and "admin" in user.get("realmRoles", [])
        for user in users
    ):
        raise DeploymentValidationError("Private production Keycloak realm import contains no enabled portal admin.")


def _resolve_operator_path(base_dir: Path, raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    if not path.is_absolute():
        path = base_dir / path
    return path.resolve(strict=False)


def _reject_example_secret_path(variable: str, path: Path) -> None:
    lowered_parts = tuple(part.lower() for part in path.parts)
    if path.name.lower().endswith(".example") or (
        "delivery" in lowered_parts and "examples" in lowered_parts
    ):
        raise DeploymentValidationError(f"{variable} must point to an operator-managed file, not a committed example.")


def _validate_non_empty_file(path: Path, label: str, *, check_placeholders: bool = True) -> None:
    try:
        file_stat = path.stat()
    except OSError as exc:
        raise DeploymentValidationError(f"Required file for {label} is missing or unreadable.") from exc
    if not stat.S_ISREG(file_stat.st_mode) or file_stat.st_size <= 0:
        raise DeploymentValidationError(f"Required file for {label} must be a non-empty regular file.")

    try:
        prefix = path.read_bytes()[:4096].decode("utf-8", errors="ignore").lower()
    except OSError as exc:
        raise DeploymentValidationError(f"Required file for {label} is unreadable.") from exc
    if check_placeholders and any(marker in prefix for marker in PLACEHOLDER_MARKERS):
        raise DeploymentValidationError(f"Required file for {label} still contains placeholder material.")


def _validate_private_file_permissions(
    path: Path,
    label: str,
    enforce_posix_permissions: bool | None,
) -> None:
    enforce = os.name == "posix" if enforce_posix_permissions is None else enforce_posix_permissions
    if not enforce:
        return
    try:
        mode = stat.S_IMODE(path.stat().st_mode)
    except OSError as exc:
        raise DeploymentValidationError(f"Cannot inspect permissions for {label}.") from exc
    if mode & 0o044:
        raise DeploymentValidationError(f"{label} must not be readable by group or other users.")


def resolve_ipv4_addresses(hostname: str) -> set[str]:
    try:
        results = socket.getaddrinfo(hostname, 443, family=socket.AF_INET, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise DeploymentValidationError(f"DNS A lookup failed for public host {hostname}.") from exc
    return {result[4][0] for result in results}


def _validate_dns(hosts: tuple[str, str, str], public_ip: str, resolver: DnsResolver) -> None:
    for host in hosts:
        addresses = resolver(host)
        if not addresses:
            raise DeploymentValidationError(f"DNS A lookup returned no addresses for public host {host}.")
        if addresses != {public_ip}:
            raise DeploymentValidationError(
                f"DNS A records for public host {host} must resolve only to the public JVB advertise address."
            )


def _parse_public_origin(variable: str, value: str) -> SplitResult:
    parsed = _parse_https_url(variable, value)
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise DeploymentValidationError(f"{variable} must be an HTTPS origin without path, query or fragment.")
    return parsed


def _parse_https_url(variable: str, value: str) -> SplitResult:
    _reject_placeholder(variable, value)
    parsed = _split_url(variable, value)
    if parsed.scheme.lower() != "https":
        raise DeploymentValidationError(f"{variable} must use HTTPS.")
    _validate_public_authority(variable, parsed)
    return parsed


def _parse_internal_oidc_url(variable: str, value: str) -> SplitResult:
    _reject_placeholder(variable, value)
    parsed = _split_url(variable, value)
    if parsed.scheme.lower() not in {"http", "https"}:
        raise DeploymentValidationError(f"{variable} must use HTTP or HTTPS on the private identity network.")
    if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise DeploymentValidationError(f"{variable} is not a valid private OIDC endpoint URL.")
    return parsed


def _split_url(variable: str, value: str) -> SplitResult:
    try:
        return urlsplit(value)
    except ValueError as exc:
        raise DeploymentValidationError(f"{variable} is not a valid URL.") from exc


def _validate_public_authority(variable: str, parsed: SplitResult) -> None:
    if not parsed.hostname or parsed.username or parsed.password:
        raise DeploymentValidationError(f"{variable} must contain a public hostname without userinfo.")
    try:
        port = parsed.port
    except ValueError as exc:
        raise DeploymentValidationError(f"{variable} contains an invalid port.") from exc
    if port not in (None, 443):
        raise DeploymentValidationError(f"{variable} must use the standard HTTPS port.")
    hostname = parsed.hostname.lower().rstrip(".")
    if any(part in hostname for part in PLACEHOLDER_HOST_PARTS) or hostname.endswith((".invalid", ".local", ".test")):
        raise DeploymentValidationError(f"{variable} contains a non-production hostname.")
    try:
        ipaddress.ip_address(hostname)
    except ValueError:
        if "." not in hostname:
            raise DeploymentValidationError(f"{variable} must contain a fully qualified public hostname.")
    else:
        raise DeploymentValidationError(f"{variable} must use a public hostname rather than a literal IP address.")


def _required(env: Mapping[str, str], variable: str) -> str:
    value = env.get(variable, "").strip()
    if not value:
        raise DeploymentValidationError(f"Required production variable {variable} is missing or empty.")
    return value


def _reject_placeholder(variable: str, value: str, *, allow_format_placeholders: bool = False) -> None:
    normalized = value.lower()
    if any(marker in normalized for marker in PLACEHOLDER_MARKERS):
        raise DeploymentValidationError(f"{variable} still contains a placeholder value.")
    if not allow_format_placeholders and "%s" in normalized:
        raise DeploymentValidationError(f"{variable} still contains a formatting placeholder.")


def _origin(parsed: SplitResult) -> str:
    host = _hostname(parsed)
    port = f":{parsed.port}" if parsed.port is not None and parsed.port != 443 else ""
    return f"{parsed.scheme.lower()}://{host}{port}"


def _hostname(parsed: SplitResult) -> str:
    if parsed.hostname is None:
        raise DeploymentValidationError("Validated URL unexpectedly has no hostname.")
    return parsed.hostname.lower().rstrip(".")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fail-closed validation of operator-provided production deployment inputs.",
        allow_abbrev=False,
    )
    parser.add_argument("--env-file", required=True, type=Path, help="Path to the actual production env file.")
    parser.add_argument(
        "--skip-dns",
        action="store_true",
        help="Skip live DNS A-record checks for staged/offline validation only.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        validate_deployment(args.env_file, skip_dns=args.skip_dns)
    except DeploymentValidationError as exc:
        print(f"validate-production-deployment: FAILED: {exc}", file=sys.stderr)
        return 1

    dns_status = "skipped by operator" if args.skip_dns else "matched to the JVB public IPv4 address"
    print("validate-production-deployment: OK")
    print("validate-production-deployment: verified production URL, realm, secret-file and TLS-file invariants")
    print(f"validate-production-deployment: DNS validation {dns_status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
