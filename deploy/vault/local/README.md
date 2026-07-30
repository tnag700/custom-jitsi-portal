# Private Vault overrides

Этот каталог зарезервирован для private rollout notes и локальных operator overrides.

- Не коммитить сюда unseal keys, recovery keys, tokens, hostnames, bastion routes или real audit export paths.
- Не коммитить сюда `role_id`, wrapped `secret_id`, operator OIDC client secret, rendered token sinks или lease dumps.
- Хранить локальные backend/backup handoff files и provider-specific OIDC overrides только вне git.
- Использовать каталог только как локальную рабочую область для environment-specific заметок вне shared baseline.
- Не превращать этот каталог в custody surface для break-glass material, emergency root-shell notes или recovery exports; такие данные живут вне shared local override areas и следуют `../break-glass-runbook.md`.