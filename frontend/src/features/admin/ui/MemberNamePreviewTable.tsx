import type { MemberNamePreviewRow } from "@/features/admin/domain/member-names";

export function PreviewTable({
  rows,
}: Readonly<{ rows: ReadonlyArray<MemberNamePreviewRow> }>) {
  if (rows.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">No members to preview.</p>
    );
  }
  return (
    <div className="border-border overflow-x-auto rounded-md border">
      <table className="w-full text-left text-sm">
        <thead className="bg-surface-sunken text-muted-foreground text-xs">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">
              Received
            </th>
            <th scope="col" className="px-3 py-2 font-medium">
              Shown as
            </th>
            <th scope="col" className="px-3 py-2 font-medium">
              Given name
            </th>
            <th scope="col" className="px-3 py-2 font-medium">
              Pattern
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.received} className="border-border border-t">
              <td className="px-3 py-2">{row.received}</td>
              <td className="px-3 py-2">{row.displayName}</td>
              <td className="px-3 py-2">{row.givenName ?? ""}</td>
              <td className="text-muted-foreground px-3 py-2">
                {row.matched === null
                  ? "Unchanged"
                  : `Pattern ${row.matched + 1}`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
