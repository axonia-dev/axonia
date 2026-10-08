import {
  Book,
  Building2,
  LifeBuoy,
  Moon,
  Power,
  Sun,
  UserRound,
} from "lucide-react";
import { useState } from "react";
import type { Key } from "react-aria-components";
import {
  Button as AriaButton,
  Menu as AriaMenu,
  MenuSection,
  MenuTrigger,
  Popover,
} from "react-aria-components";

import { signOut } from "@/auth/account-state";
import { useIsDelegated } from "@/auth/delegated-session";
import type { AuthContextProps } from "@/auth/session";
import { useAuth } from "@/auth/session";
import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import {
  ACCOUNT_SETTINGS,
  ORGANIZATION_SETTINGS,
  organizationSettingsTitle,
} from "@/components/patterns/settings-nav";
import {
  MenuItem,
  menuPopoverClass,
  MenuSeparator,
} from "@/components/ui/Menu";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useMe } from "@/features/identity/api/use-me";
import type { User } from "@/features/identity/domain/user";
import type { OidcProfile } from "@/features/identity/ui/account-identity";
import {
  resolveAccountEmail,
  resolveDisplayName,
} from "@/features/identity/ui/account-identity";
import {
  IdentityCard,
  type IdentityProps,
} from "@/features/identity/ui/AccountMenuIdentity";
import { userHue } from "@/lib/user-hue";
import { useTheme } from "@/theme/ThemeProvider";

const LOG_OUT = "logout";
const SUPPORT = "support";
const THEME = "theme";
const ACCOUNT_SETTINGS_KEY = "account-settings";
const ORGANIZATION_SETTINGS_KEY = "organization-settings";
const DOCUMENTATION_KEY = "documentation";

function sameAddress(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

function resolveEmail(
  me: User | undefined,
  p: OidcProfile | undefined,
  name: string,
): string | undefined {
  const email = resolveAccountEmail(me, p);
  return email !== undefined && sameAddress(email, name) ? undefined : email;
}

// The avatar comes from our API or not at all. The OIDC ID token's `picture` claim
// points at the identity provider's own host, and falling back to it would hotlink a
// third-party image - exactly the harm the content security policy exists to remove
// (and the browser proved it: the policy blocked the request). Absence renders
// initials, which Avatar already does for a null src, so there is no fallback to add.
function resolveAvatar(me: User | undefined): string | null {
  return me?.avatarUrl ?? null;
}

function resolveIdentity(
  me: User | undefined,
  auth: AuthContextProps,
): IdentityProps {
  const p = auth.user?.profile;
  const name = resolveDisplayName(me, p);
  const email = resolveEmail(me, p, name);
  const subject = me?.id ?? p?.sub ?? null;
  const hue = userHue(subject ?? name);
  const avatarUrl = resolveAvatar(me);
  return { name, email, hue, avatarUrl, subject };
}

/**
 * The single "this is you / log out" surface, shown in every shell: a bare avatar
 * trigger, always in the same place (the top bar's far right on desktop, the mobile
 * top bar otherwise). Opens a menu: an identity card, then the two settings
 * surfaces, Documentation, Support where the edition supplies help, then a footer
 * section pairing a theme item with Log out.
 *
 * The footer is a `MenuSection` (not a plain wrapper) whose own `className` lays
 * its two items out as a row: `AriaMenu` renders only the collection items it
 * builds from its own children, so a bare interactive element among `MenuItem`s
 * would silently vanish from the rendered menu. Both footer items are therefore
 * real `MenuItem`s - arrow-key reachable, valid `menuitem` ARIA, `Log out`'s
 * danger colouring coming from `MenuItem`'s own `tone` prop rather than a
 * hardcoded class. Choosing either one closes the menu, as react-aria does for
 * every item selection; that includes the theme item, and nothing here works
 * around it.
 *
 * The organization/community settings row is gated on being scoped into an organization
 * at all, not on any permission within it: the tenant section it opens on is ungated by
 * design (switching tenants lives there now, and a plain member must still be able to
 * reach it), so every member with an organization sees the row - an org-less caller (the
 * Discover lobby) does not, since there is nothing to scope it to. It is never rendered
 * disabled, always simply absent, matching how the rail has always dropped a destination
 * a caller cannot reach rather than offering a click that can only be refused.
 *
 * A delegated tab has no sign-in of its own to leave, so it is offered no Log out: the edition's
 * `DelegatedSessionExit`, when it has one, takes the item's place.
 */
export function AccountMenu() {
  const auth = useAuth();
  const me = useMe();
  const delegated = useIsDelegated();
  const { data: organization } = useCurrentOrganization();
  const { theme, toggleTheme } = useTheme();
  const { HelpEntry, DelegatedSessionExit } = useEdition();
  const [gettingHelp, setGettingHelp] = useState(false);
  const identity = resolveIdentity(me.data, auth);

  function onAction(key: Key) {
    if (key === SUPPORT) setGettingHelp(true);
    else if (key === THEME) toggleTheme();
    else if (key === LOG_OUT) signOut(auth);
  }

  return (
    <>
      <MenuTrigger>
        {/* MenuButton can't host a non-menu header or footer inside its Popover, so we
            assemble the primitives directly. */}
        <AriaButton
          aria-label="Account"
          className="focus-visible:ring-focus-ring cursor-pointer rounded-md outline-none focus-visible:ring-2"
        >
          <MemberAvatar
            name={identity.name}
            hue={identity.hue}
            subject={identity.subject}
            shape="circle"
            src={identity.avatarUrl}
          />
        </AriaButton>
        <Popover className={menuPopoverClass}>
          <IdentityCard {...identity} />
          <AriaMenu onAction={onAction} className="outline-none">
            <MenuItem id={ACCOUNT_SETTINGS_KEY} href={`/${ACCOUNT_SETTINGS}`}>
              <UserRound aria-hidden className="h-3.5 w-3.5 shrink-0" />
              Account settings
            </MenuItem>
            {organization !== undefined && (
              <MenuItem
                id={ORGANIZATION_SETTINGS_KEY}
                href={`/${ORGANIZATION_SETTINGS}`}
              >
                <Building2 aria-hidden className="h-3.5 w-3.5 shrink-0" />
                {organizationSettingsTitle(organization.kind)}
              </MenuItem>
            )}
            <MenuSeparator />
            <MenuItem id={DOCUMENTATION_KEY} href="/docs">
              <Book aria-hidden className="h-3.5 w-3.5 shrink-0" />
              Documentation
            </MenuItem>
            {HelpEntry !== null && (
              <MenuItem id={SUPPORT}>
                <LifeBuoy aria-hidden className="h-3.5 w-3.5 shrink-0" />
                Support
              </MenuItem>
            )}
            <MenuSeparator />
            <MenuSection
              aria-label="Theme and sign out"
              className="mt-1 flex items-center justify-between gap-1"
            >
              <MenuItem id={THEME}>
                {theme === "dark" ? (
                  <Sun aria-hidden className="h-3.5 w-3.5 shrink-0" />
                ) : (
                  <Moon aria-hidden className="h-3.5 w-3.5 shrink-0" />
                )}
                {theme === "dark" ? "Light" : "Dark"} theme
              </MenuItem>
              {delegated ? (
                DelegatedSessionExit !== null && <DelegatedSessionExit />
              ) : (
                <MenuItem id={LOG_OUT} tone="danger">
                  Log out
                  <Power aria-hidden className="h-3.5 w-3.5 shrink-0" />
                </MenuItem>
              )}
            </MenuSection>
          </AriaMenu>
        </Popover>
      </MenuTrigger>
      {HelpEntry !== null && (
        <HelpEntry isOpen={gettingHelp} onOpenChange={setGettingHelp} />
      )}
    </>
  );
}
