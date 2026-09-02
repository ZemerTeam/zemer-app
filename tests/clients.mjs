// YouTube client definitions for the harness.
//
// The STREAM chain (main + fallbacks, in order) is LOADED from the single source of truth the
// app bundles and devices fetch remotely:
//   cipher/library/src/main/assets/stream_clients.json   (see stream-clients.mjs)
// so the harness always tests exactly the chain a device runs — the old hand-maintained mirror
// of YouTubeClient.kt is gone.
//
// WEB stays hand-defined here: it is NOT a stream client (InnerTube next/transcript only) and
// deliberately lives outside the remote table; its definition mirrors YouTubeClient.WEB.

import { loadStreamClients } from "./stream-clients.mjs";

export const USER_AGENT_WEB =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

// Browse-only client (mirrors YouTubeClient.WEB — keep in sync with that one definition).
const WEB = {
  key: "WEB", clientName: "WEB", clientVersion: "2.20260213.00.00", clientId: "1",
  userAgent: USER_AGENT_WEB, loginSupported: false,
  useSignatureTimestamp: false, useWebPoTokens: false,
};

/** The stream chain in table order — entry 0 is the app's main client. */
export const STREAM_CLIENTS = loadStreamClients().clients;

/** All clients scripts may reference by key: WEB + the stream chain. */
export const CLIENTS = [WEB, ...STREAM_CLIENTS];

export const ORIGIN = "https://music.youtube.com";
export const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
