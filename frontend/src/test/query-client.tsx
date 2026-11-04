import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

/** Render options that put the tree inside a query client, for components that read the cache. */
export function inQueryClient(
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  };
}
