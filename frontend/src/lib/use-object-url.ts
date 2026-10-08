import { useEffect, useMemo } from "react";

/** An object URL for `file`, recreated whenever `file` changes and revoked on every
 *  change and on unmount - a caller that skips this leaks the underlying blob. */
export function useObjectUrl(file: File | null): string | null {
  const url = useMemo(
    () => (file === null ? null : URL.createObjectURL(file)),
    [file],
  );

  useEffect(() => {
    return () => {
      if (url !== null) URL.revokeObjectURL(url);
    };
  }, [url]);

  return url;
}
