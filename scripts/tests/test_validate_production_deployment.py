from __future__ import annotations

import importlib.util
import json
import os
import shutil
import stat
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock
from uuid import uuid4


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "validate-production-deployment.py"
SPEC = importlib.util.spec_from_file_location("validate_production_deployment", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Cannot load production deployment validator")
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class ProductionDeploymentValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path.cwd() / f".validator-test-{uuid4().hex}"
        self.root.mkdir()
        self.secret_paths = {
            variable: self.root / "operator" / f"{variable.lower()}.secret"
            for variable in VALIDATOR.REQUIRED_SECRET_FILES
        }
        shared = {
            "postgres": "P" * 32,
            "redis": "R" * 32,
            "keycloak_db": "K" * 32,
            "keycloak_admin": "A" * 32,
            "oidc": "O" * 32,
            "jwt": "J" * 32,
            "jicofo": "F" * 32,
            "jvb": "V" * 32,
            "component": "C" * 32,
        }
        service_file_contents = {
            "POSTGRES_VAULT_ENV_FILE_PATH": f"POSTGRES_PASSWORD={shared['postgres']}\n",
            "REDIS_VAULT_ENV_FILE_PATH": f"REDIS_PASSWORD={shared['redis']}\n",
            "KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH": f"POSTGRES_PASSWORD={shared['keycloak_db']}\n",
            "KEYCLOAK_VAULT_ENV_FILE_PATH": (
                f"KC_BOOTSTRAP_ADMIN_PASSWORD={shared['keycloak_admin']}\n"
                f"KC_DB_PASSWORD={shared['keycloak_db']}\n"
                f"SSO_CLIENT_SECRET={shared['oidc']}\n"
            ),
            "JITSI_WEB_VAULT_ENV_FILE_PATH": (
                f"JWT_APP_SECRET={shared['jwt']}\n"
                f"JICOFO_AUTH_PASSWORD={shared['jicofo']}\n"
                f"JVB_AUTH_PASSWORD={shared['jvb']}\n"
            ),
            "JITSI_PROSODY_VAULT_ENV_FILE_PATH": (
                f"JWT_APP_SECRET={shared['jwt']}\n"
                f"JICOFO_AUTH_PASSWORD={shared['jicofo']}\n"
                f"JVB_AUTH_PASSWORD={shared['jvb']}\n"
            ),
            "JITSI_JICOFO_VAULT_ENV_FILE_PATH": (
                f"JICOFO_AUTH_PASSWORD={shared['jicofo']}\n"
                f"JICOFO_COMPONENT_SECRET={shared['component']}\n"
            ),
            "JITSI_JVB_VAULT_ENV_FILE_PATH": f"JVB_AUTH_PASSWORD={shared['jvb']}\n",
        }
        for variable, path in self.secret_paths.items():
            self._write_private_file(
                path,
                service_file_contents.get(variable, "operator-private-material"),
            )

        self.certs_path = self.root / "certs"
        self._write_private_file(
            self.certs_path / "live" / "portal.acme.org" / "fullchain.pem",
            "certificate-material",
        )
        self._write_private_file(
            self.certs_path / "live" / "portal.acme.org" / "privkey.pem",
            "private-key-material",
        )
        self.vault_tls_dir = self.root / "vault-tls"
        for filename in ("ca.crt", "server.crt", "server.key"):
            self._write_private_file(self.vault_tls_dir / filename, "vault-tls-material")
        self.realm_import_dir = self.root / "keycloak" / "realm-import"
        self._write_private_file(
            self.realm_import_dir / "jitsi-realm.json",
            json.dumps(
                {
                    "realm": "jitsi",
                    "clients": [
                        {
                            "clientId": "jitsi-backend",
                            "secret": "${SSO_CLIENT_SECRET}",
                            "redirectUris": ["${OIDC_REDIRECT_BASE_URI}/login/oauth2/code/keycloak"],
                            "webOrigins": ["${APP_FRONTEND_ORIGIN}"],
                            "attributes": {
                                "post.logout.redirect.uris": "${APP_FRONTEND_ORIGIN}/auth"
                            },
                        }
                    ],
                    "users": [
                        {"username": "admin", "enabled": True, "realmRoles": ["admin"]}
                    ],
                }
            ),
        )

    def tearDown(self) -> None:
        shutil.rmtree(self.root)

    def test_accepts_consistent_operator_configuration_offline(self) -> None:
        env_file = self._write_env()

        VALIDATOR.validate_deployment(
            env_file,
            skip_dns=True,
            expected_realm="jitsi",
            enforce_posix_permissions=os.name == "posix",
            validate_certificates=False,
        )

    def test_rejects_http_public_url(self) -> None:
        env_file = self._write_env(APP_FRONTEND_ORIGIN="http://portal.acme.org")

        self._assert_validation_error(env_file, "APP_FRONTEND_ORIGIN must use HTTPS")

    def test_rejects_malformed_public_url_without_traceback(self) -> None:
        env_file = self._write_env(APP_FRONTEND_ORIGIN="https://portal.acme.org:not-a-port")

        self._assert_validation_error(env_file, "APP_FRONTEND_ORIGIN contains an invalid port")

    def test_rejects_realm_mismatch_in_public_or_internal_oidc_url(self) -> None:
        env_file = self._write_env(
            APP_SECURITY_SSO_EXPECTED_ISSUER="https://auth.acme.org/realms/jitsi-dev"
        )

        self._assert_validation_error(env_file, "production Keycloak hostname and realm")

        env_file = self._write_env(
            SSO_TOKEN_URI="http://keycloak:8080/realms/jitsi-dev/protocol/openid-connect/token"
        )
        self._assert_validation_error(env_file, "production Keycloak realm")

    def test_rejects_inconsistent_public_host_mapping(self) -> None:
        env_file = self._write_env(PUBLIC_URL="https://conference.acme.org")

        self._assert_validation_error(env_file, "one HTTPS origin")

    def test_rejects_private_or_documentation_jvb_address(self) -> None:
        for address in ("10.10.100.29", "203.0.113.10"):
            with self.subTest(address=address):
                env_file = self._write_env(JVB_ADVERTISE_IPS=address)
                self._assert_validation_error(env_file, "JVB_ADVERTISE_IPS")

    def test_accepts_split_horizon_jvb_addresses(self) -> None:
        env_file = self._write_env(JVB_ADVERTISE_IPS="10.10.100.29,8.8.8.8")

        VALIDATOR.validate_deployment(
            env_file,
            skip_dns=True,
            expected_realm="jitsi",
            enforce_posix_permissions=os.name == "posix",
            validate_certificates=False,
        )

    def test_split_horizon_dns_matches_only_the_public_jvb_address(self) -> None:
        env_file = self._write_env(JVB_ADVERTISE_IPS="10.10.100.29,8.8.8.8")
        resolutions = {
            "portal.acme.org": {"8.8.8.8"},
            "auth.acme.org": {"8.8.8.8"},
            "meet.acme.org": {"8.8.8.8"},
        }

        VALIDATOR.validate_deployment(
            env_file,
            resolver=lambda hostname: resolutions[hostname],
            expected_realm="jitsi",
            enforce_posix_permissions=os.name == "posix",
            validate_certificates=False,
        )

    def test_rejects_ambiguous_jvb_address_sets(self) -> None:
        for addresses in (
            "10.10.100.29,10.10.100.30,8.8.8.8",
            "8.8.8.8,1.1.1.1",
            "10.10.100.29,10.10.100.29,8.8.8.8",
        ):
            with self.subTest(addresses=addresses):
                env_file = self._write_env(JVB_ADVERTISE_IPS=addresses)
                self._assert_validation_error(env_file, "JVB_ADVERTISE_IPS")

    def test_rejects_committed_example_and_placeholder_secret_files(self) -> None:
        env_file = self._write_env(
            POSTGRES_VAULT_ENV_FILE_PATH="deploy/vault/delivery/examples/postgres.env.example"
        )
        self._assert_validation_error(env_file, "operator-managed file")

        self._write_private_file(
            self.secret_paths["POSTGRES_VAULT_ENV_FILE_PATH"],
            "SET_IN_VAULT_RENDERED_FILE_ONLY",
        )
        env_file = self._write_env()
        self._assert_validation_error(env_file, "placeholder material")

    def test_rejects_group_or_world_readable_private_files_on_posix(self) -> None:
        exposed_path = self.secret_paths["REDIS_VAULT_ENV_FILE_PATH"]
        exposed_mode = stat.S_IFREG | 0o644
        with mock.patch.object(
            VALIDATOR.Path,
            "stat",
            return_value=SimpleNamespace(st_mode=exposed_mode),
        ):
            with self.assertRaisesRegex(
                VALIDATOR.DeploymentValidationError,
                "must not be readable by group or other users",
            ):
                VALIDATOR._validate_private_file_permissions(
                    exposed_path,
                    "test private file",
                    True,
                )

    def test_rejects_ca_private_key_in_runtime_vault_tls_directory(self) -> None:
        self._write_private_file(self.vault_tls_dir / "ca.key", "offline-ca-private-key")
        env_file = self._write_env()

        self._assert_validation_error(
            env_file,
            "Vault runtime TLS directory must not contain the CA private key",
        )

    def test_dns_requires_all_hosts_to_match_jvb_public_ip(self) -> None:
        env_file = self._write_env()
        resolutions = {
            "portal.acme.org": {"8.8.8.8"},
            "auth.acme.org": {"8.8.8.8"},
            "meet.acme.org": {"1.1.1.1"},
        }

        with self.assertRaisesRegex(
            VALIDATOR.DeploymentValidationError,
            "DNS A records for public host meet.acme.org",
        ):
            VALIDATOR.validate_deployment(
                env_file,
                resolver=lambda hostname: resolutions[hostname],
                expected_realm="jitsi",
                enforce_posix_permissions=os.name == "posix",
                validate_certificates=False,
            )

    def test_errors_do_not_echo_secret_file_contents(self) -> None:
        secret_marker = "replace-with-ultra-sensitive-material"
        self._write_private_file(
            self.secret_paths["KEYCLOAK_VAULT_ENV_FILE_PATH"],
            secret_marker,
        )
        env_file = self._write_env()

        with self.assertRaises(VALIDATOR.DeploymentValidationError) as raised:
            VALIDATOR.validate_deployment(
                env_file,
                skip_dns=True,
                expected_realm="jitsi",
                enforce_posix_permissions=os.name == "posix",
                validate_certificates=False,
            )
        self.assertNotIn(secret_marker, str(raised.exception))

    def test_rejects_inconsistent_cross_service_credentials(self) -> None:
        self._write_private_file(
            self.secret_paths["KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH"],
            f"POSTGRES_PASSWORD={'X' * 32}\n",
        )
        env_file = self._write_env()
        self._assert_validation_error(env_file, "do not share the same database credential")

    def test_rejects_meetings_service_outside_portal_api(self) -> None:
        env_file = self._write_env(APP_MEETINGS_SERVICE_URL="https://meet.acme.org/api/v1")
        self._assert_validation_error(env_file, "portal HTTPS origin and the /api/v1 path")

    def _write_env(self, **overrides: str) -> Path:
        values = {
            "APP_FRONTEND_ORIGIN": "https://portal.acme.org",
            "APP_OPENAPI_SERVER_URL": "https://portal.acme.org",
            "APP_SECURITY_JWT_ISSUER": "https://portal.acme.org",
            "APP_MEETINGS_TOKEN_ISSUER": "https://portal.acme.org",
            "APP_MEETINGS_SERVICE_URL": "https://portal.acme.org/api/v1",
            "APP_MEETINGS_TOKEN_JOIN_URL_TEMPLATE": "https://meet.acme.org/%s#jwt=%s",
            "SSO_AUTHORIZATION_URI": "https://auth.acme.org/realms/jitsi/protocol/openid-connect/auth",
            "SSO_TOKEN_URI": "http://keycloak:8080/realms/jitsi/protocol/openid-connect/token",
            "SSO_JWK_SET_URI": "http://keycloak:8080/realms/jitsi/protocol/openid-connect/certs",
            "SSO_USER_INFO_URI": "http://keycloak:8080/realms/jitsi/protocol/openid-connect/userinfo",
            "APP_SECURITY_SSO_EXPECTED_ISSUER": "https://auth.acme.org/realms/jitsi",
            "KC_HOSTNAME": "https://auth.acme.org",
            "PUBLIC_URL": "https://meet.acme.org",
            "JVB_ADVERTISE_IPS": "8.8.8.8",
            "NGINX_CERTS_PATH": str(self.certs_path),
            "TLS_CERT_NAME": "portal.acme.org",
            "VAULT_TLS_DIR": str(self.vault_tls_dir),
            "KEYCLOAK_REALM_IMPORT_DIR": str(self.realm_import_dir),
        }
        values.update({key: str(path) for key, path in self.secret_paths.items()})
        values.update(overrides)
        env_file = self.root / "production.env"
        env_file.write_text(
            "\n".join(f"{key}={value}" for key, value in values.items()) + "\n",
            encoding="utf-8",
        )
        return env_file

    def _write_private_file(self, path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        if os.name == "posix":
            os.chmod(path, 0o600)

    def _assert_validation_error(
        self,
        env_file: Path,
        expected_message: str,
        *,
        enforce_posix_permissions: bool | None = None,
    ) -> None:
        with self.assertRaisesRegex(VALIDATOR.DeploymentValidationError, expected_message):
            VALIDATOR.validate_deployment(
                env_file,
                skip_dns=True,
                expected_realm="jitsi",
                enforce_posix_permissions=(
                    os.name == "posix"
                    if enforce_posix_permissions is None
                    else enforce_posix_permissions
                ),
                validate_certificates=False,
            )


if __name__ == "__main__":
    unittest.main()
