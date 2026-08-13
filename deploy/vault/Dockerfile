FROM alpine:3.22.5@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce AS vault-fetch

ARG VAULT_VERSION=1.21.4
ARG VAULT_ZIP_URL=https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_linux_amd64.zip
ARG VAULT_SHA256SUMS_URL=https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_SHA256SUMS

RUN apk add --no-cache ca-certificates coreutils curl grep unzip \
    && curl -fsSL "$VAULT_SHA256SUMS_URL" -o /tmp/vault_SHA256SUMS \
    && curl -fsSL "$VAULT_ZIP_URL" -o "/tmp/vault_${VAULT_VERSION}_linux_amd64.zip" \
    && cd /tmp \
    && grep " vault_${VAULT_VERSION}_linux_amd64.zip$" /tmp/vault_SHA256SUMS | sha256sum -c - \
    && unzip "/tmp/vault_${VAULT_VERSION}_linux_amd64.zip" -d /out \
    && chmod 0755 /out/vault

FROM alpine:3.22.5@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce

RUN apk add --no-cache ca-certificates \
    && mkdir -p /vault/config /vault/bootstrap /vault/policies /vault/tls /vault/data /vault/audit /vault/operator /vault/recovery

COPY --from=vault-fetch /out/vault /usr/local/bin/vault

ENTRYPOINT ["vault"]
