// Retest the web clients (WEB_REMIX, TVHTML5) old vs current clientVersion,
// to separate "stale client version" from "needs poToken". No poToken sent.
import crypto from "node:crypto";

const UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const UA_TV = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15";
const ORIGIN = "https://music.youtube.com";
const VID = process.env.VIDEO_ID || "dQw4w9WgXcQ";
const STS = process.env.STS || "20602";

async function getCred() {
  return await (await fetch(process.env.CRED_URL || "https://mc.alltech.dev/credentials")).json();
}
async function currentVersion(url, ua) {
  try {
    const h = await (await fetch(url, { headers: { "User-Agent": ua, "Accept-Language": "en-US,en" } })).text();
    return h.match(/"INNERTUBE_CLIENT_VERSION":"([\d.]+)"/)?.[1];
  } catch { return null; }
}
function sapisid(cookie) {
  const m = cookie.match(/(?:^|; )SAPISID=([^;]+)/);
  if (!m) return null;
  const ts = Math.floor(Date.now() / 1000);
  return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${ORIGIN}`).digest("hex")}`;
}
async function player(cn, cid, cv, ua, { cookie, auth, vd } = {}) {
  const client = { clientName: cn, clientVersion: cv, hl: "en", gl: "US" };
  if (vd) client.visitorData = vd;
  const body = { context: { client }, videoId: VID, contentCheckOk: true, racyCheckOk: true,
    playbackContext: { contentPlaybackContext: { signatureTimestamp: Number(STS) } } };
  const h = { "Content-Type": "application/json", "X-Goog-Api-Format-Version": "1", "X-YouTube-Client-Name": cid,
    "X-YouTube-Client-Version": cv, "X-Origin": ORIGIN, Referer: ORIGIN + "/", "User-Agent": ua };
  if (vd) h["X-Goog-Visitor-Id"] = vd;
  if (auth && cookie) { h.cookie = cookie; const a = sapisid(cookie); if (a) h.Authorization = a; }
  const r = await fetch(ORIGIN + "/youtubei/v1/player?prettyPrint=false", { method: "POST", headers: h, body: JSON.stringify(body) });
  let j = {}; try { j = JSON.parse(await r.text()); } catch {}
  return { http: r.status, ps: j?.playabilityStatus?.status, reason: j?.playabilityStatus?.reason || "", nf: (j?.streamingData?.adaptiveFormats || []).length };
}

(async () => {
  const cred = await getCred();
  const tvNew = await currentVersion("https://www.youtube.com/tv", UA_TV);
  const remixNew = await currentVersion("https://music.youtube.com/", UA_WEB);
  console.log(`current: WEB_REMIX=${remixNew}  TVHTML5=${tvNew}  STS=${STS}\n`);
  const matrix = [
    ["WEB_REMIX", "67", "1.20260213.01.00", UA_WEB, "OLD"],
    ["WEB_REMIX", "67", remixNew || "1.20260531.05.00", UA_WEB, "NEW"],
    ["TVHTML5", "7", "7.20260213.00.00", UA_TV, "OLD"],
    ["TVHTML5", "7", tvNew || "7.20260213.00.00", UA_TV, "NEW"],
  ];
  for (const [cn, cid, cv, ua, tag] of matrix) {
    for (const auth of [false, true]) {
      const r = await player(cn, cid, cv, ua, { cookie: cred.cookie, auth, vd: cred.visitorData });
      console.log(`${cn} ${tag} ${cv} [${auth ? "auth" : "anon"}] -> HTTP ${r.http} ${r.ps} "${r.reason}" fmts=${r.nf}`);
      await new Promise((x) => setTimeout(x, 150));
    }
  }
})();
