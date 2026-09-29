FROM alpine:3.22.6@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8

ARG VAULT_VERSION=2.1.1
ARG VAULT_ZIP_URL=https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/2.1.1/vault_2.1.1_linux_amd64.zip
COPY vault_2.1.1_linux_amd64.sha256 /tmp/vault.sha256

RUN apk add --no-cache ca-certificates \
    && apk add --no-cache --virtual .vault-fetch coreutils curl unzip \
    && curl -fsSL "$VAULT_ZIP_URL" -o "/tmp/vault_${VAULT_VERSION}_linux_amd64.zip" \
    && cd /tmp \
    && sha256sum -c /tmp/vault.sha256 \
    && unzip "/tmp/vault_${VAULT_VERSION}_linux_amd64.zip" vault -d /usr/local/bin \
    && chmod 0755 /usr/local/bin/vault \
    && rm -f "/tmp/vault_${VAULT_VERSION}_linux_amd64.zip" /tmp/vault.sha256 \
    && apk del .vault-fetch \
    && mkdir -p /vault/config /vault/bootstrap /vault/policies /vault/tls /vault/data /vault/audit /vault/operator /vault/recovery

ENTRYPOINT ["vault"]
