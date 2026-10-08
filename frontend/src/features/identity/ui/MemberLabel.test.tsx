import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MemberLabel } from "./MemberLabel";

describe("MemberLabel", () => {
  it("shows a present member's name alone", () => {
    render(<MemberLabel name="Alice Martin" status="PRESENT" />);
    expect(screen.getByText("Alice Martin")).toBeInTheDocument();
    expect(screen.queryByText(/Deactivated/)).not.toBeInTheDocument();
  });

  it("marks a deactivated member beside their name, muted", () => {
    render(<MemberLabel name="Alice Martin" status="DEACTIVATED" />);
    expect(screen.getByText("Alice Martin")).toHaveClass(
      "text-muted-foreground",
    );
    expect(screen.getByText("(Deactivated)")).toBeInTheDocument();
  });

  it("renders an erased member as a deleted member, in muted italics", () => {
    render(<MemberLabel name="Deleted member" status="ERASED" />);
    const label = screen.getByText("Deleted member");
    expect(label).toHaveClass("text-muted-foreground", "italic");
  });

  it("keeps its own muted colour even when a caller passes a conflicting one", () => {
    render(
      <MemberLabel
        name="Deleted member"
        status="ERASED"
        className="text-foreground"
      />,
    );
    expect(screen.getByText("Deleted member")).toHaveClass(
      "text-muted-foreground",
      "italic",
    );
  });
});
