import { useCallback, useEffect, useRef, useState } from "react";

import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { ApiError, apiFetchBlob } from "@/lib/api-client";

export type DownloadExportErrorKind =
  "rate_limited" | "refused" | "forbidden" | "unknown";

interface DownloadExportOptions {
  onError?: (error: unknown) => void;
}

export interface DownloadExport {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  mutate: (variables?: undefined, options?: DownloadExportOptions) => void;
}

const FALLBACK_FILENAME = "export.zip";

// The two coded refusals both endpoints can throw (see PrivacyExceptionHandler on
// the backend). A bare 403 with neither code is a different, uncoded refusal -
// ForbiddenException for a missing permission or a Hub calling the whole-org
// export - and must not be told apart as an own sign-in refusal, which it is not.
const RATE_LIMITED_CODE = "EXPORT_RATE_LIMITED";

function saveToDisk(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

async function streamToDisk(path: string): Promise<void> {
  const { blob, filename } = await apiFetchBlob(path);
  saveToDisk(blob, filename ?? FALLBACK_FILENAME);
}

/**
 * Distinguishes causes worth naming separately from the backend's own error
 * `code`, not from the HTTP status alone: a 403 is reachable for at least two
 * unrelated reasons (a session without the person's own sign-in, or a plain
 * missing permission / Hub calling the enterprise-only export), and only the
 * coded one is about the sign-in.
 */
export function classifyDownloadError(error: unknown): DownloadExportErrorKind {
  if (isOwnSignInRefusal(error)) return "refused";
  if (!(error instanceof ApiError)) return "unknown";
  if (error.code === RATE_LIMITED_CODE) return "rate_limited";
  if (error.status === 403) return "forbidden";
  return "unknown";
}

export function downloadExportErrorMessage(
  kind: DownloadExportErrorKind,
): string {
  if (kind === "rate_limited") {
    return "Too many export requests have been made recently. Wait a while and try again.";
  }
  if (kind === "refused") {
    return "This export needs the person’s own sign-in.";
  }
  if (kind === "forbidden") {
    return "This account does not have access to this export. If permissions changed recently, refresh the page and try again.";
  }
  return "Something went wrong while preparing the export. Try again.";
}

/**
 * Streams a data-export archive straight into a browser download. There is no
 * job to poll: the backend builds the zip into the response as it serves the
 * request, so `isPending` is the only progress signal there ever is, for
 * however long that stream takes. A failure always resolves back to
 * `isPending: false` - nothing is left showing "Preparing" forever.
 *
 * A plain hook rather than a TanStack Query mutation: it carries no cache
 * entry worth sharing, and this keeps every caller free of a QueryClient it
 * would otherwise need only for this one, fire-and-forget request.
 */
export function useDownloadExport(path: string): DownloadExport {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<unknown>(undefined);
  // The one thing a TanStack Query mutation gives for free: a settled request must
  // not update state once its own caller has unmounted (e.g. navigating away from
  // the export page mid-stream). The download itself still completes either way.
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const mutate = useCallback(
    (_variables?: undefined, options?: DownloadExportOptions) => {
      setIsPending(true);
      setError(undefined);
      streamToDisk(path)
        .then(() => {
          if (mountedRef.current) setIsPending(false);
        })
        .catch((thrown: unknown) => {
          if (mountedRef.current) {
            setIsPending(false);
            setError(thrown);
          }
          options?.onError?.(thrown);
        });
    },
    [path],
  );

  return { isPending, isError: error !== undefined, error, mutate };
}
