import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { openSlots } from "@/open-slots";

import { MemberAvatar } from "./MemberAvatar";

function Decoration({ subject }: Readonly<{ subject: string }>) {
  return <span data-testid="decoration">{subject}</span>;
}

const decorating: EditionSlots = { ...openSlots, AvatarDecoration: Decoration };

describe("MemberAvatar", () => {
  it("draws a bare avatar where the edition supplies no decoration", () => {
    const { container } = render(
      <MemberAvatar
        name="Marta Ruiz"
        hue="#6e3ceb"
        subject="marta"
        src={null}
        className="size-22 text-2xl"
      />,
    );

    expect(screen.getByText("MR")).toBeInTheDocument();
    expect(container.firstElementChild).toHaveClass("size-22");
    expect(screen.queryByTestId("avatar-ring")).not.toBeInTheDocument();
  });

  it("hands the edition's decoration the subject it draws, inside a box the decoration can fill", () => {
    render(
      <EditionProvider edition={decorating}>
        <MemberAvatar
          name="Marta Ruiz"
          hue="#6e3ceb"
          subject="marta"
          src={null}
        />
      </EditionProvider>,
    );

    const decoration = screen.getByTestId("decoration");
    expect(decoration).toHaveTextContent("marta");
    expect(decoration.parentElement).toHaveClass("relative", "rounded-full");
  });

  it("follows a square avatar's corners with the box it decorates in", () => {
    render(
      <EditionProvider edition={decorating}>
        <MemberAvatar
          name="Marta Ruiz"
          hue="#6e3ceb"
          subject="marta"
          src={null}
          shape="square"
        />
      </EditionProvider>,
    );

    expect(screen.getByTestId("decoration").parentElement).toHaveClass(
      "rounded-lg",
    );
  });

  it("decorates nobody it cannot name", () => {
    render(
      <EditionProvider edition={decorating}>
        <MemberAvatar
          name="Marta Ruiz"
          hue="#6e3ceb"
          subject={null}
          src={null}
        />
      </EditionProvider>,
    );

    expect(screen.queryByTestId("decoration")).not.toBeInTheDocument();
    expect(screen.getByText("MR")).toBeInTheDocument();
  });

  it("passes the caller's size straight through to the avatar", () => {
    const { container } = render(
      <EditionProvider edition={decorating}>
        <MemberAvatar
          name="Marta Ruiz"
          hue="#6e3ceb"
          subject="marta"
          src={null}
          className="size-22 text-2xl"
        />
      </EditionProvider>,
    );

    expect(container.querySelector(".size-22")).not.toBeNull();
  });
});
