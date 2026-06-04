// Full end-to-end stream resolution "as if the app": generate a BotGuard poToken,
// run the player request, decipher signature + n, and validate the stream — per client,
// with timing for each stage. Shows where the per-song time actually goes and how the
// web (poToken+decipher) path compares to the direct-URL (iOS/ANDROID) path.
//
//   node tests/full-stream.mjs
//   VIDEO_IDS=a,b CRED_URL=... node tests/full-stream.mjs
//
// Uses youtubei.js (full InnerTube + decipher) + bgutils-js (poToken). This is pure-JVM-
// equivalent (Rhino-style) decipher in-process — i.e. the path Metrolist uses — NOT the
// app's Android WebView path, so the timings are the floor the app *should* hit.

import { Innertube } from "youtubei.js";
import { BG } from "bgutils-js";
import { JSDOM } from "jsdom";

const VIDEO_IDS = (process.env.VIDEO_IDS || "dQw4w9WgXcQ,kJQP7kiw5Fk").split(",");
const CRED_URL = process.env.CRED_URL || "https://mc.alltech.dev/credentials";
const WEB_REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"; // YouTube web BotGuard request key (constant)
const ms = (a, b) => `${(b - a).toFixed(0)}ms`.padStart(7);

async function getCred() {
  try { return await (await fetch(CRED_URL)).json(); } catch { return {}; }
}

async function genPoToken(visitorData) {
  const dom = new JSDOM();
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  const bgConfig = { fetch: (u, o) => fetch(u, o), globalObj: globalThis, identifier: visitorData, requestKey: WEB_REQUEST_KEY };
  const challenge = await BG.Challenge.create(bgConfig);
  if (!challenge) throw new Error("no challenge");
  const interpreterJs = challenge.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue;
  if (!interpreterJs) throw new Error("no interpreter");
  new Function(interpreterJs)();
  const res = await BG.PoToken.generate({ program: challenge.program, globalName: challenge.globalName, bgConfig });
  return res.poToken;
}

(async () => {
  const cred = await getCred();
  const visitorData = cred.visitorData || undefined;
  console.log(`cred: cookie=${cred.cookie ? "yes" : "no"} visitorData=${(visitorData || "").slice(0, 14)}…\n`);

  let t = performance.now();
  let poToken;
  try { poToken = await genPoToken(visitorData); console.log(`poToken gen        ${ms(t, performance.now())}  ${poToken.slice(0, 22)}…  (one-time)`); }
  catch (e) { console.log(`poToken gen        FAILED (${e.message}) — web clients will be throttled/unplayable`); }

  t = performance.now();
  const yt = await Innertube.create({ po_token: poToken, visitor_data: visitorData, cookie: cred.cookie, retrieve_player: true });
  console.log(`Innertube.create   ${ms(t, performance.now())}  (downloads+parses base.js, one-time)\n`);

  const CLIENTS = ["YTMUSIC", "WEB", "iOS", "ANDROID", "TV"];
  for (const videoId of VIDEO_IDS) {
    console.log(`=== ${videoId} ===`);
    console.log("client".padEnd(9), "status".padEnd(13), "getInfo".padEnd(9), "decipher".padEnd(9), "stream", "itag");
    for (const client of CLIENTS) {
      try {
        const t0 = performance.now();
        const info = await yt.getInfo(videoId, client);
        const t1 = performance.now();
        let url = null, ciphered = false;
        const fmt = info.chooseFormat({ type: "audio", quality: "best" });
        try {
          if (fmt?.url) { url = fmt.url; }
          else { url = fmt?.decipher(yt.session.player); ciphered = true; }
        } catch { url = null; }
        const t2 = performance.now();
        let stream = "-";
        if (url) { try { const r = await fetch(url, { headers: { Range: "bytes=0-1" } }); r.body?.cancel?.(); stream = String(r.status); } catch { stream = "ERR"; } }
        console.log(
          client.padEnd(9),
          String(info.playability_status?.status || "-").padEnd(13),
          ms(t0, t1).padEnd(9),
          (ciphered ? ms(t1, t2) : "direct").padEnd(9),
          String(stream).padEnd(6),
          fmt?.itag ?? "-",
        );
      } catch (e) {
        console.log(client.padEnd(9), "ERROR", String(e.message).slice(0, 60));
      }
    }
    console.log();
  }
  process.exit(0);
})();
