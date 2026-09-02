// Egress control for the harness: route EVERY fetch (InnerTube /player, the CDN drains, pot
// minting) through SCAN_PROXY when set, so an unattended run on a datacenter runner streams from
// the same kind of network the app does. GitHub-hosted runners are bot-gated for anonymous
// requests ("Sign in to confirm you're not a bot" on every login-less client) — that is the
// runner's IP reputation, not the client, and a scan without residential egress is inconclusive
// for those clients. Import this module FIRST (side effect only).
//
//   SCAN_PROXY=http://user:pass@host:port   (or socks5://...) — a residential/mobile egress

//   FORCE_IPV4=1 / FORCE_IPV6=1 - pin the address family. On WARP this matters: its IPv6 pool
//                    passes YouTube's gate and CDN, its shared IPv4 pool does not (UNPLAYABLE /
//                    walled drains), and node's happy-eyeballs may otherwise pick v4.
import { Agent, ProxyAgent, setGlobalDispatcher } from "undici";

const url = process.env.SCAN_PROXY;
const v4 = process.env.FORCE_IPV4 === "1";
const v6 = process.env.FORCE_IPV6 === "1";
// A tunnelled egress (WARP, a proxy) adds latency and flaps: undici's 10 s connect timeout tripped
// on the very first youtube.com fetch of a CI drain. 30 s to connect, and every fetch retries a
// NETWORK-class failure (connect timeout, reset, DNS hiccup) a few times with backoff — a /player
// POST and a CDN range GET are both safe to repeat; nothing here retries an HTTP status.
const connect = { timeout: 30000, ...(v4 ? { family: 4 } : v6 ? { family: 6 } : {}) };
// A stalled tunnel must fail fast enough to be retried, not hang a slot: 60 s to first byte.
const agentOpts = { connect, headersTimeout: 60000, bodyTimeout: 120000 };
if (url) {
  setGlobalDispatcher(new ProxyAgent({ uri: url, ...agentOpts }));
  console.error(`egress: via proxy ${url.replace(/\/\/.*@/, "//<credentials>@")}`);
} else {
  setGlobalDispatcher(new Agent(agentOpts));
  if (v4) console.error("egress: IPv4 only");
  if (v6) console.error("egress: IPv6 only");
}

const NETWORK_ERR = /UND_ERR_CONNECT_TIMEOUT|UND_ERR_HEADERS_TIMEOUT|UND_ERR_BODY_TIMEOUT|UND_ERR_SOCKET|Headers Timeout|Body Timeout|ECONNRESET|ECONNREFUSED|EAI_AGAIN|ENOTFOUND|ETIMEDOUT|EHOSTUNREACH|ENETUNREACH|EPIPE|socket hang up|fetch failed|other side closed|terminated/i;
const describe = (e) => `${e?.cause?.code || e?.code || ""} ${e?.cause?.message || e?.message || e}`;
export function isNetworkError(e) { return NETWORK_ERR.test(describe(e)); }

/** Wrap a fetch so network-class failures retry (attempts total, backoff*attempt ms between). */
export function withRetries(baseFetch, { attempts = 4, backoffMs = 2000, sleep = (ms) => new Promise((r) => setTimeout(r, ms)), log = console.error } = {}) {
  return async function retryingFetch(input, init) {
    let last;
    for (let attempt = 1; attempt <= attempts; attempt++) {
      try { return await baseFetch(input, init); }
      catch (e) {
        last = e;
        if (!isNetworkError(e) || attempt === attempts) throw e;
        log(`egress: ${describe(e).trim()} on ${String(input).slice(0, 60)} — retry ${attempt}/${attempts - 1}`);
        await sleep(backoffMs * attempt);
      }
    }
    throw last;
  };
}
if (!process.env.EGRESS_NO_RETRY) globalThis.fetch = withRetries(globalThis.fetch);
