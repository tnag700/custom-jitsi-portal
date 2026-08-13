import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

function readWorkspaceFile(path: string): string {
  return readFileSync(resolve(process.cwd(), "..", path), "utf8");
}

describe("Jitsi web access policy", () => {
  it("redirects the development welcome and close pages to the configured portal", () => {
    const source = readWorkspaceFile("docker-compose.yml");

    expect(source).toContain("jitsi-web-portal-access:");
    expect(source).toContain("target: /config/nginx/custom-meet.conf");
    expect(source).toContain("location = / {");
    expect(source).toContain("location = /static/close.html {");
    expect(source).toContain("location = /static/close2.html {");
    expect(source).toContain("return 302 $$portal_return_url;");
    expect(source).toContain(
      'test: ["CMD", "wget", "-qO-", "http://localhost/config.js"]',
    );
  });

  it("mounts the production access policy from a file compatible with a read-only rootfs", () => {
    const compose = readWorkspaceFile("docker-compose.production.yml");
    const policy = readWorkspaceFile(
      "pilot/jitsi/web/custom-meet.production.conf",
    );

    expect(compose).toContain("jitsi-web-portal-access:");
    expect(compose).toContain(
      "file: ./pilot/jitsi/web/custom-meet.production.conf",
    );
    expect(compose).toContain("target: /config/nginx/custom-meet.conf");
    expect(compose).not.toContain("jitsi-web-portal-access:\n    content:");
    expect(policy).toContain('set $portal_return_url "https://jitsi-mgorka.top";');
    expect(policy).toContain("location = / {");
    expect(policy).toContain("location = /static/close.html {");
    expect(policy).toContain("location = /static/close2.html {");
    expect(policy).toContain("return 302 $portal_return_url;");
    expect(compose).toContain(
      'test: ["CMD", "wget", "-qO-", "http://localhost:8000/config.js"]',
    );
  });

  it("redirects room HTML without a portal-issued JWT", () => {
    const source = readWorkspaceFile("pilot/jitsi/web/custom-config.js");

    expect(source).toContain("config.portalReturnUrl");
    expect(source).toContain(
      "new URLSearchParams(window.location.hash.slice(1))",
    );
    expect(source).toContain('fragment.has("jwt")');
    expect(source).toContain("window.location.replace(portalUrl.toString())");
  });

  it("keeps Jitsi authentication fail-closed in every compose baseline", () => {
    for (const composeFile of [
      "docker-compose.yml",
      "docker-compose.production.yml",
    ]) {
      const source = readWorkspaceFile(composeFile);

      expect(source).toContain("JWT_ALLOW_EMPTY=0");
      expect(source).toContain("ENABLE_GUESTS=0");
      expect(source).toContain("AUTH_TYPE=jwt");
    }
  });

  it("uses one issuer contract for portal tokens and Jitsi validation", () => {
    const devCompose = readWorkspaceFile("docker-compose.yml");
    const productionCompose = readWorkspaceFile(
      "docker-compose.production.yml",
    );

    expect(
      devCompose.match(
        /(?:APP_MEETINGS_TOKEN_ISSUER|JWT_APP_ID|JWT_ACCEPTED_ISSUERS)=\$\{DEV_PORTAL_ORIGIN:-http:\/\/localhost:3000\}/g,
      ),
    ).toHaveLength(5);
    expect(
      productionCompose.match(
        /(?:APP_MEETINGS_TOKEN_ISSUER|JWT_APP_ID|JWT_ACCEPTED_ISSUERS)=\$\{APP_MEETINGS_TOKEN_ISSUER:\?Set APP_MEETINGS_TOKEN_ISSUER\}/g,
      ),
    ).toHaveLength(5);
  });
});
