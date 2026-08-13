from __future__ import annotations

import importlib.util
import json
import shutil
import unittest
from pathlib import Path
from uuid import uuid4


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "merge-keycloak-production-users.py"
SPEC = importlib.util.spec_from_file_location("merge_keycloak_production_users", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Cannot load Keycloak production user migration")
MIGRATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MIGRATION)


class KeycloakProductionUserMigrationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path.cwd() / f".realm-migration-test-{uuid4().hex}"
        self.root.mkdir()
        self.production = self.root / "production.json"
        self.development = self.root / "development.json"
        self.output = self.root / "private" / "jitsi-realm.json"
        self.production.write_text(
            json.dumps(
                {
                    "realm": "jitsi",
                    "roles": {"realm": [{"name": "admin"}, {"name": "participant"}]},
                    "clients": [
                        {
                            "clientId": "jitsi-backend",
                            "secret": "${SSO_CLIENT_SECRET}",
                            "redirectUris": ["${OIDC_REDIRECT_BASE_URI}/login/oauth2/code/keycloak"],
                        }
                    ],
                    "users": [],
                }
            ),
            encoding="utf-8",
        )
        self.development.write_text(
            json.dumps(
                {
                    "realm": "jitsi-dev",
                    "clients": [{"clientId": "development-only"}],
                    "users": [
                        {
                            "username": "admin",
                            "enabled": True,
                            "realmRoles": ["admin", "default-roles-jitsi-dev"],
                            "credentials": [{"type": "password", "secretData": "private-hash"}],
                        },
                        {
                            "username": "participant",
                            "enabled": True,
                            "realmRoles": ["participant"],
                        },
                    ],
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        shutil.rmtree(self.root)

    def test_merges_only_users_into_reviewed_production_policy(self) -> None:
        count = MIGRATION.merge_users(self.production, self.development, self.output)
        result = json.loads(self.output.read_text(encoding="utf-8"))

        self.assertEqual(count, 2)
        self.assertEqual(result["realm"], "jitsi")
        self.assertEqual(result["clients"][0]["clientId"], "jitsi-backend")
        self.assertNotIn("development-only", json.dumps(result["clients"]))
        self.assertEqual(result["users"][0]["realmRoles"], ["admin"])
        self.assertEqual(result["users"][0]["credentials"][0]["secretData"], "private-hash")

    def test_requires_enabled_admin_and_refuses_overwrite(self) -> None:
        development = json.loads(self.development.read_text(encoding="utf-8"))
        development["users"][0]["enabled"] = False
        self.development.write_text(json.dumps(development), encoding="utf-8")
        with self.assertRaisesRegex(MIGRATION.RealmMigrationError, "enabled portal admin"):
            MIGRATION.merge_users(self.production, self.development, self.output)

        development["users"][0]["enabled"] = True
        self.development.write_text(json.dumps(development), encoding="utf-8")
        MIGRATION.merge_users(self.production, self.development, self.output)
        with self.assertRaisesRegex(MIGRATION.RealmMigrationError, "refusing to overwrite"):
            MIGRATION.merge_users(self.production, self.development, self.output)


if __name__ == "__main__":
    unittest.main()
