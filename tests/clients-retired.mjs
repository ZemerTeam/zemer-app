// RETIRED client definitions — clients REMOVED from the app (YouTubeClient.kt) as proven dead,
// kept here so the historical investigation scripts that probed them still run as records.
// Verdicts (2026-08-15, app-exact protocol + whole-song drain, tests/client-fulldownload.mjs):
//   IOS / IPADOS ......... 403-wall past the ~1 MiB free window (web pot can't satisfy iOS
//                          attestation); yt-dlp-master ios (21.26.4) is SABR-only.
//   ANDROID (MOBILE) ..... HTTP 400 INVALID_ARGUMENT when the request carries auth; SABR-only
//                          without auth. Dead in both session states.
//   ANDROID_NO_SDK ....... SABR-only (it IS the anonymous ANDROID request).
//   ANDROID_CREATOR ...... HTTP 400 with auth, LOGIN_REQUIRED without. Dead both ways.
//   ANDROID_VR 1.61.48/1.43.32 (all variants) ... "Sign in to confirm you're not a bot" at
//                          /player — the gate keys on the VERSION (probed the old versions under
//                          the eureka UA: still gated). Only 1.65.10 passes; it lives in clients.mjs.
//   TVHTML5_SIMPLY_EMBEDDED_PLAYER ... server-killed ("YouTube is no longer supported in this
//                          application or device").
// Do NOT move these back into clients.mjs — that file mirrors the app's live registry.

import { CLIENTS } from "./clients.mjs";

export const RETIRED = [
  // SABR-only since ~2026-07 (serverAbrStreamingUrl, zero url/signatureCipher) — the app has no SABR support.
{ key: "TVHTML5", clientName: "TVHTML5", clientVersion: "7.20260213.00.00", clientId: "7",
    userAgent: "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
    loginSupported: true, loginRequired: true, useSignatureTimestamp: true, useWebPoTokens: true },

  { key: "IOS", clientName: "IOS", clientVersion: "21.03.1", clientId: "5",
    userAgent: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
    osVersion: "18.2.22C152", loginSupported: false, useSignatureTimestamp: false },

  { key: "IPADOS", clientName: "IOS", clientVersion: "21.03.3", clientId: "5",
    userAgent: "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
    osName: "iPadOS", osVersion: "17.7.10.21H450", deviceMake: "Apple", deviceModel: "iPad7,6",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "ANDROID", clientName: "ANDROID", clientVersion: "21.03.38", clientId: "3",
    userAgent: "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
    loginSupported: true, useSignatureTimestamp: true },

  { key: "ANDROID_NO_SDK", clientName: "ANDROID", clientVersion: "21.03.38", clientId: "3",
    userAgent: "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "ANDROID_CREATOR", clientName: "ANDROID_CREATOR", clientVersion: "25.03.101", clientId: "14",
    userAgent: "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
    osName: "Android", osVersion: "15", deviceMake: "Google", deviceModel: "Pixel 9 Pro Fold", androidSdkVersion: "35",
    loginSupported: true, useSignatureTimestamp: true },

  { key: "ANDROID_VR_NO_AUTH", clientName: "ANDROID_VR", clientVersion: "1.61.48", clientId: "28",
    userAgent: "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "ANDROID_VR_1_61_48", clientName: "ANDROID_VR", clientVersion: "1.61.48", clientId: "28",
    userAgent: "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
    osName: "Android", osVersion: "12", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: "32",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "ANDROID_VR_1_43_32", clientName: "ANDROID_VR", clientVersion: "1.43.32", clientId: "28",
    userAgent: "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
    osName: "Android", osVersion: "12", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: "32",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientName: "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientVersion: "2.0", clientId: "85",
    userAgent: "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
    loginSupported: true, useSignatureTimestamp: true, isEmbedded: true },
];

// Live registry + retired records, for scripts that probe historical clients.
export const CLIENTS_WITH_RETIRED = [...CLIENTS, ...RETIRED];
