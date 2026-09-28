import { describe, expect, it } from "vitest";
import { pageHref, readPage } from "../lib/shared/routes/page-query";

describe("page query", () => {
  it("keeps independent page and selection parameters in navigation links", () => {
    expect(pageHref(
      "https://portal.example.test/meetings?roomId=r21&meetingsPage=2&invitesPage=1",
      "roomsPage", 3,
    )).toBe("/meetings?roomId=r21&meetingsPage=2&invitesPage=1&roomsPage=3");
  });

  it("uses the first page for malformed or unsafe page numbers", () => {
    expect(readPage(new URLSearchParams("roomsPage=-1"), "roomsPage")).toBe(0);
    expect(readPage(new URLSearchParams("roomsPage=abc"), "roomsPage")).toBe(0);
    expect(readPage(new URLSearchParams("roomsPage=999999999999999999"), "roomsPage")).toBe(0);
  });
});
