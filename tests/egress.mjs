// Egress control for the harness: route EVERY fetch (InnerTube /player, the CDN drains, pot
// minting) through SCAN_PROXY when set, so an unattended run on a datacenter runner streams from
// the same kind of network the app does. GitHub-hosted runners are bot-gated for anonymous
// requests ("Sign in to confirm you're not a bot" on every login-less client) — that is the
// runner's IP reputation, not the client, and a scan without residential egress is inconclusive
// for those clients. Import this module FIRST (side effect only).
//
//   SCAN_PROXY=http://user:pass@host:port   (or socks5://...) — a residential/mobile egress

//   FORCE_IPV4=1   - connect over IPv4 only (a WARP/proxy egress hands out IPv6 by default, and
//                    YouTube's bot gate scores v6 pools differently from v4 ones)
import { Agent, ProxyAgent, setGlobalDispatcher } from "undici";

const url = process.env.SCAN_PROXY;
const v4 = process.env.FORCE_IPV4 === "1";
if (url) {
  setGlobalDispatcher(new ProxyAgent(url));
  console.error(`egress: via proxy ${url.replace(/\/\/.*@/, "//<credentials>@")}`);
} else if (v4) {
  setGlobalDispatcher(new Agent({ connect: { family: 4 } }));
  console.error("egress: IPv4 only");
}
