import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TagInput } from "@/components/ui/TagInput";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("TagInput", () => {
  it("renders with an empty tag list", () => {
    render(<TagInput label="Tags" value={[]} onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: /tags/i })).toBeInTheDocument();
  });

  it("names the field once, not twice", () => {
    render(<TagInput label="Tags" value={[]} onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "Tags" })).toBeInTheDocument();
  });

  it("calls onChange with the new tag when Enter is pressed", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={[]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "typescript{Enter}");
    expect(onChange).toHaveBeenCalledWith(["typescript"]);
  });

  it("calls onChange with the new tag when comma is typed", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={[]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "ddd,");
    expect(onChange).toHaveBeenCalledWith(["ddd"]);
  });

  it("renders existing chips for the value prop", () => {
    render(
      <TagInput
        label="Tags"
        value={["typescript", "ddd"]}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText("typescript")).toBeInTheDocument();
    expect(screen.getByText("ddd")).toBeInTheDocument();
  });

  it("calls onChange without the removed tag when the remove button is clicked", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={["typescript", "ddd"]}
        onChange={onChange}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /remove tag typescript/i }),
    );
    expect(onChange).toHaveBeenCalledWith(["ddd"]);
  });

  it("stores raw input trimmed only (preserves casing and spaces)", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={[]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "Apache Kafka{Enter}");
    expect(onChange).toHaveBeenCalledWith(["Apache Kafka"]);
  });

  it("trims leading and trailing whitespace from tags", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={[]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    // userEvent types the string; we add spaces via the raw value via blur
    await user.type(input, "  DDD  {Enter}");
    expect(onChange).toHaveBeenCalledWith(["DDD"]);
  });

  it("does not add duplicate tags (exact match)", async () => {
    const onChange = vi.fn();
    render(
      <TagInput label="Tags" value={["typescript"]} onChange={onChange} />,
    );
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "typescript{Enter}");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("deduplicates case-insensitively, keeping the first spelling", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={["Kafka"]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "kafka{Enter}");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("removes the last tag when Backspace is pressed in an empty input", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={["typescript", "ddd"]}
        onChange={onChange}
      />,
    );
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.keyboard("{Backspace}");
    expect(onChange).toHaveBeenCalledWith(["typescript"]);
  });

  it("commits on a separator the caller names instead of the comma", async () => {
    const onChange = vi.fn();
    render(
      <TagInput
        label="Tags"
        value={[]}
        onChange={onChange}
        separators={[" "]}
      />,
    );
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.type(input, "kafka ");
    expect(onChange).toHaveBeenCalledWith(["kafka"]);
  });

  it("splits pasted comma-separated text into multiple tags on commit", async () => {
    const onChange = vi.fn();
    render(<TagInput label="Tags" value={[]} onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: /tags/i });
    await user.click(input);
    await user.paste("kafka, ddd");
    await user.keyboard("{Enter}");
    expect(onChange).toHaveBeenCalledWith(["kafka", "ddd"]);
  });
});

describe("TagInput - validation", () => {
  const rejectShort = (candidate: string): string | null =>
    candidate.length < 3 ? "Too short." : null;

  function renderWithValidator(
    onChange = vi.fn(),
    extraProps: Partial<ComponentProps<typeof TagInput>> = {},
  ) {
    render(
      <TagInput
        label="Tags"
        value={[]}
        onChange={onChange}
        validate={rejectShort}
        {...extraProps}
      />,
    );
    return screen.getByRole("textbox", { name: /tags/i });
  }

  it("accepts entries the validator approves", async () => {
    const onChange = vi.fn();
    const input = renderWithValidator(onChange);
    await user.click(input);
    await user.type(input, "kafka{Enter}");
    expect(onChange).toHaveBeenCalledWith(["kafka"]);
  });

  it("rejects an invalid entry, keeps the text, and shows the message", async () => {
    const onChange = vi.fn();
    const input = renderWithValidator(onChange);
    await user.click(input);
    await user.type(input, "ab{Enter}");
    expect(onChange).not.toHaveBeenCalled();
    expect(input).toHaveValue("ab");
    expect(screen.getByRole("alert")).toHaveTextContent("Too short.");
  });

  it("clears the rejection message as soon as the input changes", async () => {
    const input = renderWithValidator();
    await user.click(input);
    await user.type(input, "ab{Enter}");
    await user.type(input, "c");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("adds nothing when a pasted list contains one invalid entry", async () => {
    const onChange = vi.fn();
    const input = renderWithValidator(onChange);
    await user.click(input);
    await user.paste("kafka, ab");
    await user.keyboard("{Enter}");
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Too short.");
  });

  it("associates the rejection message with the input", async () => {
    const input = renderWithValidator();
    await user.click(input);
    await user.type(input, "ab{Enter}");
    expect(input).toHaveAccessibleDescription("Too short.");
  });

  it("reports error transitions through onErrorChange", async () => {
    const onErrorChange = vi.fn();
    const input = renderWithValidator(vi.fn(), { onErrorChange });
    await user.click(input);
    await user.type(input, "ab{Enter}");
    expect(onErrorChange).toHaveBeenLastCalledWith("Too short.");
    await user.type(input, "c");
    expect(onErrorChange).toHaveBeenLastCalledWith(null);
  });

  it("clears the rejection message when a chip is removed", async () => {
    const onChange = vi.fn();
    const input = renderWithValidator(onChange, { value: ["kafka"] });
    await user.click(input);
    await user.type(input, "ab{Enter}");
    expect(screen.getByRole("alert")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /remove tag kafka/i }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
