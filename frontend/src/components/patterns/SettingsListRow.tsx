import type { ReactNode } from "react";

interface SettingsListRowProps {
  /** The row's own content. Free to shrink, so a long value can truncate. */
  children: ReactNode;
  /** Chips and controls at the trailing edge. Held in a `flex-none` row by this component, so
   *  nothing here shrinks to make room for the body however long the body gets. */
  actions?: ReactNode;
}

/**
 * The shell a settings list row shares: a bordered card on the surface, a body that may shrink,
 * and trailing controls that may not.
 *
 * Not {@link ListRow}, which is a Link or a button - a whole-row activation surface with a hover
 * lift and an accent bar. These rows navigate nowhere; they state something and carry their own
 * controls, so an activation surface around them would announce a destination that is not there.
 */
export function SettingsListRow({
  children,
  actions,
}: Readonly<SettingsListRowProps>) {
  return (
    <li className="border-border bg-surface flex items-center gap-3 rounded-lg border px-4 py-3.5">
      <div className="min-w-0 flex-1">{children}</div>
      {/* The row owns the `flex-none` rather than trusting every caller to bring it: the
          guarantee the prop's doc makes is only worth making if it cannot be forgotten. */}
      {actions !== undefined && (
        <div className="flex flex-none items-center gap-3">{actions}</div>
      )}
    </li>
  );
}
