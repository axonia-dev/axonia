import { describe, expect, it } from "vitest";

import { classLists, withoutComments } from "@/test/source-files";

describe("withoutComments", () => {
  it("keeps a class list on a line that also holds a URL", () => {
    // given
    const source = `<a href="https://x.example" className="divide-y">`;

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toContain("divide-y");
  });

  it("drops a comment, whole line or trailing", () => {
    // given
    const source = `// "divide-y"\nconst a = 1; // "divide-y"\n/* "divide-x" */`;

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toEqual([]);
  });

  it("reads past a block comment opener that sits inside a line comment", () => {
    // given
    const source = `// legacy /admin/* paths\n<div className="divide-y" />;\n/** x */`;

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toEqual(["divide-y"]);
  });

  it("keeps a string holding a line comment opener, and a template holding a block one", () => {
    // given
    const source =
      'const a = "a // b";\nconst glob = `/${first}/*`;\n<i className="divide-y" />;';

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toContain("a // b");
    expect(actual).toContain("divide-y");
  });

  it("keeps JSX text that reads like a comment", () => {
    // given
    const source = `<p>See https://axonia.example.com // for more</p>`;

    // when
    const actual = withoutComments(source, "module.tsx");

    // then
    expect(actual).toBe(source);
  });

  it("keeps every line break, so a line number still points at the same code", () => {
    // given
    const source = `/**\n * doc\n */\nconst a = 1;`;

    // when
    const actual = withoutComments(source, "module.tsx");

    // then
    expect(actual.split("\n")).toHaveLength(4);
    expect(actual.split("\n")[3]).toBe("const a = 1;");
  });

  it("keeps JSX text that opens with a line comment opener right after a tag", () => {
    // given
    const source = `<p> // x <b className="divide-y" /></p>;`;

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toEqual(["divide-y"]);
  });

  it("drops a comment just before a literal or alone in a JSX expression", () => {
    // given
    const source = `f(/* "divide-x" */ "divide-y");\n<p>{/* "divide-x" */}</p>;`;

    // when
    const actual = classLists(withoutComments(source, "module.tsx"));

    // then
    expect(actual).toEqual(["divide-y"]);
  });

  it("keeps a regular expression or a template part that holds a comment opener", () => {
    // given
    const source = "const r = /a\\/*b/;\nconst t = `${r} // c`;";

    // when
    const actual = withoutComments(source, "module.ts");

    // then
    expect(actual).toBe(source);
  });

  it("drops a comment that directly follows a regular expression", () => {
    // given
    const source = 'const r = /a///"divide-x"\nconst s = /a//*"divide-x"*/;';

    // when
    const actual = classLists(withoutComments(source, "module.ts"));

    // then
    expect(actual).toEqual([]);
  });

  it("drops a doc comment that closes the file", () => {
    // given
    const source = `const a = 1;\n/** "divide-x" */`;

    // when
    const actual = classLists(withoutComments(source, "module.ts"));

    // then
    expect(actual).toEqual([]);
  });
});
