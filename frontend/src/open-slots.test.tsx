import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { openSlots } from "@/open-slots";

describe("the open core's slots", () => {
  it("lands a member at the root on the communities list", () => {
    // given
    const { RootRedirect } = openSlots;

    // when
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/communities" element={<p>communities list</p>} />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText("communities list")).toBeInTheDocument();
  });

  it("renders what its shell gate wraps, and nothing else", () => {
    // given
    const { ShellGate } = openSlots;

    // when
    const { container } = render(
      <ShellGate>
        <p>shell content</p>
      </ShellGate>,
    );

    // then
    expect(container.innerHTML).toBe("<p>shell content</p>");
  });

  it("adds nothing a closed slice would", () => {
    // when
    const actual = {
      settingsSections: openSlots.settingsSections,
      accountPages: openSlots.accountPages,
      accountMenuItems: openSlots.accountMenuItems,
      AvatarDecoration: openSlots.AvatarDecoration,
      HelpEntry: openSlots.HelpEntry,
      OrganizationPicker: openSlots.OrganizationPicker,
      AgentAccessNotice: openSlots.AgentAccessNotice,
      PrivacyNotice: openSlots.PrivacyNotice,
      organizationScope: openSlots.organizationScope,
      CommunityInvite: openSlots.CommunityInvite,
      CommunityDeletion: openSlots.CommunityDeletion,
      notificationsCard: openSlots.notificationsCard,
      DelegatedSessionExit: openSlots.DelegatedSessionExit,
    };

    // then
    expect(actual).toEqual({
      settingsSections: [],
      accountPages: [],
      accountMenuItems: [],
      AvatarDecoration: null,
      HelpEntry: null,
      OrganizationPicker: null,
      AgentAccessNotice: null,
      PrivacyNotice: null,
      organizationScope: "token",
      CommunityInvite: null,
      CommunityDeletion: null,
      notificationsCard: null,
      DelegatedSessionExit: null,
    });
  });
});
