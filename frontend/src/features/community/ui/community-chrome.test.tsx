import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import {
  CrumbSetterProvider,
  useCommunityCrumbs,
} from "@/features/community/ui/community-chrome";

function Child() {
  useCommunityCrumbs([{ label: "Frontend Dojo" }]);
  return null;
}

function ConditionalChild({ show }: Readonly<{ show: boolean }>) {
  if (!show) return null;
  return <Child />;
}

describe("useCommunityCrumbs", () => {
  it("registers crumbs with the provider", () => {
    function Harness() {
      const [crumbs, setCrumbs] = useState<{ label: string }[]>([]);
      return (
        <CrumbSetterProvider value={setCrumbs}>
          <span>crumbs:{crumbs.map((c) => c.label).join(",")}</span>
          <Child />
        </CrumbSetterProvider>
      );
    }
    render(<Harness />);
    expect(screen.getByText("crumbs:Frontend Dojo")).toBeInTheDocument();
  });

  it("clears crumbs on unmount", () => {
    function Harness() {
      const [crumbs, setCrumbs] = useState<{ label: string }[]>([]);
      const [show, setShow] = useState(true);
      return (
        <CrumbSetterProvider value={setCrumbs}>
          <span>crumbs:{crumbs.map((c) => c.label).join(",")}</span>
          <button onClick={() => setShow(false)}>unmount</button>
          <ConditionalChild show={show} />
        </CrumbSetterProvider>
      );
    }
    render(<Harness />);
    expect(screen.getByText("crumbs:Frontend Dojo")).toBeInTheDocument();
    fireEvent.click(screen.getByText("unmount"));
    expect(screen.getByText("crumbs:")).toBeInTheDocument();
  });
});
