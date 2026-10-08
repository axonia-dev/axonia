/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

// Fontsource packages ship CSS-only entry points with no type declarations.
declare module "@fontsource-variable/inter";
declare module "@fontsource-variable/space-grotesk";

// MDX pages compile to React components (see the @mdx-js/rollup Vite plugin).
declare module "*.mdx" {
  import type { FC } from "react";
  const MDXComponent: FC;
  export default MDXComponent;
}
