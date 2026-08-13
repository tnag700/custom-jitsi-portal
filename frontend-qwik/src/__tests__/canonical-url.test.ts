import { describe, expect, it } from "vitest";
import { resolveCanonicalHref } from "~/lib/shared/security/canonical-url";

describe("resolveCanonicalHref", () => {
  it("omits canonical metadata for bearer invite paths", () => {
    expect(
      resolveCanonicalHref(
        new URL("https://portal.example.test/invite/secret-token?source=mail"),
      ),
    ).toBeNull();
  });

  it.each([
    "/INVITE/secret-token",
    "/%69nvite/secret-token",
    "/%2569nvite/secret-token",
    "//invite/secret-token",
    "///%69nvite/secret-token",
    "/%2Finvite/secret-token",
  ])(
    "omits canonical metadata after router-style normalization for %s",
    (path) => {
      expect(
        resolveCanonicalHref(new URL(`https://portal.example.test${path}`)),
      ).toBeNull();
    },
  );

  it("preserves existing canonical behavior outside invite paths", () => {
    expect(
      resolveCanonicalHref(
        new URL("https://portal.example.test/meetings/?roomId=room-1"),
      ),
    ).toBe("https://portal.example.test/meetings/?roomId=room-1");
  });

  it("omits client-only fragments from canonical metadata", () => {
    expect(
      resolveCanonicalHref(
        new URL(
          "https://portal.example.test/meetings/?roomId=room-1#participant-panel",
        ),
      ),
    ).toBe("https://portal.example.test/meetings/?roomId=room-1");
  });

  it("does not suppress unrelated paths that only share the prefix", () => {
    expect(
      resolveCanonicalHref(new URL("https://portal.example.test/invited")),
    ).toBe("https://portal.example.test/invited");
  });
});
