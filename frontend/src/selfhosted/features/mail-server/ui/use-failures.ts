import { useState } from "react";

import { ApiError } from "@/lib/api-client";
import type { MailServerValues } from "@/selfhosted/features/mail-server/domain/mail-server";

/** What a refused call sent: its values, and its recipient when it was a test. */
export type Sent = Readonly<{
  values: MailServerValues;
  recipient: string | null;
}>;

export type Failures = Readonly<{
  test: Error | null;
  save: Error | null;
  sent: Sent | null;
  stale: boolean;
}>;

const NONE: Failures = { test: null, save: null, sent: null, stale: false };

export function isStale(error: Error): boolean {
  return error instanceof ApiError && error.code === "stale_revision";
}

/** The open form's refused calls: the last test's or save's with what it sent, and whether the
 *  mail server changed since the form opened, which no edit or test undoes. */
export function useFailures() {
  const [failures, setFailures] = useState<Failures>(NONE);
  return {
    ...failures,
    failTest: (error: Error, sent: Sent) =>
      setFailures((current) => ({ ...current, test: error, sent })),
    failSave: (error: Error, sent: Sent) =>
      setFailures((current) => ({
        ...current,
        save: error,
        sent,
        stale: current.stale || isStale(error),
      })),
    markStale: () => setFailures((current) => ({ ...current, stale: true })),
    forget: () =>
      setFailures((current) =>
        current.test === null && current.save === null
          ? current
          : { ...current, test: null, save: null, sent: null },
      ),
  };
}
