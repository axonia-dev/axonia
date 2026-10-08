import { Switch } from "@/components/ui/Switch";
import { TextInput } from "@/components/ui/TextInput";

import {
  AGENT_SCOPES,
  type AgentScopeCode,
} from "../domain/personal-access-token";

/** A scope that only ever looks. The roster is one of these, so it must not read as a write. */
function isReadScope(code: AgentScopeCode): boolean {
  return code === "read" || code.endsWith(":read");
}

const READ_SCOPES = AGENT_SCOPES.filter((scope) => isReadScope(scope.code));
const WRITE_SCOPES = AGENT_SCOPES.filter((scope) => !isReadScope(scope.code));

interface ScopeFieldsetProps {
  legend: string;
  scopes: readonly { readonly code: AgentScopeCode; readonly label: string }[];
  selected: AgentScopeCode[];
  onToggle: (code: AgentScopeCode, on: boolean) => void;
}

function ScopeFieldset({
  legend,
  scopes,
  selected,
  onToggle,
}: Readonly<ScopeFieldsetProps>) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-foreground text-sm font-medium">{legend}</legend>
      {scopes.map((scope) => (
        <Switch
          key={scope.code}
          label={scope.label}
          isSelected={selected.includes(scope.code)}
          onChange={(on) => onToggle(scope.code, on)}
        />
      ))}
    </fieldset>
  );
}

export interface ScopeFieldsProps {
  scopes: AgentScopeCode[];
  onScopesChange: (scopes: AgentScopeCode[]) => void;
}

export function ScopeFields({
  scopes,
  onScopesChange,
}: Readonly<ScopeFieldsProps>) {
  function toggleScope(code: AgentScopeCode, on: boolean) {
    onScopesChange(
      on ? [...scopes, code] : scopes.filter((scope) => scope !== code),
    );
  }

  return (
    <>
      <ScopeFieldset
        legend="Read permissions"
        scopes={READ_SCOPES}
        selected={scopes}
        onToggle={toggleScope}
      />
      <ScopeFieldset
        legend="Write permissions"
        scopes={WRITE_SCOPES}
        selected={scopes}
        onToggle={toggleScope}
      />
    </>
  );
}

export interface TokenFieldsProps {
  name: string;
  onNameChange: (name: string) => void;
  scopes: AgentScopeCode[];
  onScopesChange: (scopes: AgentScopeCode[]) => void;
}

export function TokenFields({
  name,
  onNameChange,
  scopes,
  onScopesChange,
}: Readonly<TokenFieldsProps>) {
  return (
    <>
      <TextInput
        label="Name"
        value={name}
        onChange={onNameChange}
        isRequired
        placeholder="e.g. Claude desktop"
      />
      <ScopeFields scopes={scopes} onScopesChange={onScopesChange} />
    </>
  );
}
