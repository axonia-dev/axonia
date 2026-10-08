import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  DelegatedSessionProvider,
  useIsDelegated,
} from "@/auth/delegated-session";

function Probe() {
  return <p>{useIsDelegated() ? "delegated" : "own sign-in"}</p>;
}

describe("useIsDelegated", () => {
  it("reports a tab below the provider as delegated", () => {
    // when
    render(
      <DelegatedSessionProvider>
        <Probe />
      </DelegatedSessionProvider>,
    );

    // then
    expect(screen.getByText("delegated")).toBeInTheDocument();
  });

  it("reports a tab with no provider above it as signed in on its own", () => {
    // when
    render(<Probe />);

    // then
    expect(screen.getByText("own sign-in")).toBeInTheDocument();
  });
});
