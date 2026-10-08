import { Button, Dialog, DialogTrigger, Popover } from "react-aria-components";

import { COMMUNITY_PALETTE } from "@/lib/kind-hue";

interface AccentControlProps {
  value: string | null | undefined;
  onChange: (value: string | null) => void;
}

export function AccentControl({
  value,
  onChange,
}: Readonly<AccentControlProps>) {
  return (
    <DialogTrigger>
      <Button
        aria-label="Accent color"
        className="bg-surface border-border-strong inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-semibold shadow-sm transition-transform duration-150 hover:scale-105 hover:shadow-md active:scale-95"
      >
        <span
          className="size-4 rounded-full"
          style={{ background: value ?? "var(--primary)" }}
        />
        Accent
      </Button>
      <Popover>
        <Dialog className="bg-surface border-border w-56 rounded-xl border p-3 shadow-md outline-none">
          <p className="text-muted-foreground mb-2 text-xs font-semibold tracking-wide uppercase">
            Accent color
          </p>
          <div className="mb-3 flex flex-wrap gap-2">
            {COMMUNITY_PALETTE.map((hex) => (
              <Button
                key={hex}
                aria-label={`Accent ${hex}`}
                onPress={() => onChange(hex)}
                className={
                  value?.toLowerCase() === hex
                    ? "size-7 cursor-pointer rounded-full ring-2 ring-[var(--foreground)] ring-offset-2 ring-offset-[var(--surface)] transition-transform duration-150 hover:scale-110 active:scale-95"
                    : "size-7 cursor-pointer rounded-full ring-1 ring-[var(--border-strong)] transition-transform duration-150 hover:scale-110 active:scale-95"
                }
                style={{ background: hex }}
              />
            ))}
          </div>
          <input
            type="text"
            defaultValue={value ?? ""}
            placeholder="#1AA6A0"
            aria-label="Accent hex value"
            onChange={(e) => {
              const next = e.target.value.trim();
              if (/^#[0-9A-Fa-f]{6}$/.test(next)) {
                onChange(next);
              }
            }}
            className="border-border-strong w-full rounded-md border px-2.5 py-1.5 text-sm"
          />
          <Button
            onPress={() => onChange(null)}
            className="text-muted-foreground hover:text-foreground mt-2 cursor-pointer text-sm transition-colors"
          >
            Clear
          </Button>
        </Dialog>
      </Popover>
    </DialogTrigger>
  );
}
