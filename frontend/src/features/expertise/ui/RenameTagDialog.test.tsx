import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RenameTagDialog } from "@/features/expertise/ui/RenameTagDialog";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderDialog(over: { tag?: string | null; existingTags?: string[] }) {
  const onCancel = vi.fn();
  const onConfirm = vi.fn();
  render(
    <RenameTagDialog
      tag={over.tag === undefined ? "kafak" : over.tag}
      existingTags={over.existingTags ?? []}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />,
  );
  return { onCancel, onConfirm };
}

function nameField() {
  return screen.getByRole("textbox", { name: /new name/i });
}

async function typeName(name: string) {
  await user.clear(nameField());
  await user.type(nameField(), name);
}

describe("RenameTagDialog", () => {
  it("stays closed when no tag is being renamed", () => {
    renderDialog({ tag: null });
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("opens on the tag, prefilled with its current name", () => {
    renderDialog({});
    expect(nameField()).toHaveValue("kafak");
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  });

  it("shows the normalized name as the curator types", async () => {
    renderDialog({});

    await typeName("Apache Kafka!");

    expect(screen.getByText("apache-kafka")).toBeInTheDocument();
  });

  it("warns that renaming onto an existing tag folds them together", async () => {
    renderDialog({ existingTags: ["kafka", "spring"] });

    await typeName("Kafka");

    expect(
      screen.getByText(/folds .*kafak.* into .*kafka.*, permanently/i),
    ).toBeInTheDocument();
  });

  it("refuses a name longer than fifty characters", async () => {
    renderDialog({});

    await typeName("a".repeat(51));

    expect(screen.getByRole("button", { name: "Rename" })).toBeDisabled();
    expect(screen.getByText(/at most 50 characters/i)).toBeInTheDocument();
  });

  it("refuses an empty name", async () => {
    renderDialog({});

    await user.clear(nameField());

    expect(screen.getByRole("button", { name: "Rename" })).toBeDisabled();
  });

  it("refuses renaming a tag onto itself", async () => {
    renderDialog({});

    await typeName("Kafak");

    expect(screen.getByRole("button", { name: "Rename" })).toBeDisabled();
  });

  it("confirms with the normalized name, not the typed one", async () => {
    const { onConfirm } = renderDialog({});

    await typeName("Apache Kafka!");
    await user.click(screen.getByRole("button", { name: "Rename" }));

    expect(onConfirm).toHaveBeenCalledWith("apache-kafka");
  });

  it("cancels without renaming", async () => {
    const { onCancel, onConfirm } = renderDialog({});

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("says the rename reaches the community's content", () => {
    renderDialog({});
    expect(
      screen.getByText(/rewrites the tag on every artifact, topic and talk/i),
    ).toBeInTheDocument();
  });

  it("says the rename also reaches members' declarations and areas to learn", () => {
    renderDialog({});
    expect(
      screen.getByText(/members’ declarations and areas to learn/),
    ).toBeInTheDocument();
  });
});
