import { useMutation } from "@tanstack/react-query";

import { httpPrivacyGateway } from "@/features/privacy/adapters/http-privacy-gateway";

const gateway = httpPrivacyGateway;

/**
 * Deletes the caller's own account. Success and the two special-cased failures
 * (still blocked, identity-provider step incomplete) are handled by the dialog
 * that owns this mutation; there is nothing left to invalidate on success, since
 * the account - and every query scoped to it - is about to sign out for good.
 */
export function useEraseAccount() {
  return useMutation({ mutationFn: () => gateway.eraseAccount() });
}
