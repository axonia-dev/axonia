import type { ComponentPropsWithoutRef } from "react";

import { slugify } from "./slug";

function headingText(children: unknown): string {
  return typeof children === "string" ? children : "";
}

export const mdxComponents = {
  h2(props: ComponentPropsWithoutRef<"h2">) {
    const { children } = props;
    return <h2 id={slugify(headingText(children))}>{children}</h2>;
  },
};
