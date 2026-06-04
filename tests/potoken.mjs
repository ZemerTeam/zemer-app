// Reusable BotGuard poToken generator (web client), built on bgutils-js + jsdom.
//
// YouTube web clients (WEB / WEB_REMIX / TVHTML5) need a poToken in two places.
// Bindings verified against the app's PoTokenGenerator.getWebClientPoToken(videoId, sessionId):
//   - streamingDataPoToken : appended to the media URL as &pot=...
//                            content binding = the SESSION id (visitorData). Minted FIRST, once.
//   - playerRequestPoToken : sent in serviceIntegrityDimensions.poToken on the /player request.
//                            content binding = the VIDEO ID. Minted after the streaming token.
// (PoTokenResult(playerPot, streamingPot) = PoTokenResult(generate(videoId), generate(visitorData)).)
// Session id is ALWAYS visitorData — dataSyncId is rejected by BotGuard as a session context.
//
// Run directly to mint + print tokens (uses ../innertube_cookie.txt for visitorData):
//   node tests/potoken.mjs                 # session token bound to visitorData
//   node tests/potoken.mjs <videoId>       # also mint the streaming token bound to <videoId>
//
// Import:
//   import { mintWebPoTokens, generatePoToken } from "./potoken.mjs";

import { BG } from "bgutils-js";
import { JSDOM } from "jsdom";

// Constant request key for YouTube web BotGuard (same value NewPipe/yt-dlp/youtubei.js use).
export const WEB_REQUEST_KEY = "O43z0dpjhgX20SCx4KAo";

let domReady = false;
function ensureDom() {
  if (domReady) return;
  const dom = new JSDOM("<!DOCTYPE html><html><body></body></html>", {
    url: "https://music.youtube.com/",
  });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.location = dom.window.location;
  domReady = true;
}

/**
 * Build a BotGuard integrity-token "minter" with ONE BotGuard run, then mint as
 * many poTokens as needed for different content bindings. This matches how the
 * app mints both the player token (binding=visitorData) and the streaming token
 * (binding=videoId) from a single attestation.
 *
 * @param {string} sessionIdentifier  visitorData (or dataSyncId)
 */
export async function createMinter(sessionIdentifier) {
  ensureDom();
  const bgConfig = {
    fetch: (url, opts) => fetch(url, opts),
    globalObj: globalThis,
    identifier: sessionIdentifier,
    requestKey: WEB_REQUEST_KEY,
  };

  const challenge = await BG.Challenge.create(bgConfig);
  if (!challenge) throw new Error("BotGuard: Challenge.create returned nothing");

  const interpreterJs =
    challenge.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue;
  if (!interpreterJs) throw new Error("BotGuard: no interpreter javascript in challenge");
  // Evaluate the BotGuard VM into the (jsdom) global scope.
  new Function(interpreterJs)();

  return {
    bgConfig,
    program: challenge.program,
    globalName: challenge.globalName,
    /** Mint a poToken for a given content binding (visitorData or videoId). */
    async mint(contentBinding) {
      const res = await BG.PoToken.generate({
        program: challenge.program,
        globalName: challenge.globalName,
        bgConfig: { ...bgConfig, identifier: contentBinding },
      });
      const tok = res?.poToken;
      if (!tok) throw new Error(`poToken mint returned empty for binding=${contentBinding}`);
      return tok;
    },
  };
}

/** One-shot: mint a single poToken bound to `identifier`. */
export async function generatePoToken(identifier) {
  const minter = await createMinter(identifier);
  return minter.mint(identifier);
}

/**
 * Mint the pair the web player path needs.
 * @returns {{ playerRequestPoToken: string, streamingDataPoToken: string|null }}
 */
export async function mintWebPoTokens({ visitorData, videoId }) {
  if (!visitorData) throw new Error("mintWebPoTokens: visitorData is required");
  const minter = await createMinter(visitorData);
  // App order/bindings (PoTokenGenerator): the streaming poToken (bound to the
  // session = visitorData) is minted exactly once, BEFORE the player token.
  const streamingDataPoToken = await minter.mint(visitorData);
  // The player-request poToken is bound to the videoId.
  const playerRequestPoToken = videoId ? await minter.mint(videoId) : null;
  return { playerRequestPoToken, streamingDataPoToken };
}

// ---- CLI ----
if (import.meta.url === `file://${process.argv[1]}`) {
  const { getCred, describeCred } = await import("./cred.mjs");
  const videoId = process.argv[2] || null;
  const cred = await getCred();
  console.error(describeCred(cred));
  if (!cred.visitorData) {
    console.error("ERROR: no visitorData available (set YT_VISITOR_DATA or innertube_cookie.txt)");
    process.exit(1);
  }
  const t0 = performance.now();
  const { playerRequestPoToken, streamingDataPoToken } = await mintWebPoTokens({
    visitorData: cred.visitorData,
    videoId,
  });
  const dt = (performance.now() - t0).toFixed(0);
  console.error(`minted in ${dt}ms`);
  console.log(JSON.stringify(
    {
      visitorData: cred.visitorData,
      videoId,
      playerRequestPoToken,
      streamingDataPoToken,
      playerRequestPoToken_len: playerRequestPoToken?.length ?? 0,
      streamingDataPoToken_len: streamingDataPoToken?.length ?? 0,
    },
    null,
    2,
  ));
  process.exit(0);
}
