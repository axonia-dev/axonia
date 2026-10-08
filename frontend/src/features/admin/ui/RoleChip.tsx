import type { CSSProperties } from "react";

import { cn } from "@/lib/cn";
import { kindHue } from "@/lib/kind-hue";

interface RoleChipProps {
  roleName: string;
  roleId: number;
  via: string;
}

export function RoleChip({ roleName, roleId, via }: Readonly<RoleChipProps>) {
  return (
    <span
      style={{ "--k": kindHue(String(roleId)) } as CSSProperties}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap",
        "border-[color-mix(in_srgb,var(--k)_35%,transparent)] bg-[color-mix(in_srgb,var(--k)_22%,var(--surface))] text-[color-mix(in_srgb,var(--k)_90%,var(--foreground))]",
      )}
    >
      {roleName}
      {via === "GROUP" && (
        <span className="text-muted-foreground font-medium">· via group</span>
      )}
    </span>
  );
}
