import { ArrowRight } from "lucide-react";
import type { IconName } from "lucide-react/dynamic";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { AccentIcon } from "@/components/ui/AccentIcon";

interface SectionCardProps {
  title: string;
  icon: IconName;
  accent: string;
  action?: { label: string; to: string };
  children: ReactNode;
}

/** The unified surface for every section on the community home. */
export function SectionCard({
  title,
  icon,
  accent,
  action,
  children,
}: Readonly<SectionCardProps>) {
  return (
    <section className="border-border bg-surface rounded-lg border shadow-sm">
      <header className="flex items-center justify-between gap-3 px-4 pt-4 pb-2">
        <h2 className="font-display text-foreground flex min-w-0 items-center gap-2 text-base font-semibold">
          <AccentIcon
            name={icon}
            accent={accent}
            size={15}
            className="size-7"
          />
          {title}
        </h2>
        {action && (
          <Link
            to={action.to}
            className="text-primary inline-flex shrink-0 items-center gap-1 text-sm font-medium whitespace-nowrap hover:underline"
          >
            {action.label}
            <ArrowRight size={13} />
          </Link>
        )}
      </header>
      <div className="px-2 pb-3">{children}</div>
    </section>
  );
}
