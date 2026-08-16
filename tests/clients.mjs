// YouTube client definitions — mirrors
// innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt
// Keep in sync with that file.

export const USER_AGENT_WEB =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

export const CLIENTS = [
  { key: "WEB", clientName: "WEB", clientVersion: "2.20260213.00.00", clientId: "1",
    userAgent: USER_AGENT_WEB, loginSupported: false, useSignatureTimestamp: false },

  { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", clientId: "67",
    userAgent: USER_AGENT_WEB, loginSupported: true, useSignatureTimestamp: true, useWebPoTokens: true },

  { key: "WEB_CREATOR", clientName: "WEB_CREATOR", clientVersion: "1.20260213.00.00", clientId: "62",
    userAgent: USER_AGENT_WEB, loginSupported: true, loginRequired: true, useSignatureTimestamp: true, useWebPoTokens: true },

  { key: "MWEB", clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: "2",
    userAgent: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)",
    loginSupported: true, loginRequired: true, useSignatureTimestamp: true, useWebPoTokens: true },


  { key: "TVHTML5_SIMPLY", clientName: "TVHTML5_SIMPLY", clientVersion: "1.0", clientId: "75",
    userAgent: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
    loginSupported: false, useSignatureTimestamp: true, useWebPoTokens: true },







  { key: "ANDROID_VR_1_65_10", clientName: "ANDROID_VR", clientVersion: "1.65.10", clientId: "28",
    userAgent: "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
    osName: "Android", osVersion: "12L", deviceMake: "Oculus", deviceModel: "Quest 3", androidSdkVersion: "32",
    loginSupported: false, useSignatureTimestamp: false },



  { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "1.02", clientId: "101",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
    osName: "visionOS", osVersion: "26.5.23O471", deviceMake: "Apple", deviceModel: "RealityDevice17,1",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "VISIONOS_0_1", clientName: "VISIONOS", clientVersion: "0.1", clientId: "101",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
    osName: "visionOS", osVersion: "1.3.21O771", deviceMake: "Apple", deviceModel: "RealityDevice14,1",
    loginSupported: false, useSignatureTimestamp: false },

];

export const ORIGIN = "https://music.youtube.com";
export const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
