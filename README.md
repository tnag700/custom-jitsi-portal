# Custom Jitsi Portal

Веб-портал для управления комнатами и встречами Jitsi с единым входом через Keycloak. Разворачивается на собственном сервере с помощью Docker Compose.

> Проект находится в активной разработке. Для production нужны отдельная конфигурация и проверка реального звонка из внешней сети.

## Возможности

- Комнаты и встречи с ролями организатора, модератора и участника.
- Вход через Keycloak, профили пользователей и каталог в пределах организации.
- Гостевые приглашения со сроком действия, лимитом использований и отзывом.
- Администрирование пользователей, ролей и конфигураций окружений.
- Журнал изменений и проверка закреплённых версий на известные уязвимости через OSV.
- Вход в Jitsi по подписанному JWT, метрики и оповещения.

## Стек

| Компонент | Технологии |
| --- | --- |
| Backend | Java 25, Spring Boot, Spring Modulith |
| Frontend | Qwik SSR, TypeScript, Tailwind CSS |
| Данные | PostgreSQL, Redis, Flyway |
| Вход и видеосвязь | Keycloak, Jitsi Meet |
| Инфраструктура | Nginx, Vault, Prometheus, Alertmanager, Grafana |

Версии закреплены в [backend/build.gradle](backend/build.gradle), [frontend-qwik/package.json](frontend-qwik/package.json) и файлах Docker Compose.

## Локальный запуск

Потребуются:

- Docker Engine с плагином Docker Compose;
- Node.js `>=24.21.0 <25` и npm;
- Python 3, доступный как `python`;
- JDK 25 для локальной сборки backend и полного набора проверок.

Из PowerShell:

```powershell
git clone https://github.com/tnag700/custom-jitsi-portal.git
Set-Location custom-jitsi-portal
npm ci
npm --prefix frontend-qwik ci
Copy-Item .env.example .env
npm run stack:up
```

В Linux/macOS используйте `cd` вместо `Set-Location` и `cp` вместо `Copy-Item`. Файл `.env` содержит локальные настройки и не коммитится.

`stack:up` проверяет конфигурацию, подготавливает локальный Vault и запускает контейнеры. Остановка — `Ctrl+C`, удаление контейнеров и сети с сохранением данных — `npm run stack:down`.

| Сервис | Адрес по умолчанию |
| --- | --- |
| Портал | <http://localhost:3000> |
| Backend API | <http://localhost:8080/api/v1> |
| Swagger UI | <http://localhost:8082> |
| Keycloak | <http://localhost:8081> |
| Jitsi | <http://localhost:8000> / <https://localhost:8443> |

Мониторинг запускается командой `npm run stack:up:monitoring`.

## Разработка и проверки

Команды выполняются из корня репозитория:

| Команда | Назначение |
| --- | --- |
| `npm run frontend:dev` | Сервер разработки frontend |
| `npm run frontend:build` | Сборка frontend |
| `npm run frontend:verify:ssr` | Сборка и проверка SSR |
| `npm run contracts:check` | Проверка OpenAPI и frontend-типов |
| `npm run verify` | Тесты, сборка, статический анализ и проверка конфигураций |

Для backend перейдите в каталог `backend` и выполните `.\gradlew.bat test` в Windows или `./gradlew test` в Linux/macOS. Тесты на Testcontainers требуют работающий Docker.

## Production

Развёртывание описано в [production-инструкции](docs/deployment-production.md). Подготовьте DNS, доверенные TLS-сертификаты, NAT/firewall, секреты и резервные копии с проверяемым откатом. Dev-конфигурацию, тестовых пользователей и локальные секреты в production не используйте; секреты и приватные ключи храните вне Git.

По умолчанию наружу публикуются `80/tcp` и `443/tcp` для веб-доступа; `443/udp` — HTTP/3 QUIC; `10000/udp` — медиатрафик Jitsi Videobridge. Для доступа из LAN нужен split DNS или корректный NAT hairpin.

После запуска проверьте вход через Keycloak и звонок минимум с тремя участниками, включая LAN и внешнюю сеть. Успешный `npm run verify` и health checks не подтверждают прохождение аудио и видео через NAT.

<details>
<summary>Проверки production и управление секретами</summary>

```sh
npm run prod:host:baseline:validate
npm run prod:secret:baseline:validate
npm run prod:secret:auth:validate
```

Файлы окружения содержат только non-secret config и path hints; frontend SSR по умолчанию не становится Vault client. Vault собирается по [deploy/vault/Dockerfile](deploy/vault/Dockerfile), закрепляющему approved Yandex mirror artifact path и проверку контрольной суммы.

Ротация описана в [матрице управления секретами](deploy/vault/secret-governance-matrix.md), аварийный доступ — в [break-glass инструкции](deploy/vault/break-glass-runbook.md).

HTTP/3 вводится с `Alt-Svc: h3=":443"; ma=300` и сохранением HTTP/2 fallback. При откате сначала отдайте `Alt-Svc: clear` на всех трёх hostname и выждите ранее объявленный `ma`, затем закрывайте UDP `443`. Порт JVB UDP `10000` не меняется.

</details>

## Документация

- [Развёртывание в production](docs/deployment-production.md)
- [Эксплуатация и диагностика](docs/runbook.md)
- [Роли и права доступа](docs/access-control.md)
- [Модель угроз](docs/threat-model.md)
- [Vault и управление секретами](deploy/vault/README.md)
- [Графы архитектуры и аудит кода](docs/code-graph-audit.md)
- [Мониторинг версий и уязвимостей](docs/framework-version-monitoring.md)
