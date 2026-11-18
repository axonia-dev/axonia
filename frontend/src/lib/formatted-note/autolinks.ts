import type { Link, PhrasingContent } from "mdast";

const TRAIL = "!\"')*,.:;?_~";
const WORD_END = /[\s<]/;

function entityEnd(url: string, from: number): number {
  let index = from;
  while (/^[A-Za-z]$/.test(url.charAt(index))) {
    index++;
  }
  return index > from && url.charAt(index) === ";" ? index + 1 : -1;
}

function isTrailing(url: string, from: number): boolean {
  let index = from;
  while (index < url.length) {
    const char = url.charAt(index);
    if (TRAIL.includes(char)) {
      index++;
    } else if (char === "&") {
      index = entityEnd(url, index + 1);
      if (index < 0) {
        return false;
      }
    } else if (char === "]") {
      index++;
      if (index === url.length || "([".includes(url.charAt(index))) {
        return true;
      }
    } else {
      return false;
    }
  }
  return true;
}

function urlEnd(url: string): number {
  let open = 0;
  let close = 0;
  for (let index = url.indexOf("://") + 3; index < url.length; index++) {
    const char = url.charAt(index);
    if (char === "(") {
      open++;
    } else if (char === ")" && close < open) {
      close++;
    } else if (isTrailing(url, index)) {
      return index;
    } else if (char === ")") {
      close++;
    }
  }
  return url.length;
}

export function foundText(link: Link): string {
  const [text] = link.children;
  return text?.type === "text" ? text.value : "";
}

function isFoundUrl(node: PhrasingContent | undefined): node is Link {
  return (
    node?.type === "link" && !node.position && foundText(node) === node.url
  );
}

function split(written: string): PhrasingContent[] {
  const wordEnd = written.search(WORD_END);
  const word = wordEnd < 0 ? written : written.slice(0, wordEnd);
  const url = word.slice(0, urlEnd(word));
  const rest = written.slice(url.length);
  const link: Link = {
    type: "link",
    url,
    children: [{ type: "text", value: url }],
  };
  return rest ? [link, { type: "text", value: rest }] : [link];
}

function withTextsJoined(nodes: PhrasingContent[]): PhrasingContent[] {
  const result: PhrasingContent[] = [];
  for (const node of nodes) {
    const last = result.at(-1);
    if (node.type === "text" && last?.type === "text") {
      result.splice(-1, 1, { type: "text", value: last.value + node.value });
    } else {
      result.push(node);
    }
  }
  return result;
}

/** A url the autolink transform found where an open bracket kept the parser from reading it,
 *  ended where the parser ends one it reads itself: trailing punctuation, emphasis marks and
 *  an entity-like `&name;` stay text. */
export function withFoundUrls(nodes: PhrasingContent[]): PhrasingContent[] {
  const merged = withTextsJoined(nodes);
  return merged.flatMap((node, index) => {
    const next = merged[index + 1];
    if (isFoundUrl(node)) {
      return split(node.url + (next?.type === "text" ? next.value : ""));
    }
    return node.type === "text" && isFoundUrl(merged[index - 1]) ? [] : [node];
  });
}
