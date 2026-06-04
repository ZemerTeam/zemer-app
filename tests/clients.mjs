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
    userAgent: USER_AGENT_WEB, loginSupported: true, loginRequired: true, useSignatureTimestamp: true },

  { key: "TVHTML5", clientName: "TVHTML5", clientVersion: "7.20260213.00.00", clientId: "7",
    userAgent: "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
    loginSupported: true, loginRequired: true, useSignatureTimestamp: true, useWebPoTokens: true },

  { key: "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientName: "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientVersion: "2.0", clientId: "85",
    userAgent: "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
    loginSupported: true, useSignatureTimestamp: true, isEmbedded: true },

  { key: "IOS", clientName: "IOS", clientVersion: "21.03.1", clientId: "5",
    userAgent: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
    osVersion: "18.2.22C152", loginSupported: false, useSignatureTimestamp: false },

  { key: "ANDROID", clientName: "ANDROID", clientVersion: "21.03.38", clientId: "3",
    userAgent: "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
    loginSupported: true, useSignatureTimestamp: true },

  { key: "ANDROID_NO_SDK", clientName: "ANDROID", clientVersion: "21.03.38", clientId: "3",
    userAgent: "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
    loginSupported: false, useSignatureTimestamp: false },

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

  { key: "ANDROID_CREATOR", clientName: "ANDROID_CREATOR", clientVersion: "25.03.101", clientId: "14",
    userAgent: "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
    osName: "Android", osVersion: "15", deviceMake: "Google", deviceModel: "Pixel 9 Pro Fold", androidSdkVersion: "35",
    loginSupported: true, useSignatureTimestamp: true },

  { key: "VISIONOS", clientName: "VISIONOS", clientVersion: "0.1", clientId: "101",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
    osName: "visionOS", osVersion: "1.3.21O771", deviceMake: "Apple", deviceModel: "RealityDevice14,1",
    loginSupported: false, useSignatureTimestamp: false },

  { key: "IPADOS", clientName: "IOS", clientVersion: "21.03.3", clientId: "5",
    userAgent: "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
    osName: "iPadOS", osVersion: "17.7.10.21H450", deviceMake: "Apple", deviceModel: "iPad7,6",
    loginSupported: false, useSignatureTimestamp: false },
];

export const ORIGIN = "https://music.youtube.com";
export const PLAYER_URL = ORIGIN + "/youtubei/v1/player?prettyPrint=false";
