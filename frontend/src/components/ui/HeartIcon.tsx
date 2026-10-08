import { Heart } from "lucide-react";

/**
 * A flat heart, sized for inline use beside a love count. Hollow by default
 * (an un-loved outline); pass `filled` for the loved state, where it fills with
 * currentColor so it picks up the surrounding text colour — primary when loved.
 * Decorative: the meaning lives in the button's accessible label, so the icon is
 * hidden from assistive tech.
 */
export function HeartIcon({
  filled = false,
  className,
}: Readonly<{ filled?: boolean; className?: string }>) {
  return (
    <Heart
      size={15}
      fill={filled ? "currentColor" : "none"}
      aria-hidden
      className={className}
    />
  );
}
