# Private host overrides

Этот каталог зарезервирован для локальных private override файлов.

- Реальные operator CIDR allowlists держать в локальном файле `operator-allowlist.local.txt`.
- Реальные usernames, SSH public keys, hostnames и recovery notes не коммитить.
- Использовать этот каталог только как private input к шаблонам из `deploy/host/`.