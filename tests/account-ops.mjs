#!/usr/bin/env node
// Account read/write harness — reproduces the app's EXACT authenticated InnerTube
// requests (WEB_REMIX, SAPISIDHASH + onBehalfOfUser=dataSyncId, ?prettyPrint=false,
// the Context the app serializes) against live YouTube Music, using the local
// innertube_cookie.txt. Measures what the live API really does for:
//   - account_menu  (the "am I logged in?" check — issue #140)
//   - liked-songs read (VLLM) + saved-playlists read (FEmusic_liked_playlists)  (sync reads)
//   - like / removelike                                                          (likes)
//   - create playlist -> add song -> IMMEDIATE re-read -> delete   (playlist add — issue #130)
//
// Mirrors: InnerTube.kt ytClient()/toContext(), YouTubeClient.WEB_REMIX, Context.kt,
// EditPlaylistBody.kt. Keep in sync if those change.
//
// WRITES to the real account: likes then unlikes one video; creates a temp playlist,
// adds a song, then DELETES the playlist. Net effect is ~zero, but it does touch the
// live account. Run only against a cookie you own.

import { createHash } from "node:crypto";
import { getCred, describeCred } from "./cred.mjs";

const ORIGIN = "https://music.youtube.com";
const API = ORIGIN + "/youtubei/v1/";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
const WEB_REMIX = { clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: "67" };
const TEST_VIDEO = process.env.TEST_VIDEO || "dQw4w9WgXcQ"; // a public, always-available video

// ANON=1 simulates the anonymous/pooled shape: drop onBehalfOfUser (dataSyncId) from the
// context, exactly as the app does when no personal login is present. Proves whether the
// API needs dataSyncId at all, or whether the app's isPersonalAccountSignedIn gate is the
// only thing standing between anon and a working sync.
const ANON = process.env.ANON === "1";

const cred = await getCred();
console.log(describeCred(cred) + (ANON ? "  [ANON MODE: onBehalfOfUser omitted]" : ""));
if (!cred.cookie || !/SAPISID=/.test(cred.cookie)) {
  console.error("No cookie / SAPISID — cannot test account ops.");
  process.exit(2);
}
const SAPISID = cred.cookie.match(/SAPISID=([^;]+)/)[1];

function authHeader() {
  const t = Math.floor(Date.now() / 1000);
  const h = createHash("sha1").update(`${t} ${SAPISID} ${ORIGIN}`).digest("hex");
  return `SAPISIDHASH ${t}_${h}`;
}

// The Context the app serializes (encodeDefaults=true, explicitNulls=false):
// client + request{} defaults + user{lockedSafetyMode:false, onBehalfOfUser:dataSyncId}.
function context() {
  return {
    client: {
      clientName: WEB_REMIX.clientName,
      clientVersion: WEB_REMIX.clientVersion,
      gl: "US",
      hl: "en-US",
      visitorData: cred.visitorData || undefined,
    },
    request: { internalExperimentFlags: [], useSsl: true },
    user: {
      lockedSafetyMode: false,
      // loginSupported=true for WEB_REMIX, so the app attaches dataSyncId here.
      ...(!ANON && cred.dataSyncId ? { onBehalfOfUser: cred.dataSyncId } : {}),
    },
  };
}

async function call(endpoint, body, { setLogin = true, sendVisitorData = true } = {}) {
  const headers = {
    "Content-Type": "application/json",
    "X-Goog-Api-Format-Version": "1",
    "X-YouTube-Client-Name": WEB_REMIX.clientId,
    "X-YouTube-Client-Version": WEB_REMIX.clientVersion,
    "X-Origin": ORIGIN,
    "Referer": ORIGIN + "/",
    "User-Agent": UA,
  };
  if (sendVisitorData && cred.visitorData) headers["X-Goog-Visitor-Id"] = cred.visitorData;
  if (setLogin) {
    headers["cookie"] = cred.cookie;
    headers["Authorization"] = authHeader();
  }
  const res = await fetch(`${API}${endpoint}?prettyPrint=false`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  let json = null, text = null;
  try { json = await res.json(); } catch { try { text = await res.text(); } catch {} }
  return { status: res.status, ok: res.ok, json, text };
}

// ---- generic recursive helpers over the renderer tree -------------------------------
function collect(obj, key, out = []) {
  if (obj == null || typeof obj !== "object") return out;
  if (Array.isArray(obj)) { for (const v of obj) collect(v, key, out); return out; }
  for (const [k, v] of Object.entries(obj)) {
    if (k === key) out.push(v);
    collect(v, key, out);
  }
  return out;
}
const runsText = (r) => (r?.runs ? r.runs.map((x) => x.text).join("") : r?.simpleText ?? "");

// ---- the tests ----------------------------------------------------------------------
const log = (...a) => console.log(...a);
const line = () => log("─".repeat(72));
const results = [];
function record(name, ok, detail) { results.push({ name, ok }); log(`${ok ? "✅" : "❌"} ${name}${detail ? " — " + detail : ""}`); }

line();
log("T1  account_menu  (is this session actually logged into YouTube? — issue #140)");
{
  const r = await call("account/account_menu", { context: context() });
  const names = collect(r.json, "accountName").map(runsText).filter(Boolean);
  const emails = collect(r.json, "accountEmail").map(runsText).filter(Boolean);
  const handles = collect(r.json, "channelHandle").map(runsText).filter(Boolean);
  const signedIn = names.length > 0;
  record("account_menu returns a signed-in account", signedIn && r.ok,
    `HTTP ${r.status}; account=${names[0] || "(none)"} ${emails[0] || ""} ${handles[0] || ""}`);
}

line();
log("T2  read liked-songs playlist VLLM  (sync read path)");
let likedCount = 0;
{
  const r = await call("browse", { context: context(), browseId: "VLLM" });
  const ids = new Set(collect(r.json, "videoId"));
  likedCount = ids.size;
  record("liked-songs (VLLM) loads", r.ok && likedCount >= 0, `HTTP ${r.status}; ${likedCount} distinct videoIds`);
}

line();
log("T3  read saved playlists FEmusic_liked_playlists  (the #140 'playlist not synced' read)");
{
  const r = await call("browse", { context: context(), browseId: "FEmusic_liked_playlists" });
  const items = collect(r.json, "musicTwoRowItemRenderer");
  const titles = items.map((i) => runsText(i.title)).filter(Boolean);
  record("saved playlists load", r.ok, `HTTP ${r.status}; ${titles.length} playlists: ${titles.slice(0, 8).join(" | ")}${titles.length > 8 ? " …" : ""}`);
}

line();
log("T4  like then removelike a video  (likes write path)");
{
  const like = await call("like/like", { context: context(), target: { videoId: TEST_VIDEO } });
  const likeStatus = (collect(like.json, "status")[0]) || (like.ok ? "(ok, no status field)" : "(none)");
  record(`like/like ${TEST_VIDEO}`, like.ok, `HTTP ${like.status}; status=${likeStatus}`);
  const unlike = await call("like/removelike", { context: context(), target: { videoId: TEST_VIDEO } });
  record(`like/removelike ${TEST_VIDEO} (restore)`, unlike.ok, `HTTP ${unlike.status}`);
}

line();
log("T5  create playlist → add song → IMMEDIATE re-read → delete  (issue #130 smoking gun)");
{
  const title = `zemer-diag-${Math.floor(Date.now() / 1000)}`;
  const create = await call("playlist/create", { context: context(), title });
  const newId = collect(create.json, "playlistId")[0];
  record(`create playlist "${title}"`, create.ok && !!newId, `HTTP ${create.status}; playlistId=${newId || "(none)"}`);

  if (newId) {
    const add = await call("browse/edit_playlist", {
      context: context(),
      playlistId: newId.replace(/^VL/, ""),
      actions: [{ action: "ACTION_ADD_VIDEO", addedVideoId: TEST_VIDEO }],
    });
    const addStatus = collect(add.json, "status")[0] || "(none)";
    const addOk = add.ok && /SUCCEED/i.test(addStatus);
    record(`add ${TEST_VIDEO} to playlist`, addOk, `HTTP ${add.status}; status=${addStatus}`);

    // IMMEDIATE re-read — the exact thing the app's sync does after a local add.
    const read = await call("browse", { context: context(), browseId: `VL${newId.replace(/^VL/, "")}` });
    const ids = collect(read.json, "videoId");
    const setVideoIds = collect(read.json, "playlistSetVideoId");
    const present = ids.includes(TEST_VIDEO);
    record("IMMEDIATE re-read shows the just-added song", present,
      `HTTP ${read.status}; videoIds=[${[...new Set(ids)].join(",")}] setVideoId=${setVideoIds[0] || "(none)"}`);

    // Does the new playlist show up in the saved-playlists list right away? (#140 angle)
    const saved = await call("browse", { context: context(), browseId: "FEmusic_liked_playlists" });
    const savedTitles = collect(saved.json, "musicTwoRowItemRenderer").map((i) => runsText(i.title));
    record("new playlist appears in saved-playlists immediately", savedTitles.includes(title),
      `seen=${savedTitles.includes(title)}`);

    const del = await call("playlist/delete", { context: context(), playlistId: newId.replace(/^VL/, "") });
    record(`cleanup: delete playlist`, del.ok, `HTTP ${del.status}`);
  }
}

line();
const passed = results.filter((r) => r.ok).length;
log(`SUMMARY: ${passed}/${results.length} checks passed.`);
log("Interpretation:");
log("  • T1 signed-in but app says 'not logged in' ⇒ bug is client-side gating (dataSyncId), not the API.");
log("  • T5 add SUCCEEDS but IMMEDIATE re-read MISSING ⇒ eventual-consistency window; the app's");
log("    sync (clearPlaylist → re-insert only remote songs) wipes the fresh local add. (#130)");
log("  • T5 add SUCCEEDS and re-read SHOWS it ⇒ #130 is purely local sync clobber, independent of API lag.");
