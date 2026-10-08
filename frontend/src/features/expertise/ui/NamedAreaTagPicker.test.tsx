import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NamedAreaTagPicker } from "@/features/expertise/ui/NamedAreaTagPicker";

const STREAMING = { id: 1, name: "Streaming", tags: ["kafka"] };
const DATA = { id: 2, name: "Data", tags: ["postgres"] };

const onPlace = vi.fn();
let user: ReturnType<typeof userEvent.setup>;

function renderPicker() {
  render(
    <NamedAreaTagPicker
      namedArea={DATA}
      namedAreas={[STREAMING, DATA]}
      vocabulary={["kafka", "postgres", "redis"]}
      onPlace={onPlace}
    />,
  );
}

async function pick(tag: string) {
  await user.type(screen.getByRole("combobox"), tag);
  await user.click(
    await screen.findByRole("option", { name: (name) => name.startsWith(tag) }),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
});

describe("NamedAreaTagPicker", () => {
  it("places a tag no named area holds straight away", async () => {
    // given
    renderPicker();

    // when
    await pick("redis");

    // then
    expect(onPlace).toHaveBeenCalledWith("redis");
  });

  it("offers only tags this named area does not hold yet", async () => {
    // given
    renderPicker();

    // when
    await user.click(screen.getByRole("combobox"));
    await user.keyboard("{ArrowDown}");

    // then
    const options = await screen.findAllByRole("option");
    expect(options.map((o) => o.textContent)).toEqual([
      "kafkain Streaming",
      "redis",
    ]);
  });

  it("names the named area a held tag would leave, and waits", async () => {
    // given
    renderPicker();

    // when
    await pick("kafka");

    // then
    expect(
      screen.getByText("Moving kafka takes it out of Streaming."),
    ).toBeInTheDocument();
    expect(onPlace).not.toHaveBeenCalled();
  });

  it("moves a held tag once confirmed", async () => {
    // given
    renderPicker();
    await pick("kafka");

    // when
    await user.click(screen.getByRole("button", { name: "Move" }));

    // then
    expect(onPlace).toHaveBeenCalledWith("kafka");
    expect(screen.queryByText(/takes it out of/)).not.toBeInTheDocument();
  });

  it("leaves a held tag where it is when cancelled", async () => {
    // given
    renderPicker();
    await pick("kafka");

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onPlace).not.toHaveBeenCalled();
    expect(screen.queryByText(/takes it out of/)).not.toBeInTheDocument();
  });
});
