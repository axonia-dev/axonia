import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { NotFoundPage } from "@/components/patterns/NotFoundPage";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("NotFoundPage", () => {
  it("shows the not-found message and navigates to the home target", async () => {
    render(
      <MemoryRouter initialEntries={["/nope"]}>
        <Routes>
          <Route path="/" element={<div>home-screen</div>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("heading", { name: "Page not found" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Go home" }));
    expect(screen.getByText("home-screen")).toBeInTheDocument();
  });

  it("uses a custom home label and target", async () => {
    render(
      <MemoryRouter initialEntries={["/communities/x/nope"]}>
        <Routes>
          <Route path="/communities/x" element={<div>community-home</div>} />
          <Route
            path="*"
            element={
              <NotFoundPage
                homeTo="/communities/x"
                homeLabel="Back to community"
              />
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: "Back to community" }));
    expect(screen.getByText("community-home")).toBeInTheDocument();
  });
});
