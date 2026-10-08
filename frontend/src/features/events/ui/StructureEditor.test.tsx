import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { SectionType } from "@/features/events/domain/section-type";
import { emptyTemplate } from "@/features/events/domain/structure";
import { StructureEditor } from "@/features/events/ui/StructureEditor";
import { stubVerticalLayout } from "@/test/dndkit";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const sectionTypes: SectionType[] = [
  {
    id: 1,
    label: "Talk",
    expectsPresenter: true,
    icon: null,
    accentColor: null,
  },
  {
    id: 2,
    label: "Round-table",
    expectsPresenter: false,
    icon: null,
    accentColor: null,
  },
];

describe("StructureEditor", () => {
  it("adds a section", async () => {
    const onChange = vi.fn();
    render(
      <StructureEditor
        value={[]}
        onChange={onChange}
        sectionTypes={sectionTypes}
      />,
    );
    await user.click(screen.getByRole("button", { name: /add section/i }));
    expect(onChange).toHaveBeenCalledWith([emptyTemplate(1)]);
  });

  it("removes a section", async () => {
    const onChange = vi.fn();
    render(
      <StructureEditor
        value={[emptyTemplate(1)]}
        onChange={onChange}
        sectionTypes={sectionTypes}
      />,
    );
    await user.click(screen.getByRole("button", { name: /remove section 1/i }));
    expect(onChange).toHaveBeenCalledWith([]);
  });

  it("shows the round-table hint for a no-presenter section type", () => {
    render(
      <StructureEditor
        value={[emptyTemplate(2)]}
        onChange={vi.fn()}
        sectionTypes={sectionTypes}
      />,
    );
    expect(screen.getByText(/no presenter/i)).toBeInTheDocument();
  });

  describe("keyboard reorder", () => {
    beforeEach(stubVerticalLayout);
    afterEach(() => vi.restoreAllMocks());

    it("reorders sections via the drag handle keyboard sensor", async () => {
      const onChange = vi.fn();
      render(
        <StructureEditor
          value={[emptyTemplate(1), emptyTemplate(2)]}
          onChange={onChange}
          sectionTypes={sectionTypes}
        />,
      );
      const handle = screen.getByRole("button", { name: /reorder section 1/i });
      handle.focus();
      await user.keyboard("{ }");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ }");
      expect(onChange).toHaveBeenCalledWith([
        emptyTemplate(2),
        emptyTemplate(1),
      ]);
    });
  });
});
