// Egress control for the harness: route EVERY fetch (InnerTube /player, the CDN drains, pot
// minting) through SCAN_PROXY when set, so an unattended run on a datacenter runner streams from
// the same kind of network the app does. GitHub-hosted runners are bot-gated for anonymous
// requests ("Sign in to confirm you're not a bot" on every login-less client) — that is the
// runner's IP reputation, not the client, and a scan without residential egress is inconclusive
// for those clients. Import this module FIRST (side effect only).
//
//   SCAN_PROXY=http://user:pass@host:port   (or socks5://...) — a residential/mobile egress

import { ProxyAgent, setGlobalDispatcher } from "undici";

const url = process.env.SCAN_PROXY;
if (url) {
  setGlobalDispatcher(new ProxyAgent(url));
  console.error(`egress: via proxy ${url.replace(/\/\/.*@/, "//<credentials>@")}`);
}
