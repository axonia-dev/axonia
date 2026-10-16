import type { ReactNode } from "react";

export function ViewTransitionProbe({
  name,
  children,
}: Readonly<{ name?: string | undefined; children: ReactNode }>) {
  return <div data-morph-name={name}>{children}</div>;
}

export function morphNames(container: HTMLElement): (string | null)[] {
  return [...container.querySelectorAll("[data-morph-name]")].map((part) =>
    part.getAttribute("data-morph-name"),
  );
}
