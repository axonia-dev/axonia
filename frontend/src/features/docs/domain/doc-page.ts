import type { ComponentType } from "react";

export interface DocPage {
  slug: string;
  title: string;
  group: string;
  Component: ComponentType;
}
