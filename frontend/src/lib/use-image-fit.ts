import { useCallback, useRef, useState } from "react";

import { fitImage, type ImageLimits } from "@/lib/fit-image";

/** Fits picked images one at a time: a pick made later, or discarded, wins over a fit still
 *  in flight, whose result then resolves to null. */
export function useImageFit(limits: ImageLimits) {
  const latest = useRef(0);
  const [fitting, setFitting] = useState(false);

  const discard = useCallback(() => {
    latest.current += 1;
    setFitting(false);
  }, []);

  const fit = useCallback(
    async (file: File) => {
      latest.current += 1;
      const pick = latest.current;
      setFitting(true);
      const fitted = await fitImage(file, limits);
      if (pick !== latest.current) return null;
      setFitting(false);
      return fitted;
    },
    [limits],
  );

  return { fitting, fit, discard };
}
