# Changelog - 2026-03-16

## Frontend security headers

- Moved browser-facing document CSP enforcement to the Qwik SSR route layer via `plugin@csp.ts`.
- Added centralized CSP/header builder utilities for nonce generation, document-route filtering, and localhost API origin resolution.
- Updated the document CSP so Qwik SSR inline style tags are allowed with `style-src 'self' 'unsafe-inline'` while scripts remain nonce-protected with `strict-dynamic`.
- Relaxed `Permissions-Policy` for same-origin `camera` and `microphone` access required by join preflight checks.

## Frontend hardening tests

- Added runtime tests for document security headers and root shell security guarantees.
- Extended migration guard coverage to assert the route-plugin CSP path and `@nonce` propagation.
- Removed the inline bootstrap script from the root document shell.

## Container packaging

- Switched frontend Docker packaging to reuse host-built runtime artifacts instead of running `npm ci` in-container.
- Switched backend Docker packaging to reuse the host-built application JAR instead of downloading Gradle inside the image build.

## Validation

- Verified frontend security tests pass.
- Verified the production frontend build succeeds.
- Verified the live frontend now serves `style-src 'self' 'unsafe-inline'` without a style nonce.