import { KeyRound, Star } from "lucide-react";
import { describe, expect, it } from "vitest";

import { accountSettingsGroups } from "@/components/patterns/settings-nav.account";

function labels(groups: ReturnType<typeof accountSettingsGroups>) {
  return groups.flatMap((group) => group.items.map((item) => item.label));
}

const everything = {
  dataRights: true,
  agentAccess: true,
};

const extra = {
  label: "Extras",
  chrome: "divider" as const,
  items: [{ path: "extra", label: "Extra", icon: Star }],
};

describe("accountSettingsGroups", () => {
  it("offers Privacy only when data rights apply", () => {
    expect(labels(accountSettingsGroups(everything, []))).toEqual([
      "Profile",
      "API keys",
      "Notifications",
      "Privacy",
    ]);
    expect(
      labels(accountSettingsGroups({ ...everything, dataRights: false }, [])),
    ).toEqual(["Profile", "API keys", "Notifications"]);
  });

  it("drops API keys where agents may not connect", () => {
    expect(
      labels(accountSettingsGroups({ ...everything, agentAccess: false }, [])),
    ).toEqual(["Profile", "Notifications", "Privacy"]);
  });

  it("paths the Account section as profile, not account", () => {
    const [account] = accountSettingsGroups(everything, []);
    expect(account?.items[0]?.path).toBe("profile");
  });

  it("lists every account path relative, with no leading slash", () => {
    const paths = accountSettingsGroups(everything, [extra]).flatMap((group) =>
      group.items.map((item) => item.path),
    );
    expect(paths.length).toBeGreaterThan(0);
    expect(paths.every((path) => !path.startsWith("/"))).toBe(true);
  });

  it("places a page that joins the Account group after Notifications and ahead of Privacy", () => {
    // given
    const joined = { path: "keys", label: "Joined", icon: KeyRound };

    // when
    const [account, ...others] = accountSettingsGroups(
      everything,
      [],
      [joined],
    );

    // then
    expect(account?.items.map((item) => item.label)).toEqual([
      "Profile",
      "API keys",
      "Notifications",
      "Joined",
      "Privacy",
    ]);
    expect(others).toEqual([]);
  });
});

describe("an account page an edition contributes", () => {
  it("stands in its own group after the account's, which then draws no header", () => {
    const groups = accountSettingsGroups(everything, [extra]);

    expect(groups.map((group) => group.label)).toEqual(["Account", "Extras"]);
    expect(groups[0]?.chrome).toBe("none");
    expect(groups[1]?.chrome).toBe("divider");
  });

  it("leaves the account's group alone, with no chrome of its own, where none is contributed", () => {
    const groups = accountSettingsGroups(everything, []);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.label).toBe("Account");
    expect(groups[0]?.chrome).toBeUndefined();
  });
});

describe("a person with no organization", () => {
  it("is offered only what needs no organization", () => {
    // when
    const actual = labels(
      accountSettingsGroups({ ...everything, orgless: true }, [extra]),
    );

    // then
    expect(actual).toEqual(["API keys", "Privacy", "Extra"]);
  });
});
