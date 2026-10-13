import { render, screen } from "@testing-library/react";
import { Suspense } from "react";
import { describe, expect, it, vi } from "vitest";

import type { HeroCeremonyProps } from "@/features/community/ui/ceremony/HeroCeremony";
import type { WelcomePreviewProps } from "@/features/community/ui/ceremony/WelcomePreview";

import {
  LazyHeroCeremony,
  LazyWelcomePreview,
  preloadWelcomePreview,
} from "./lazy-ceremonies";

vi.mock("@/features/community/ui/ceremony/WelcomePreview", () => ({
  WelcomePreview: () => <p>preview playing</p>,
}));
vi.mock("@/features/community/ui/ceremony/HeroCeremony", () => ({
  HeroCeremony: () => <p>ceremony playing</p>,
}));

const previewProps = {
  target: { current: null },
  onDone: () => undefined,
} as unknown as WelcomePreviewProps;

const heroProps = {
  ceremony: { kind: "birth", countFrom: 1, countTo: 1 },
  target: { current: null },
  onDone: () => undefined,
} as unknown as HeroCeremonyProps;

async function landed(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve));
}

describe("lazy ceremonies", () => {
  it("renders a preloaded ceremony at once, without its fallback", async () => {
    // given
    preloadWelcomePreview();
    await landed();

    // when
    render(
      <Suspense fallback={<p>fallback</p>}>
        <LazyWelcomePreview {...previewProps} />
      </Suspense>,
    );

    // then
    expect(screen.getByText("preview playing")).toBeInTheDocument();
    expect(screen.queryByText("fallback")).toBeNull();
  });

  it("still loads a ceremony nobody preloaded", async () => {
    // when
    render(
      <Suspense fallback={<p>fallback</p>}>
        <LazyHeroCeremony {...heroProps} />
      </Suspense>,
    );

    // then
    expect(await screen.findByText("ceremony playing")).toBeInTheDocument();
  });
});
