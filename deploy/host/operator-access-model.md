# Operator access model

## Identity and privilege

- Использовать named admin accounts для каждого оператора. Shared accounts запрещены.
- Операторы получают доступ через `sudo`, а не через прямой root login.
- Do not share the root password. Root credentials не используются как штатный deploy workflow.
- Доступ в `docker` group рассматривать как исключение: docker group grants root-equivalent access и должен выдаваться только ограниченному числу операторов.

## Ownership and permissions baseline

- Директории deployment artifacts, compose bundles и backup scripts должны принадлежать `root:root` или `root:ops-admins` с минимально достаточными правами на чтение.
- Изменения production artifacts в `/opt/jitsi`, `/etc/jitsi`, `/etc/ssh/sshd_config.d`, `/etc/systemd/` и `/etc/docker/` выполняются только через `sudo`.
- Приватные backup/export outputs должны храниться с правами уровня `0640` или строже; каталоги под них - `0750` или строже.
- Обычный deploy operator не должен автоматически получать доступ к break-glass material, backup encryption keys или privileged recovery notes.

## Separation of duties

- Deploy access и доступ к Vault unseal or recovery material разделяются.
- Vault unseal or recovery material хранится вне обычного deploy path и не дублируется в compose/env workflow.
- Break-glass доступ документируется отдельно, выдается ограниченному кругу лиц и используется только для recovery scenarios.
- Если для emergency recovery нужен root shell, это считается break-glass path с отдельным аудитом, а не everyday operation.

## Role separation model

- deploy operator выполняет обычные rollout/deploy действия, но не получает автоматического доступа к recovery material.
- day-2 Vault operator поддерживает audit/auth/policy baseline и не получает implicit recovery entitlements.
- bootstrap/init actor используется только для initial setup или explicit re-bootstrap event и не считается routine day-2 operator role.
- recovery actor выполняет break-glass действия по отдельному approval path и evidence contract из `../vault/break-glass-runbook.md`.

## Review checklist

- Есть персональный admin account для каждого оператора.
- В SSH authorized_keys находятся только индивидуальные ключи операторов.
- В sudoers нет blanket `NOPASSWD: ALL` без отдельного решения и аудита.
- Реальные usernames, SSH public keys и recovery artifacts не коммитятся в репозиторий.
