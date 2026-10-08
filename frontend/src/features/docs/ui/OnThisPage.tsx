import { type RefObject, useEffect, useState } from "react";

interface Heading {
  id: string;
  text: string;
}

interface OnThisPageProps {
  containerRef: RefObject<HTMLElement | null>;
  slug: string;
}

export function OnThisPage({ containerRef, slug }: Readonly<OnThisPageProps>) {
  const [headings, setHeadings] = useState<Heading[]>([]);

  useEffect(() => {
    const root = containerRef.current;
    if (root === null) {
      setHeadings([]);
      return;
    }
    const found = Array.from(root.querySelectorAll("h2[id]")).map((node) => ({
      id: node.id,
      text: node.textContent ?? "",
    }));
    setHeadings(found);
  }, [containerRef, slug]);

  if (headings.length === 0) {
    return <div />;
  }

  return (
    <nav aria-label="On this page" className="text-sm">
      <p className="text-muted-foreground pb-2 text-xs font-bold tracking-wide uppercase">
        On this page
      </p>
      {headings.map((heading) => (
        <a
          key={heading.id}
          href={`#${heading.id}`}
          className="text-muted-foreground hover:text-foreground block py-1"
        >
          {heading.text}
        </a>
      ))}
    </nav>
  );
}
