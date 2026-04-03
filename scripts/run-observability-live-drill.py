from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import http.cookiejar
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone


def write_step(message: str) -> None:
    print(f"==> {message}")


def convert_to_base64_url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def new_hs256_jwt(payload: dict[str, object], secret: str) -> str:
    header_json = json.dumps({"typ": "JWT", "alg": "HS256"}, separators=(",", ":"))
    payload_json = json.dumps(payload, separators=(",", ":"))
    encoded_header = convert_to_base64_url(header_json.encode("utf-8"))
    encoded_payload = convert_to_base64_url(payload_json.encode("utf-8"))
    unsigned_token = f"{encoded_header}.{encoded_payload}"
    signature = hmac.new(secret.encode("utf-8"), unsigned_token.encode("utf-8"), hashlib.sha256).digest()
    return f"{unsigned_token}.{convert_to_base64_url(signature)}"


def merge_dicts(base: dict[str, str], extra: dict[str, str]) -> dict[str, str]:
    merged = dict(base)
    merged.update(extra)
    return merged


@dataclass
class JsonResponse:
    status_code: int
    body: object | None
    raw_body: str
    final_url: str


@dataclass
class TextResponse:
    status_code: int
    final_url: str
    body: str
    set_cookie_headers: list[str]


class Session:
    def __init__(self) -> None:
        self.cookie_jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookie_jar))

    def request(
        self,
        uri: str,
        *,
        method: str = "GET",
        headers: dict[str, str] | None = None,
        body: object | None = None,
        follow_redirects: bool = True,
    ) -> JsonResponse:
        request_headers = dict(headers or {})
        data = None
        if body is not None:
            request_headers.setdefault("Content-Type", "application/json")
            data = json.dumps(body, separators=(",", ":")).encode("utf-8")

        request = urllib.request.Request(uri, data=data, method=method)
        for key, value in request_headers.items():
            request.add_header(key, value)

        opener = self.opener if follow_redirects else urllib.request.build_opener(urllib.request.HTTPHandler(), urllib.request.HTTPCookieProcessor(self.cookie_jar))
        try:
            with opener.open(request) as response:
                raw_body = response.read().decode("utf-8")
                final_url = response.geturl()
                status_code = response.status
        except urllib.error.HTTPError as error:
            raw_body = error.read().decode("utf-8")
            final_url = error.geturl()
            status_code = error.code

        parsed_body: object | None = None
        if raw_body.strip():
            try:
                parsed_body = json.loads(raw_body)
            except json.JSONDecodeError:
                parsed_body = raw_body

        return JsonResponse(status_code=status_code, body=parsed_body, raw_body=raw_body, final_url=final_url)

    def text_request(self, uri: str, *, method: str = "GET", headers: dict[str, str] | None = None, data: bytes | None = None) -> TextResponse:
        request = urllib.request.Request(uri, data=data, method=method)
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        try:
            with self.opener.open(request) as response:
                return TextResponse(
                    status_code=response.status,
                    final_url=response.geturl(),
                    body=response.read().decode("utf-8"),
                    set_cookie_headers=list(response.headers.get_all("Set-Cookie") or []),
                )
        except urllib.error.HTTPError as error:
            return TextResponse(
                status_code=error.code,
                final_url=error.geturl(),
                body=error.read().decode("utf-8"),
                set_cookie_headers=list(error.headers.get_all("Set-Cookie") or []),
            )


def get_keycloak_login_action(html: str, base_url: str) -> str:
    match = re.search(r'id="kc-form-login"[\s\S]*?action="([^"]+)"', html)
    if match is None:
        raise RuntimeError("Не удалось найти action формы логина Keycloak.")
    action = urllib.parse.unquote(match.group(1).replace("&amp;", "&"))
    if action.startswith("/"):
        return f"{base_url.rstrip('/')}{action}"
    return action


def build_localhost_cookie_header(session: Session, uri: str, set_cookie_headers: list[str]) -> str:
    parsed_uri = urllib.parse.urlparse(uri)
    host = parsed_uri.hostname or ""
    path = parsed_uri.path or "/"
    if parsed_uri.scheme != "http" or host not in {"localhost", "127.0.0.1"}:
        return ""

    cookies: dict[str, str] = {}
    for cookie in session.cookie_jar:
        domain = cookie.domain.lstrip(".")
        if domain not in {"", host, f"{host}.local", "localhost", "localhost.local", "127.0.0.1"} and not host.endswith(f".{domain}"):
            continue
        cookie_path = cookie.path or "/"
        if not path.startswith(cookie_path):
            continue
        cookies[cookie.name] = cookie.value

    for header in set_cookie_headers:
        match = re.match(r"\s*([^=;\s]+)=([^;]*)", header)
        if match is not None:
            cookies[match.group(1)] = match.group(2)

    return "; ".join(f"{name}={value}" for name, value in cookies.items())


def new_trace_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex}"


def new_idempotency_key(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex}"


def invoke_json_request(session: Session, uri: str, *, method: str = "GET", headers: dict[str, str] | None = None, body: object | None = None) -> JsonResponse:
    return session.request(uri, method=method, headers=headers, body=body)


def get_prometheus_json(url: str) -> object:
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode("utf-8"))


def wait_for_prometheus_api(prometheus_url: str, timeout_seconds: int = 90, poll_interval_seconds: int = 5) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"{prometheus_url}/-/ready"):
                return
        except Exception:
            time.sleep(poll_interval_seconds)
    raise RuntimeError(f"Prometheus API не стало ready за {timeout_seconds} секунд.")


def get_prometheus_metric(prometheus_url: str, query: str) -> object:
    encoded_query = urllib.parse.quote(query, safe="")
    return get_prometheus_json(f"{prometheus_url}/api/v1/query?query={encoded_query}")


def get_prometheus_value(metric_response: object) -> str:
    if not isinstance(metric_response, dict):
        return "0"
    data = metric_response.get("data")
    if not isinstance(data, dict):
        return "0"
    result = data.get("result")
    if not isinstance(result, list) or not result:
        return "0"
    value = result[0].get("value") if isinstance(result[0], dict) else None
    if not isinstance(value, list) or len(value) < 2:
        return "0"
    return str(value[1])


def invoke_alert_receiver_request(alert_receiver_url: str, path: str, *, method: str = "GET") -> object:
    request = urllib.request.Request(f"{alert_receiver_url}{path}", method=method)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        if error.code >= 400:
            raise RuntimeError(f"Alert receiver request failed for {path}: {error.code} {raw}") from error
    return json.loads(raw) if raw.strip() else None


def get_alertmanager_alerts(alertmanager_url: str) -> list[dict[str, object]]:
    result = get_prometheus_json(f"{alertmanager_url}/api/v2/alerts")
    return result if isinstance(result, list) else []


def find_alert_notification(notifications: list[object], expected_alert_name: str, expected_status: str) -> object | None:
    for notification in notifications:
        if not isinstance(notification, dict):
            continue
        if notification.get("status") == expected_status and expected_alert_name in list(notification.get("alertNames") or []):
            return notification
    return None


def wait_for_alert_notification(alert_receiver_url: str, expected_alert_name: str, expected_status: str, timeout_seconds: int, poll_interval_seconds: int) -> dict[str, object]:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        response = invoke_alert_receiver_request(alert_receiver_url, "/notifications")
        notifications = response.get("notifications", []) if isinstance(response, dict) else []
        notification = find_alert_notification(list(notifications), expected_alert_name, expected_status)
        if isinstance(notification, dict):
            return notification
        time.sleep(poll_interval_seconds)
    raise RuntimeError(f"Не удалось дождаться {expected_status} notification для alert {expected_alert_name} через {timeout_seconds} секунд.")


def wait_for_alertmanager_state(alertmanager_url: str, expected_alert_name: str, timeout_seconds: int, poll_interval_seconds: int) -> dict[str, object]:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        for alert in get_alertmanager_alerts(alertmanager_url):
            labels = alert.get("labels", {}) if isinstance(alert, dict) else {}
            status = alert.get("status", {}) if isinstance(alert, dict) else {}
            if isinstance(labels, dict) and labels.get("alertname") == expected_alert_name and isinstance(status, dict) and status.get("state") == "active":
                return alert
        time.sleep(poll_interval_seconds)
    raise RuntimeError(f"Не удалось дождаться firing state в Alertmanager для alert {expected_alert_name} через {timeout_seconds} секунд.")


def wait_for_alertmanager_clear(alertmanager_url: str, expected_alert_name: str, timeout_seconds: int, poll_interval_seconds: int) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        active = False
        for alert in get_alertmanager_alerts(alertmanager_url):
            labels = alert.get("labels", {}) if isinstance(alert, dict) else {}
            status = alert.get("status", {}) if isinstance(alert, dict) else {}
            if isinstance(labels, dict) and labels.get("alertname") == expected_alert_name and isinstance(status, dict) and status.get("state") == "active":
                active = True
                break
        if not active:
            return
        time.sleep(poll_interval_seconds)
    raise RuntimeError(f"Alertmanager не снял firing state для alert {expected_alert_name} за {timeout_seconds} секунд.")


def invoke_join_request(session: Session, backend_url: str, meeting_id: str, expected_status_code: int, trace_prefix: str, headers: dict[str, str]) -> JsonResponse:
    response = invoke_json_request(
        session,
        f"{backend_url}/api/v1/meetings/{meeting_id}/access-token",
        method="POST",
        headers=merge_dicts(headers, {"X-Trace-Id": new_trace_id(trace_prefix)}),
    )
    if response.status_code != expected_status_code:
        raise RuntimeError(f"Join request for {meeting_id} returned unexpected status {response.status_code}: {response.raw_body}")
    return response


def invoke_refresh_pair(session: Session, backend_url: str, meeting_id: str, user_id: str, headers: dict[str, str], signing_secret: str, frontend_url: str) -> dict[str, JsonResponse]:
    now = datetime.now(timezone.utc)
    refresh_token = new_hs256_jwt(
        {
            "iss": frontend_url,
            "aud": "jitsi-meet",
            "sub": user_id,
            "iat": int(now.timestamp()),
            "exp": int((now + timedelta(hours=2)).timestamp()),
            "jti": str(uuid.uuid4()),
            "tokenType": "refresh",
            "meetingId": meeting_id,
        },
        signing_secret,
    )

    refresh_success = invoke_json_request(
        session,
        f"{backend_url}/api/v1/auth/refresh",
        method="POST",
        headers=merge_dicts(headers, {"X-Trace-Id": new_trace_id("trace-refresh-success")}),
        body={"refreshToken": refresh_token},
    )
    if refresh_success.status_code != 200:
        raise RuntimeError(f"Refresh success path завершился со статусом {refresh_success.status_code}: {refresh_success.raw_body}")

    refresh_reuse = invoke_json_request(
        session,
        f"{backend_url}/api/v1/auth/refresh",
        method="POST",
        headers=merge_dicts(headers, {"X-Trace-Id": new_trace_id("trace-refresh-reuse")}),
        body={"refreshToken": refresh_token},
    )
    if refresh_reuse.status_code != 409:
        raise RuntimeError(f"Refresh reuse path вернул неожиданный статус {refresh_reuse.status_code}: {refresh_reuse.raw_body}")

    return {"success": refresh_success, "reuse": refresh_reuse}


def remove_observability_artifacts(session: Session, backend_url: str, meeting_id: str | None, room_id: str | None, headers: dict[str, str]) -> dict[str, object]:
    cleanup = {"attempted": False, "meetingCanceled": False, "roomDeleted": False}
    if not meeting_id and not room_id:
        return cleanup

    cleanup["attempted"] = True
    if meeting_id:
        cancel_response = invoke_json_request(
            session,
            f"{backend_url}/api/v1/meetings/{meeting_id}/cancel",
            method="POST",
            headers=merge_dicts(headers, {"X-Trace-Id": new_trace_id("trace-cleanup-meeting")}),
        )
        if cancel_response.status_code not in {200, 404, 409}:
            raise RuntimeError(f"Cleanup cancel for meeting {meeting_id} returned unexpected status {cancel_response.status_code}: {cancel_response.raw_body}")
        cleanup["meetingCanceled"] = cancel_response.status_code in {200, 409}

    if room_id:
        delete_response = invoke_json_request(
            session,
            f"{backend_url}/api/v1/rooms/{room_id}",
            method="DELETE",
            headers=merge_dicts(headers, {"X-Trace-Id": new_trace_id("trace-cleanup-room")}),
        )
        if delete_response.status_code not in {204, 404}:
            raise RuntimeError(f"Cleanup delete for room {room_id} returned unexpected status {delete_response.status_code}: {delete_response.raw_body}")
        cleanup["roomDeleted"] = delete_response.status_code == 204

    return cleanup


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("-FrontendUrl", dest="frontend_url", default="http://localhost:3000")
    parser.add_argument("-BackendUrl", dest="backend_url", default="http://localhost:8080")
    parser.add_argument("-KeycloakBaseUrl", dest="keycloak_base_url", default="http://localhost:8081")
    parser.add_argument("-PrometheusUrl", dest="prometheus_url", default="http://localhost:9090")
    parser.add_argument("-GrafanaUrl", dest="grafana_url", default="http://localhost:3001")
    parser.add_argument("-AlertmanagerUrl", dest="alertmanager_url", default="http://localhost:9093")
    parser.add_argument("-AlertReceiverUrl", dest="alert_receiver_url", default="http://localhost:9080")
    parser.add_argument("-Username", dest="username", default="dev-admin")
    parser.add_argument("-Password", dest="password", default="dev-admin-pass")
    parser.add_argument("-TenantId", dest="tenant_id", default="tenant-1")
    parser.add_argument("-ConfigSetId", dest="config_set_id", default="config-1")
    parser.add_argument("-WaitForAlertState", dest="wait_for_alert_state", choices=["none", "firing", "resolved", "cycle"], default="none")
    parser.add_argument("-AlertName", dest="alert_name", default="JitsiAuthRefreshReuseSpike")
    parser.add_argument("-AlertWarmupSeconds", dest="alert_warmup_seconds", type=int, default=20)
    parser.add_argument("-AlertPollIntervalSeconds", dest="alert_poll_interval_seconds", type=int, default=5)
    parser.add_argument("-AlertWaitTimeoutSeconds", dest="alert_wait_timeout_seconds", type=int, default=180)
    parser.add_argument("-QuietWindowSeconds", dest="quiet_window_seconds", type=int, default=135)
    parser.add_argument("-PrometheusWaitSeconds", dest="prometheus_wait_seconds", type=int, default=20)
    parser.add_argument("-Cycles", dest="cycles", type=int, default=1)
    parser.add_argument("-CycleIntervalSeconds", dest="cycle_interval_seconds", type=int, default=0)
    parser.add_argument("-JoinSuccessRequestsPerCycle", dest="join_success_requests_per_cycle", type=int, default=1)
    parser.add_argument("-JoinFailureRequestsPerCycle", dest="join_failure_requests_per_cycle", type=int, default=1)
    parser.add_argument("-RefreshPairsPerCycle", dest="refresh_pairs_per_cycle", type=int, default=1)
    parser.add_argument("-KeepArtifacts", dest="keep_artifacts", action="store_true")
    parser.add_argument("-SigningSecret", dest="signing_secret", default="")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    signing_secret = args.signing_secret or os.environ.get("APP_MEETINGS_TOKEN_SIGNING_SECRET") or "01234567890123456789012345678901"
    session = Session()
    base_headers: dict[str, str] = {}
    created_room_id: str | None = None
    created_meeting_id: str | None = None
    cleanup_result = {"attempted": False, "meetingCanceled": False, "roomDeleted": False}
    cleanup_completed = False

    try:
        write_step("Logging in through Keycloak")
        if args.wait_for_alert_state != "none":
            write_step("Resetting mock alert receiver before smoke verification")
            invoke_alert_receiver_request(args.alert_receiver_url, "/notifications", method="DELETE")

        login_page = session.text_request(f"{args.backend_url}/api/v1/auth/login")
        login_action = get_keycloak_login_action(login_page.body, args.keycloak_base_url)
        login_form_body = urllib.parse.urlencode(
            {
                "username": args.username,
                "password": args.password,
                "credentialId": "",
                "login": "Sign In",
            }
        ).encode("utf-8")
        login_headers = {"Content-Type": "application/x-www-form-urlencoded"}
        localhost_cookie_header = build_localhost_cookie_header(session, login_action, login_page.set_cookie_headers)
        if localhost_cookie_header:
            login_headers["Cookie"] = localhost_cookie_header
        login_response = session.text_request(
            login_action,
            method="POST",
            headers=login_headers,
            data=login_form_body,
        )
        if login_response.status_code != 200 or not login_response.final_url.startswith(args.frontend_url):
            raise RuntimeError(
                f"Логин через Keycloak завершился неожиданно: status={login_response.status_code} url={login_response.final_url}"
            )

        write_step("Loading authenticated profile")
        me = invoke_json_request(session, f"{args.backend_url}/api/v1/auth/me").body
        csrf = invoke_json_request(session, f"{args.backend_url}/api/v1/auth/csrf").body
        if not isinstance(me, dict) or not isinstance(csrf, dict):
            raise RuntimeError("Не удалось получить профиль пользователя или CSRF token.")
        base_headers[str(csrf["headerName"])] = str(csrf["token"])

        write_step("Creating room, meeting, and participant assignment")
        room_response = invoke_json_request(
            session,
            f"{args.backend_url}/api/v1/rooms",
            method="POST",
            headers=merge_dicts(base_headers, {"X-Trace-Id": new_trace_id("trace-room"), "Idempotency-Key": new_idempotency_key("room")}),
            body={
                "name": f"Observability Live Room {datetime.now().strftime('%Y%m%d-%H%M%S')}",
                "description": "Live metrics drill",
                "tenantId": args.tenant_id,
                "configSetId": args.config_set_id,
            },
        )
        if room_response.status_code != 201 or not isinstance(room_response.body, dict):
            raise RuntimeError(f"Создание room завершилось со статусом {room_response.status_code}: {room_response.raw_body}")
        created_room_id = str(room_response.body["roomId"])

        starts_at = (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat().replace("+00:00", "Z")
        ends_at = (datetime.now(timezone.utc) + timedelta(minutes=65)).isoformat().replace("+00:00", "Z")
        meeting_response = invoke_json_request(
            session,
            f"{args.backend_url}/api/v1/rooms/{created_room_id}/meetings",
            method="POST",
            headers=merge_dicts(base_headers, {"X-Trace-Id": new_trace_id("trace-meeting"), "Idempotency-Key": new_idempotency_key("meeting")}),
            body={
                "title": "Observability Drill",
                "description": "Synthetic flow for metrics",
                "meetingType": "instant",
                "startsAt": starts_at,
                "endsAt": ends_at,
                "allowGuests": True,
                "recordingEnabled": False,
            },
        )
        if meeting_response.status_code != 201 or not isinstance(meeting_response.body, dict):
            raise RuntimeError(f"Создание meeting завершилось со статусом {meeting_response.status_code}: {meeting_response.raw_body}")
        created_meeting_id = str(meeting_response.body["meetingId"])

        assignment_response = invoke_json_request(
            session,
            f"{args.backend_url}/api/v1/meetings/{created_meeting_id}/participants",
            method="POST",
            headers=merge_dicts(base_headers, {"X-Trace-Id": new_trace_id("trace-assignment")}),
            body={"subjectId": me["id"], "role": "host"},
        )
        if assignment_response.status_code != 201:
            raise RuntimeError(f"Назначение participant завершилось со статусом {assignment_response.status_code}: {assignment_response.raw_body}")

        write_step("Generating traffic over time")
        join_success_count = join_failure_count = refresh_success_count = refresh_reuse_count = 0
        last_join_success_status = last_join_failure_status = last_refresh_success_status = last_refresh_reuse_status = None
        requires_refresh_reuse_alert_pacing = (
            args.wait_for_alert_state != "none" and args.alert_name == "JitsiAuthRefreshReuseSpike"
        )
        effective_refresh_pairs_per_cycle = (
            max(args.refresh_pairs_per_cycle, 3) if requires_refresh_reuse_alert_pacing else args.refresh_pairs_per_cycle
        )
        refresh_pair_spacing_seconds = max(args.alert_poll_interval_seconds, 16) if requires_refresh_reuse_alert_pacing else 0

        if requires_refresh_reuse_alert_pacing:
            write_step(
                "Pacing refresh reuse traffic across Prometheus scrapes so the alert rule can observe counter increments"
            )

        for cycle in range(1, args.cycles + 1):
            write_step(f"Traffic cycle {cycle}/{args.cycles}")
            for _ in range(args.join_success_requests_per_cycle):
                join_success_response = invoke_join_request(session, args.backend_url, created_meeting_id, 200, "trace-join-success", base_headers)
                join_success_count += 1
                last_join_success_status = join_success_response.status_code
            for _ in range(args.join_failure_requests_per_cycle):
                join_failure_response = invoke_join_request(session, args.backend_url, "meeting-does-not-exist", 404, "trace-join-fail", base_headers)
                join_failure_count += 1
                last_join_failure_status = join_failure_response.status_code
            for refresh_pair_index in range(effective_refresh_pairs_per_cycle):
                refresh_result = invoke_refresh_pair(session, args.backend_url, created_meeting_id, str(me["id"]), base_headers, signing_secret, args.frontend_url)
                refresh_success_count += 1
                refresh_reuse_count += 1
                last_refresh_success_status = refresh_result["success"].status_code
                last_refresh_reuse_status = refresh_result["reuse"].status_code
                if refresh_pair_spacing_seconds > 0 and refresh_pair_index < effective_refresh_pairs_per_cycle - 1:
                    time.sleep(refresh_pair_spacing_seconds)
            if cycle < args.cycles and args.cycle_interval_seconds > 0:
                time.sleep(args.cycle_interval_seconds)

        write_step("Waiting for Prometheus scrape")
        time.sleep(args.prometheus_wait_seconds)
        wait_for_prometheus_api(args.prometheus_url)

        write_step("Collecting Prometheus metrics")
        join_attempts = get_prometheus_metric(args.prometheus_url, "jitsi_join_attempts_total")
        join_success = get_prometheus_metric(args.prometheus_url, "jitsi_join_success_total")
        join_failure = get_prometheus_metric(args.prometheus_url, 'jitsi_join_failure_total{error_code="MEETING_NOT_FOUND"}')
        join_latency_success = get_prometheus_metric(args.prometheus_url, 'jitsi_join_latency_seconds_count{result="success"}')
        join_latency_fail = get_prometheus_metric(args.prometheus_url, 'jitsi_join_latency_seconds_count{result="fail"}')
        refresh_reuse = get_prometheus_metric(args.prometheus_url, 'jitsi_auth_refresh_events_total{event_type="refresh_reuse"}')
        refresh_issued = get_prometheus_metric(args.prometheus_url, 'jitsi_auth_refresh_events_total{event_type="token_issued"}')
        refresh_token_refreshed = get_prometheus_metric(args.prometheus_url, 'jitsi_auth_refresh_events_total{event_type="token_refreshed"}')

        alert_state = firing_notification = resolved_notification = None
        if args.wait_for_alert_state != "none":
            if args.alert_warmup_seconds > 0:
                write_step("Waiting for Prometheus rule evaluation warm-up")
                time.sleep(args.alert_warmup_seconds)
            write_step(f"Waiting for Alertmanager firing state for {args.alert_name}")
            alert_state = wait_for_alertmanager_state(args.alertmanager_url, args.alert_name, args.alert_wait_timeout_seconds, args.alert_poll_interval_seconds)
            write_step(f"Waiting for firing notification delivery for {args.alert_name}")
            firing_notification = wait_for_alert_notification(args.alert_receiver_url, args.alert_name, "firing", args.alert_wait_timeout_seconds, args.alert_poll_interval_seconds)

        if args.wait_for_alert_state in {"resolved", "cycle"}:
            write_step("Waiting quiet window for resolved notification")
            time.sleep(args.quiet_window_seconds)
            write_step(f"Waiting for Alertmanager to clear firing state for {args.alert_name}")
            wait_for_alertmanager_clear(args.alertmanager_url, args.alert_name, args.alert_wait_timeout_seconds, args.alert_poll_interval_seconds)
            write_step(f"Waiting for resolved notification delivery for {args.alert_name}")
            resolved_notification = wait_for_alert_notification(args.alert_receiver_url, args.alert_name, "resolved", args.alert_wait_timeout_seconds, args.alert_poll_interval_seconds)

        if not args.keep_artifacts:
            write_step("Cleaning up synthetic room and meeting")
            cleanup_result = remove_observability_artifacts(session, args.backend_url, created_meeting_id, created_room_id, base_headers)
            cleanup_completed = True

        result = {
            "user": me.get("displayName"),
            "userId": me.get("id"),
            "tenant": me.get("tenant"),
            "roomId": created_room_id,
            "meetingId": created_meeting_id,
            "trafficPlan": {
                "cycles": args.cycles,
                "cycleIntervalSeconds": args.cycle_interval_seconds,
                "joinSuccessRequestsPerCycle": args.join_success_requests_per_cycle,
                "joinFailureRequestsPerCycle": args.join_failure_requests_per_cycle,
                "refreshPairsPerCycle": effective_refresh_pairs_per_cycle,
                "requestedRefreshPairsPerCycle": args.refresh_pairs_per_cycle,
                "prometheusWaitSeconds": args.prometheus_wait_seconds,
            },
            "requestSummary": {
                "joinSuccessCompleted": join_success_count,
                "joinFailureCompleted": join_failure_count,
                "refreshSuccessCompleted": refresh_success_count,
                "refreshReuseCompleted": refresh_reuse_count,
                "totalRequestsCompleted": join_success_count + join_failure_count + refresh_success_count + refresh_reuse_count,
            },
            "lastStatuses": {
                "joinSuccessStatus": last_join_success_status,
                "joinFailureStatus": last_join_failure_status,
                "refreshSuccessStatus": last_refresh_success_status,
                "refreshReuseStatus": last_refresh_reuse_status,
            },
            "metrics": {
                "join_attempts_total": get_prometheus_value(join_attempts),
                "join_success_total": get_prometheus_value(join_success),
                "join_failure_meeting_not_found": get_prometheus_value(join_failure),
                "join_latency_success_count": get_prometheus_value(join_latency_success),
                "join_latency_fail_count": get_prometheus_value(join_latency_fail),
                "auth_refresh_reuse": get_prometheus_value(refresh_reuse),
                "auth_refresh_token_issued": get_prometheus_value(refresh_issued),
                "auth_refresh_token_refreshed": get_prometheus_value(refresh_token_refreshed),
            },
            "dashboards": {
                "join_and_errors": f"{args.grafana_url}/d/jitsi-join-errors/join-and-errors",
                "service_health": f"{args.grafana_url}/d/jitsi-service-health/service-health",
            },
            "alerts": {
                "alertmanagerUrl": args.alertmanager_url,
                "receiverUrl": args.alert_receiver_url,
                "waitForAlertState": args.wait_for_alert_state,
                "alertName": args.alert_name,
                "firingObserved": firing_notification is not None,
                "resolvedObserved": resolved_notification is not None,
                "lastAlertmanagerState": (alert_state.get("status") or {}).get("state") if isinstance(alert_state, dict) else None,
                "firingNotificationReceivedAt": firing_notification.get("receivedAt") if isinstance(firing_notification, dict) else None,
                "resolvedNotificationReceivedAt": resolved_notification.get("receivedAt") if isinstance(resolved_notification, dict) else None,
            },
            "cleanup": {
                "keepArtifacts": bool(args.keep_artifacts),
                "attempted": cleanup_result["attempted"],
                "meetingCanceled": cleanup_result["meetingCanceled"],
                "roomDeleted": cleanup_result["roomDeleted"],
            },
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        if not args.keep_artifacts and not cleanup_completed:
            try:
                cleanup_result = remove_observability_artifacts(session, args.backend_url, created_meeting_id, created_room_id, base_headers)
            except Exception as exc:
                print(f"Cleanup of observability artifacts failed: {exc}", file=sys.stderr)


if __name__ == "__main__":
    main()
