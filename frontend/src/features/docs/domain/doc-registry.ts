import AgentOverview from "../content/agent-overview.mdx";
import ConnectYourAgent from "../content/connect-your-agent.mdx";
import type { DocPage } from "./doc-page";

export const DOC_PAGES: readonly DocPage[] = [
  {
    slug: "agent-overview",
    title: "Overview",
    group: "Agent",
    Component: AgentOverview,
  },
  {
    slug: "connect-your-agent",
    title: "Connect an agent",
    group: "Agent",
    Component: ConnectYourAgent,
  },
];

export function findDocPage(slug: string): DocPage | undefined {
  return DOC_PAGES.find((page) => page.slug === slug);
}
