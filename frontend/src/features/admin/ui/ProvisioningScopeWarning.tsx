import { TriangleAlert } from "lucide-react";

import type { GroupSource } from "@/features/admin/domain/admin-user";

interface ProvisioningScopeWarningProps {
  source: GroupSource;
}

/**
 * Warns about the Entra provisioning-scope silent failure, shown only when the
 * resolved group source is Entra-backed.
 *
 * When a member belongs to more than roughly 200 groups, Entra omits the group
 * claim from their token entirely and axonia resolves their access from the
 * SCIM-synced directory instead. Entra only provisions groups assigned to the
 * enterprise application, so a role bound to a group outside that scope silently
 * grants nothing to exactly those members, and neither side reports an error.
 *
 * The caveat is true precisely for the SCIM and Graph sources, both of which are
 * Entra-federated. For direct value entry it does not apply, so the note stays out
 * of the way. Gating on the already-resolved source means the page needs no separate
 * connection read to decide whether to warn.
 */
export function ProvisioningScopeWarning({
  source,
}: Readonly<ProvisioningScopeWarningProps>) {
  if (source !== "SCIM" && source !== "GRAPH") {
    return null;
  }

  return (
    <div
      role="note"
      className="border-border bg-surface-sunken text-muted-foreground flex items-start gap-2 rounded-md border px-3 py-2 text-xs"
    >
      <TriangleAlert
        size={14}
        className="text-warning mt-0.5 shrink-0"
        aria-hidden
      />
      <p>
        Bind only groups inside the Entra provisioning scope. A member of more
        than roughly 200 groups signs in without a group claim, so axonia
        resolves their access from the synced directory instead. A group outside
        that scope silently grants none of the access its roles carry, and
        nothing reports it.
      </p>
    </div>
  );
}
