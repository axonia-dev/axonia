import "@/boot";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import App from "@/App";
import { selfHostedEdition } from "@/selfhosted/edition";

// No telemetry: the collector is the Cloud's, and an installation is sent nothing it did not ask for.
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App edition={selfHostedEdition} />
  </StrictMode>,
);
