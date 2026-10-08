import type { ReactNode } from "react";

export function Chapter({
  id,
  title,
  question,
  children,
}: Readonly<{
  id: string;
  title: string;
  question: string;
  children: ReactNode;
}>) {
  return (
    <section id={id} className="mt-9 scroll-mt-20">
      <header className="mb-3 flex flex-wrap items-baseline gap-2.5">
        <h2 className="text-[15px] font-bold">{title}</h2>
        <span className="text-muted-foreground text-[13px]">{question}</span>
      </header>
      {children}
    </section>
  );
}
