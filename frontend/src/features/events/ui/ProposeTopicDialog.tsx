import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { ProposeTopicForm } from "@/features/events/ui/ProposeTopicForm";

export function ProposeTopicDialog({ slug }: Readonly<{ slug: string }>) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button size="sm" onPress={() => setOpen(true)}>
        + Add new
      </Button>
      <Dialog isOpen={open} onOpenChange={setOpen} title="Propose a topic">
        <ProposeTopicForm slug={slug} onProposed={() => setOpen(false)} />
      </Dialog>
    </>
  );
}
