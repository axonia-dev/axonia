import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  escapeHtmlAttribute,
  openMessageInNewTab,
} from "@/components/patterns/open-message-in-tab";

describe("escapeHtmlAttribute", () => {
  it("escapes the four attribute-breaking characters", () => {
    expect(escapeHtmlAttribute(`<b>"quoted" & <i>`)).toBe(
      "&lt;b&gt;&quot;quoted&quot; &amp; &lt;i&gt;",
    );
  });

  it("escapes a bare ampersand exactly once, never double-escaping it", () => {
    // `<` and `"` both expand into entities that themselves contain `&`
    // (`&lt;`, `&quot;`). If `&` were not escaped first, a later `&` pass over
    // those entities would double-escape them into `&amp;lt;` / `&amp;quot;`.
    // This input only proves the ordering because it contains characters that
    // produce those entities - a bare "Q&A" would pass either way.
    expect(escapeHtmlAttribute('Q&A <b>"x"</b>')).toBe(
      "Q&amp;A &lt;b&gt;&quot;x&quot;&lt;/b&gt;",
    );
  });
});

describe("openMessageInNewTab", () => {
  let openSpy: MockInstance<typeof window.open>;
  let revokeObjectUrlSpy: MockInstance<typeof URL.revokeObjectURL>;
  let capturedBlob: Blob | undefined;

  beforeEach(() => {
    vi.useFakeTimers();
    capturedBlob = undefined;
    // Per the HTML spec, passing "noopener" makes `window.open` return `null`
    // unconditionally - whether the tab opened, was blocked, or anything else.
    // `null` is what a real browser returns here, including on success, so it
    // is the only return value worth mocking.
    openSpy = vi.spyOn(window, "open").mockReturnValue(null);
    vi.spyOn(URL, "createObjectURL").mockImplementation((blob) => {
      capturedBlob = blob as Blob;
      return "blob:mock-url";
    });
    revokeObjectUrlSpy = vi
      .spyOn(URL, "revokeObjectURL")
      .mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  async function capturedWrapperHtml(): Promise<string> {
    if (!capturedBlob) {
      throw new Error("no blob was passed to URL.createObjectURL");
    }
    return capturedBlob.text();
  }

  it("opens a same-origin wrapper tab, never the mail HTML itself as a blob: navigation", () => {
    // when
    openMessageInNewTab("Wrap-up", "<p>Thanks</p>");

    // then
    expect(openSpy).toHaveBeenCalledWith("blob:mock-url", "_blank", "noopener");
    expect(capturedBlob?.type).toBe("text/html");
  });

  it("carries the untrusted HTML only inside an empty-sandbox srcdoc iframe", async () => {
    // when
    openMessageInNewTab("Wrap-up", '<script>alert("hi")</script>');

    // then
    const wrapper = await capturedWrapperHtml();
    expect(wrapper).toContain('<iframe sandbox="" srcdoc="');
    // The raw markup never appears unescaped in the outer document...
    expect(wrapper).not.toContain('<script>alert("hi")</script>');
    // ...it is present only as its escaped form, inside the srcdoc attribute.
    expect(wrapper).toContain(
      "&lt;script&gt;alert(&quot;hi&quot;)&lt;/script&gt;",
    );
  });

  it("names the tab after the subject in its title, also escaped", async () => {
    // when
    openMessageInNewTab("Q&A <digest>", "<p>Body</p>");

    // then
    const wrapper = await capturedWrapperHtml();
    expect(wrapper).toContain("<title>Q&amp;A &lt;digest&gt;</title>");
  });

  it("always revokes the object URL after the deferral window, never branching on window.open's return value", async () => {
    // given - `window.open` returns `null` here (see beforeEach): with "noopener"
    // that is what a real browser returns whether the tab opened or was
    // blocked, so the two are indistinguishable and must be handled identically -
    // an earlier version treated `null` as "blocked" and revoked immediately,
    // killing the blob before a successfully opened tab could load it.
    // when
    expect(() => openMessageInNewTab("Wrap-up", "<p>Thanks</p>")).not.toThrow();

    // then
    expect(revokeObjectUrlSpy).not.toHaveBeenCalled();
    await vi.runAllTimersAsync();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith("blob:mock-url");
  });
});
