import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider } from "react-aria-components";
import {
  MemoryRouter,
  useHref,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  MenuAnswerSection,
  MenuButton,
  MenuHeading,
  MenuItem,
  MenuSeparator,
} from "@/components/ui/Menu";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function CurrentLocation() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname}</p>;
}

/** The exact shape App.tsx wires at the root: react-router's `useNavigate` and `useHref`
 *  handed to react-aria's `RouterProvider`, so every `href` item underneath gets
 *  client-side navigation instead of react-aria's default `isNative: true` router. */
function RoutedMenu() {
  const navigate = useNavigate();
  return (
    <RouterProvider
      navigate={(path, options) => void navigate(path, options)}
      useHref={useHref}
    >
      <MenuButton label="Actions">
        <MenuItem id="settings" href="/settings/account">
          Account settings
        </MenuItem>
      </MenuButton>
    </RouterProvider>
  );
}

describe("Menu", () => {
  it("opens on trigger click and fires onAction with the item id", async () => {
    // given
    const onAction = vi.fn();
    render(
      <MenuButton label="Actions" onAction={onAction}>
        <MenuItem id="edit">Edit</MenuItem>
        <MenuItem id="delete">Delete</MenuItem>
      </MenuButton>,
    );

    // when - open the menu
    await user.click(screen.getByRole("button", { name: "Actions" }));

    // then - items are visible
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeInTheDocument();

    // when - choose one
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));

    // then
    expect(onAction).toHaveBeenCalledOnce();
    expect(onAction.mock.calls[0]?.[0]).toBe("delete");
  });

  it("runs an item's own action as well as the menu's", async () => {
    // given
    const onMenuAction = vi.fn();
    const onItemAction = vi.fn();
    render(
      <MenuButton label="Actions" onAction={onMenuAction}>
        <MenuItem id="end" onAction={onItemAction}>
          End
        </MenuItem>
      </MenuButton>,
    );
    await user.click(screen.getByRole("button", { name: "Actions" }));

    // when
    await user.click(screen.getByRole("menuitem", { name: "End" }));

    // then
    expect(onItemAction).toHaveBeenCalledOnce();
    expect(onMenuAction).toHaveBeenCalledOnce();
    expect(onMenuAction.mock.calls[0]?.[0]).toBe("end");
  });

  it("renders a separator between groups and keeps every item selectable", async () => {
    // given
    const onAction = vi.fn();
    render(
      <MenuButton label="Actions" onAction={onAction}>
        <MenuItem id="edit">Edit</MenuItem>
        <MenuSeparator />
        <MenuItem id="delete">Delete</MenuItem>
      </MenuButton>,
    );

    // when - open the menu
    await user.click(screen.getByRole("button", { name: "Actions" }));

    // then - both items render around the rule, and the collection still builds
    expect(screen.getAllByRole("menuitem")).toHaveLength(2);
    expect(screen.getByRole("separator")).toBeInTheDocument();

    // when - either item is still selectable
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));

    // then
    expect(onAction).toHaveBeenCalledOnce();
    expect(onAction.mock.calls[0]?.[0]).toBe("delete");
  });

  it("does not fire onAction when a disabled item is clicked", async () => {
    // given
    const onAction = vi.fn();
    render(
      <MenuButton label="Actions" onAction={onAction}>
        <MenuItem id="edit" isDisabled>
          Edit
        </MenuItem>
        <MenuItem id="delete">Delete</MenuItem>
      </MenuButton>,
    );

    // when - open the menu and click the disabled item
    await user.click(screen.getByRole("button", { name: "Actions" }));
    const editItem = screen.getByRole("menuitem", { name: "Edit" });
    expect(editItem).toHaveAttribute("data-disabled");
    await user.click(editItem);

    // then
    expect(onAction).not.toHaveBeenCalled();
  });

  it("renders a same-tab href as a plain link, not a new-tab one", async () => {
    // given
    render(
      <MenuButton label="Actions">
        <MenuItem id="settings" href="/settings/account">
          Account settings
        </MenuItem>
      </MenuButton>,
    );

    // when
    await user.click(screen.getByRole("button", { name: "Actions" }));

    // then
    const item = screen.getByRole("menuitem", { name: "Account settings" });
    expect(item).toHaveAttribute("href", "/settings/account");
    expect(item).not.toHaveAttribute("target");
    expect(item).not.toHaveAttribute("rel");
  });

  it("navigates client-side when wrapped in RouterProvider, rather than a document navigation", async () => {
    // Without a RouterProvider in scope, react-aria's default router is `isNative:
    // true` - clicking a plain `href` item does a real anchor navigation (full app
    // reboot, thrown-away query cache). The static `href` attribute alone can't tell
    // the two apart, since the browser would use it either way; only a click that
    // actually lands on the router - here, a real react-router location changing -
    // proves client-side navigation happened.
    render(
      <MemoryRouter initialEntries={["/"]}>
        <RoutedMenu />
        <CurrentLocation />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("location")).toHaveTextContent("/");

    await user.click(screen.getByRole("button", { name: "Actions" }));
    await user.click(
      screen.getByRole("menuitem", { name: "Account settings" }),
    );

    expect(screen.getByTestId("location")).toHaveTextContent(
      "/settings/account",
    );
  });

  it("colours a destructive item by tone rather than a caller-supplied class", async () => {
    // given
    render(
      <MenuButton label="Actions">
        <MenuItem id="edit">Edit</MenuItem>
        <MenuItem id="delete" tone="danger">
          Delete
        </MenuItem>
      </MenuButton>,
    );

    // when
    await user.click(screen.getByRole("button", { name: "Actions" }));

    // then
    expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveClass(
      "text-danger",
    );
    expect(screen.getByRole("menuitem", { name: "Edit" })).not.toHaveClass(
      "text-danger",
    );
  });

  /**
   * A menu whose items answer one question often has to state the fact being answered first.
   * The heading must not be an item: made selectable it would take arrow-key focus and read as a
   * fourth answer to a question that has three.
   */
  it("states a heading above the items without making it one of them", async () => {
    render(
      <MenuButton label="Actions">
        <MenuHeading
          statement="The reminder goes out Sunday."
          subline="To 2 members"
        />
        <MenuItem id="now">Send it now</MenuItem>
      </MenuButton>,
    );

    await user.click(screen.getByRole("button", { name: "Actions" }));

    expect(screen.getByText("The reminder goes out Sunday.")).toBeVisible();
    expect(screen.getByText("To 2 members")).toBeVisible();
    expect(screen.getAllByRole("menuitem")).toHaveLength(1);
  });

  /**
   * The answers to one question are radios; a command that sits beside them is not. Putting the
   * selection on the whole menu instead would make every command a `menuitemradio` reading
   * "not selected", announcing an answer nobody asked for.
   */
  it("keeps selection inside the answer group, so a command beside it stays a command", async () => {
    render(
      <MenuButton label="Actions">
        <MenuAnswerSection label="When does it go out" selectedKey="schedule">
          <MenuItem id="now">Send it now</MenuItem>
          <MenuItem id="schedule">Sunday</MenuItem>
        </MenuAnswerSection>
        <MenuItem id="preview">Preview it</MenuItem>
      </MenuButton>,
    );

    await user.click(screen.getByRole("button", { name: "Actions" }));

    expect(screen.getByRole("menuitemradio", { name: "Sunday" })).toBeChecked();
    expect(
      screen.getByRole("menuitemradio", { name: "Send it now" }),
    ).not.toBeChecked();
    expect(screen.getByRole("menuitem", { name: "Preview it" })).toBeVisible();
  });
});
