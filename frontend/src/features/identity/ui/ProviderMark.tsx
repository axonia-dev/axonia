import type { ReactNode } from "react";

import {
  GitHubMark,
  GoogleMark,
  MicrosoftMark,
} from "@/components/ui/BrandMarks";

interface ProviderMarkProps {
  label: string;
  className?: string | undefined;
}

interface MarkProps {
  className?: string | undefined;
}

const MARKS: Record<string, (props: Readonly<MarkProps>) => ReactNode> = {
  google: GoogleMark,
  github: GitHubMark,
  microsoft: MicrosoftMark,
};

// SignInProviderResolver builds an Entra-federated connection's label as
// "<Display Name>, via Microsoft Entra ID", where the display name is whatever the customer
// chose - it never equals "microsoft" outright, so the MARKS table alone can never reach
// MicrosoftMark for it. Matched by suffix, and checked before the exact-match table below, so a
// connection literally named "Google SSO, via Microsoft Entra ID" still resolves to the
// Microsoft mark rather than being caught by a broader match on "google" first.
const ENTRA_SUFFIX = ", via microsoft entra id";

/** A sign-in provider's brand mark, keyed by its label. An unrecognised label (a provider not
 *  yet mapped here) renders nothing rather than a broken image, so the caller's chip degrades
 *  to bare text instead of showing a missing icon. */
export function ProviderMark({
  label,
  className,
}: Readonly<ProviderMarkProps>) {
  const normalized = label.trim().toLowerCase();
  if (normalized.endsWith(ENTRA_SUFFIX)) {
    return <MicrosoftMark className={className} />;
  }
  const Mark = MARKS[normalized];
  if (Mark === undefined) return null;
  return <Mark className={className} />;
}
