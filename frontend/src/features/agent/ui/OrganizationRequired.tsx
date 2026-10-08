import { useLocation } from "react-router-dom";

import { enterOrganization } from "@/auth/enter-organization";
import { useAuth } from "@/auth/session";
import { Button } from "@/components/ui/Button";
import { useEdition } from "@/edition-context";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";

import { ConsentNotice, OrganizationChoice } from "./ConsentRefusal";

/**
 * A token that holds no organization the auto-entry could settle. Where the edition supplies a
 * picker the choice is made in it; without one, each membership is offered as a sign-in scoped to
 * it that comes back to this request, so the page never asks for a choice it cannot take.
 */
export function OrganizationRequired({ title }: Readonly<{ title: string }>) {
  const { OrganizationPicker } = useEdition();

  if (OrganizationPicker !== null) {
    return (
      <ConsentNotice title={title} line="Choose an organization to connect.">
        <OrganizationChoice />
      </ConsentNotice>
    );
  }

  return (
    <ConsentNotice title={title} line="Enter an organization to connect.">
      <OrganizationEntry />
    </ConsentNotice>
  );
}

function OrganizationEntry() {
  const auth = useAuth();
  const { pathname, search } = useLocation();
  const { data: organizations = [] } = useMyOrganizations();

  return (
    <div className="flex flex-wrap gap-2">
      {organizations.map((org) => (
        <Button
          key={org.id}
          onPress={() =>
            enterOrganization(auth, org.id, `${pathname}${search}`)
          }
        >
          Enter {org.name}
        </Button>
      ))}
    </div>
  );
}
