# Ubuntu 24 host control plane baseline

Этот каталог задает repo-kept baseline для Story 18.3 и дополняет production perimeter source of truth, а не заменяет его.

## Scope и source of truth

- `docker-compose.production.yml`, `docker-compose.production.monitoring.yml`, `deploy/nginx/portal.conf.example`, `backend/src/main/resources/application-prod.yml`, `docs/deployment-production.md` и `scripts/validate-production-perimeter.py` остаются perimeter source of truth.
- `deploy/host/` добавляет host/control-plane baseline поверх perimeter baseline: SSH, UFW, operator access model, AppArmor/time sync и evidence retention.
- Этот baseline не переносит scope в Vault policy design, backup/restore redesign или container confinement beyond minimum host policy.

## Canonical artifacts

- `sshd/10-jitsi-production-hardening.conf.example` - snippet для `/etc/ssh/sshd_config.d/`.
- `ufw/ubuntu-24-production.rules.example` - baseline preview/apply workflow для default-deny UFW.
- `operator-access-model.md` - named-admin + sudo model, ownership и break-glass boundaries.
- `journald/journald.conf.d/60-jitsi-host-baseline.conf.example` - retention baseline для journald.
- `../vault/break-glass-runbook.md` - canonical approval path, cleanup и post-use rotation expectations для recovery actions.
- `local/` - private override area для host-specific allowlists и notes, не для коммита реальных IP, usernames или recovery material.

## Required operating posture

- SSH только по ключам; `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PermitRootLogin no`.
- Предпочитаются `ed25519` ключи для новых operator credentials, но shared baseline не должен жестко отключать уже выданные strong keys до плановой ротации.
- UFW работает как default deny inbound baseline с allow only для `80/tcp`, `443/tcp`, `443/udp`, `10000/udp` и `22/tcp` от operator allowlist.
- `443/udp` переносит HTTP/3 QUIC до Nginx. Это отдельный edge path, его нельзя путать с Jitsi media на `10000/udp`.
- AppArmor должен оставаться enabled/enforce posture. Не отключать его ради удобства контейнеров.
- Time sync обязателен для TLS, OIDC и token validity.
- Либо automatic security updates, либо документированный patch window для security fixes. Ad hoc patching не считается baseline.

## Apply and verify

1. Скопировать ssh snippet в `/etc/ssh/sshd_config.d/10-jitsi-production-hardening.conf`.
2. Проверить конфигурацию командой `sshd -t` и только после успешной проверки выполнять reload `ssh.service`.
3. Прогнать allow rules через `ufw --dry-run` по образцу из `ufw/ubuntu-24-production.rules.example`.
4. Default policy команды `ufw reset`, `ufw default deny incoming`, `ufw default allow outgoing` сначала валидировать в disposable container или maintenance console, затем применять live только с консольным доступом и подтвержденным operator allowlist.
5. Убедиться, что `aa-status` показывает loaded profiles и нет baseline-практики полного disable AppArmor.
6. Проверить `timedatectl status` и наличие synchronized system clock.
7. Проверить, что journald retention baseline применен и не допускает disk exhaustion.

## HTTP/3 perimeter and fallback

- Держите TCP `443` открытым вместе с UDP `443`: HTTP/3 дополняет существующий HTTP/2 fallback и HTTP/1.1 path, а не заменяет его.
- Роутер должен пробрасывать public UDP `443` на VM UDP `443`; Docker затем публикует его на rootless Nginx UDP listener на container port `8443`. Public `Alt-Svc` должен рекламировать `:443`, а не внутренний `:8443`.
- Для LAN предпочтителен split DNS, который разрешает все три canonical hostname в LAN-адрес VM. Если внутренние клиенты разрешают public address, NAT hairpin/reflection должен поддерживать UDP `443` наряду с TCP `80/443`.
- Держите `ssl_early_data off`: в портале есть authenticated и state-changing routes, поэтому replayable 0-RTT requests не входят в production contract.
- Начинайте HTTP/3 canary с `Alt-Svc: h3=":443"; ma=300`. Проверьте portal, auth и meet hostname из внешней сети QUIC-capable client и отдельно проверьте HTTP/2 fallback с заблокированным на стороне клиента UDP `443`.
- HTTP/3 rollback должен учитывать client cache: сначала отдайте `Alt-Svc: clear` через оставшийся рабочим TCP edge, подождите не меньше ранее объявленного `ma` и только затем удалите QUIC listener, UDP Docker publication, router DNAT и UFW rule. Не меняйте независимое JVB-правило UDP `10000` при HTTP/3 rollback.

## Container simulation

До появления целевого Ubuntu host можно выполнить partial smoke в Docker:

- `npm run prod:host:baseline:simulate`

Если не нужен npm wrapper, тот же сценарий запускается напрямую:

- `python scripts/run-production-host-baseline-container-smoke.py`

Этот путь поднимает `ubuntu:24.04` container, устанавливает `openssh-server` и `ufw`, затем:

- копирует committed SSH snippet в `/etc/ssh/sshd_config.d/`;
- выполняет `sshd -t` внутри контейнера;
- подставляет test allowlist вместо `YOUR_OPERATOR_CIDR`, исполняет committed `ufw --dry-run` preview для allow rules и отдельно валидирует committed default policy команды в disposable Ubuntu container.

Ограничения simulation path:

- это полезно для syntax/package smoke, но не доказывает реальное host hardening;
- AppArmor, journald retention behavior, `timedatectl`, live UFW enforcement и host audit evidence остаются проверками только на реальном Ubuntu host;
- container run не заменяет финальный smoke checklist из раздела Apply and verify.

## Audit evidence to retain

- SSH login events: `journalctl -u ssh --since "24 hours ago"`.
- sudo use: `journalctl SYSLOG_IDENTIFIER=sudo --since "24 hours ago"`.
- container restarts: `journalctl -u docker --since "24 hours ago"` и `docker ps -a --format "table {{.Names}}\t{{.Status}}"`.
- firewall changes: `journalctl -u ufw --since "24 hours ago"` или equivalent host audit trail.

## Logging and retention

- Security evidence должно храниться достаточно долго для расследования, но с bounded retention.
- Journald retention baseline закреплен отдельным `.conf.example` artifact.
- Syslog forwarding disabled by default, чтобы не дублировать security logs вне journald retention limits. Если нужен внешний shipper через rsyslog/syslog, оформляйте это как documented local override с отдельным учетом retention и disk budget.
- Plaintext secrets, operator tokens, real SSH public keys, real CIDR allowlists и Vault recovery material не должны появляться в этих шаблонах.

## Patch and hardening policy

- Допустимы два baseline варианта: automatic security updates или заранее согласованный patch window с явной operational ownership.
- Любой выбранный вариант должен регулярно обновлять Ubuntu packages и требовать refresh production images по согласованной cadence.
- AppArmor exceptions оформляются как documented local override, а не через полное отключение профилей.
- Любое break-glass использование должно завершаться cleanup и post-use rotation по runbook, а не оставаться скрытым host-side workaround.

## Strongly recommended next steps

fail2ban, VPN-only admin plane, and SSH MFA are strongly recommended next steps, но они не заменяют минимальный baseline этой story.
