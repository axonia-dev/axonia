import { MDXProvider } from "@mdx-js/react";
import { useParams } from "react-router-dom";

import { mdxComponents } from "@/components/mdx/mdx-components";
import { EmptyState } from "@/components/patterns/EmptyState";

import { findDocPage } from "../domain/doc-registry";

export function DocPageView() {
  const { slug = "" } = useParams();
  const page = findDocPage(slug);

  if (page === undefined) {
    return (
      <EmptyState
        title="Page not found"
        description="This documentation page does not exist. Pick a page from the menu."
      />
    );
  }

  const { Component } = page;
  return (
    <article className="doc-prose">
      <MDXProvider components={mdxComponents}>
        <Component />
      </MDXProvider>
    </article>
  );
}
