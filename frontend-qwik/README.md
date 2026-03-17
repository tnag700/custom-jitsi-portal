# Frontend Package

Этот пакет не является основным входом для разработчика монорепозитория. Обычная работа должна идти из корня проекта через root-скрипты в [package.json](../package.json).

Канонические команды:

```shell
npm run frontend:install
npm run frontend:dev
```

Для локальной production-проверки frontend:

```shell
npm run frontend:build
npm run frontend:start
```

Для production-like контейнерного запуска всего проекта:

```shell
npm run prod:up
```

Команды в этом package.json нужны в основном как внутренний контракт frontend-пакета, для Docker build и для точечных локальных проверок.

Если вам нужен общий сценарий запуска, ориентируйтесь на корневой README: [README.md](../README.md).
