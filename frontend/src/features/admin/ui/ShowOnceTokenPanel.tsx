import { ShowOnceSecret } from "@/components/ui/ShowOnceSecret";

interface ShowOnceTokenPanelProps {
  token: string;
}

export function ShowOnceTokenPanel({
  token,
}: Readonly<ShowOnceTokenPanelProps>) {
  return (
    <ShowOnceSecret
      label="New SCIM token"
      note="This token is shown only once. Copy it now, it cannot be retrieved again."
      secret={token}
    />
  );
}
