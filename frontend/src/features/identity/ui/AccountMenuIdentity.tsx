import { MemberAvatar } from "@/components/patterns/MemberAvatar";

export interface IdentityProps {
  name: string;
  email: string | undefined;
  hue: string;
  avatarUrl: string | null;
  subject: string | null;
}

/** The menu's own identity card: avatar centred above the name and email, on
 *  `--surface-sunken` so it reads apart from the rows below it. */
export function IdentityCard({
  name,
  email,
  hue,
  avatarUrl,
  subject,
}: Readonly<IdentityProps>) {
  return (
    <div className="bg-surface-sunken mb-1 flex flex-col items-center gap-2 rounded-sm px-3 py-4 text-center">
      <MemberAvatar
        name={name}
        hue={hue}
        subject={subject}
        shape="circle"
        src={avatarUrl}
        className="size-12"
      />
      <span className="flex min-w-0 flex-col items-center">
        <span className="text-foreground truncate text-sm font-medium">
          {name}
        </span>
        {email && (
          <span className="text-muted-foreground truncate text-xs">
            {email}
          </span>
        )}
      </span>
    </div>
  );
}
