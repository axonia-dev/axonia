import { render } from "@testing-library/react";
import { vi } from "vitest";

import type { NamedArea } from "@/features/expertise/domain/named-area";
import { NamedAreaPanel } from "@/features/expertise/ui/NamedAreaPanel";

export const STREAMING: NamedArea = {
  id: 1,
  name: "Streaming",
  tags: ["kafka", "cobol"],
};

export const TOPICS = ["kafka", "flink", "postgres", "redis"].map(
  (canonicalTag) => ({ canonicalTag }),
);
export const LINKS = [{ a: "kafka", b: "flink", weight: 2 }];

export const actions = {
  createNamedArea: vi.fn(),
  renameNamedArea: vi.fn(),
  deleteNamedArea: vi.fn(),
  placeInNamedArea: vi.fn(),
  removeFromNamedArea: vi.fn(),
};

export function namedAreasResult(data: NamedArea[] | undefined) {
  return { data, isSuccess: data !== undefined } as never;
}

export function mapResult(loaded: boolean) {
  return (
    loaded
      ? { data: { topics: TOPICS, links: LINKS }, isSuccess: true }
      : { data: undefined, isSuccess: false }
  ) as never;
}

export function renderPanel() {
  render(
    <NamedAreaPanel
      slug="guild"
      vocabulary={["kafka", "flink", "postgres", "redis", "cobol"]}
    />,
  );
}
