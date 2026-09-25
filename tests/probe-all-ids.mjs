#!/usr/bin/env node
// Comprehensive scan of client IDs 1-130 with auth

import { createHash } from 'crypto';

const TEST_VIDEO_ID = 'dQw4w9WgXcQ';
const COOKIE = 'HSID=AGohAxISFIyXLqm7v; SSID=AAozZv1auInAw5clE; APISID=xpa2Z4eli5XIVPaw/ANAXCictCR85fx-p6; SAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; SID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSv96zdFIJGfiZHY0O5iEBPogACgYKAdESARASFQHGX2Mia26Yo3Tnz8PTG_cE0Lgj2BoVAUF8yKorVf-A4rhnpftYlHtY1TWN0076';

const KNOWN_IDS = new Set([1, 3, 5, 7, 14, 28, 62, 67, 85, 101]);

// Known client names (best effort mapping)
const ID_TO_NAME = {
  1: 'WEB', 2: 'MWEB', 3: 'ANDROID', 4: 'ANDROID_EMBEDDED_PLAYER', 5: 'IOS',
  6: 'TVAPPLE', 7: 'TVHTML5', 10: 'TVHTML5_AUDIO', 13: 'XBOX', 14: 'ANDROID_CREATOR',
  15: 'IOS_CREATOR', 16: 'TVHTML5_SIMPLY', 18: 'ANDROID_KIDS', 20: 'ANDROID_TESTSUITE',
  21: 'WEB_PRODUCER_EMBEDDED_PLAYER', 23: 'TVHTML5_KIDS', 26: 'ANDROID_MUSIC',
  27: 'IOS_MUSIC', 28: 'ANDROID_VR', 29: 'ANDROID_UNPLUGGED', 30: 'ANDROID_LITE',
  31: 'IOS_UNPLUGGED', 32: 'IOS_TESTSUITE', 33: 'WEB_MUSIC_ANALYTICS',
  36: 'IOS_EMBEDDED_PLAYER', 38: 'WEB_EMBEDDED_PLAYER', 40: 'MWEB_UNPLUGGED',
  56: 'WEB_UNPLUGGED', 57: 'ANDROID_MUSIC_TESTSUITE', 58: 'MWEB_EMBEDDED_PLAYER',
  59: 'MEDIA_CONNECT_FRONTEND', 60: 'ANDROID_VR_CREATOR', 62: 'WEB_CREATOR',
  63: 'IOS_DIRECTOR', 64: 'ANDROID_DIRECTOR', 65: 'GOOGLE_ASSISTANT',
  66: 'IOS_KIDS', 67: 'WEB_REMIX', 68: 'IOS_UPTIME', 70: 'WEB_UNPLUGGED_OPS',
  71: 'WEB_UNPLUGGED_PUBLIC', 72: 'TVHTML5_UNPLUGGED', 74: 'MWEB_MUSIC',
  75: 'TVHTML5_AUDIO', 76: 'ANDROID_PRODUCER', 77: 'MUSIC_INTEGRATIONS',
  78: 'GOOGLE_MEDIA_ACTIONS', 80: 'TVHTML5_YONGLE', 82: 'WEB_MUSIC_EMBEDDED_PLAYER',
  84: 'TVHTML5_CAST', 85: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', 87: 'WEB_KIDS',
  88: 'WEB_HEROES', 89: 'WEB_MUSIC', 90: 'GOOGLE_ASSISTANT_CREATOR', 91: 'CHROMECAST',
  92: 'ANDROID_SPORTS', 93: 'IOS_SPORTS', 94: 'ANDROID_LIVE_RING', 95: 'WEB_SPORTS',
  96: 'GOOGLE_LENS', 100: 'TVOS', 101: 'VISIONOS', 102: 'ANDROID_GO',
};

function generateSapiSidHash(origin) {
  const sapisid = COOKIE.match(/SAPISID=([^;]+)/)?.[1];
  if (!sapisid) return null;
  const timestamp = Math.floor(Date.now() / 1000);
  const hash = createHash('sha1').update(`${timestamp} ${sapisid} ${origin}`).digest('hex');
  return `SAPISIDHASH ${timestamp}_${hash}`;
}

async function probeId(id) {
  const clientName = ID_TO_NAME[id] || id.toString();
  const origin = 'https://www.youtube.com';

  const body = {
    context: {
      client: {
        clientName: clientName,
        clientVersion: '1.0',
        hl: 'en',
        gl: 'US',
      }
    },
    videoId: TEST_VIDEO_ID,
  };

  try {
    const authHeader = generateSapiSidHash(origin);
    const headers = {
      'Content-Type': 'application/json',
      'Cookie': COOKIE,
      'Origin': origin,
    };
    if (authHeader) headers['Authorization'] = authHeader;

    const res = await fetch(
      `https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8`,
      { method: 'POST', headers, body: JSON.stringify(body) }
    );

    const data = await res.json();
    const status = data.playabilityStatus?.status;
    const hasStreams = !!(data.streamingData?.formats?.length || data.streamingData?.adaptiveFormats?.length);

    return {
      id,
      name: clientName,
      status,
      hasStreams,
      isKnown: KNOWN_IDS.has(id),
      adaptive: data.streamingData?.adaptiveFormats?.length || 0,
      urlType: data.streamingData?.adaptiveFormats?.[0]?.url ? 'direct' :
               data.streamingData?.adaptiveFormats?.[0]?.signatureCipher ? 'cipher' : null,
    };
  } catch (e) {
    return { id, name: clientName, status: 'ERROR', hasStreams: false, isKnown: KNOWN_IDS.has(id) };
  }
}

async function main() {
  console.log('Scanning client IDs 1-130...\n');

  const working = [];
  const loginRequired = [];

  for (let id = 1; id <= 130; id++) {
    const result = await probeId(id);

    if (result.hasStreams) {
      const marker = result.isKnown ? '[KNOWN]' : '[NEW!]';
      console.log(`${marker} ID ${id} (${result.name}): ${result.status} - ${result.adaptive} adaptive (${result.urlType})`);
      if (!result.isKnown) working.push(result);
    } else if (result.status === 'LOGIN_REQUIRED') {
      if (!result.isKnown) {
        console.log(`[AUTH?] ID ${id} (${result.name}): needs login`);
        loginRequired.push(result);
      }
    }

    // Rate limit
    await new Promise(r => setTimeout(r, 80));
  }

  console.log('\n========== SUMMARY ==========');
  console.log('\nNEW working clients (not in app):');
  if (working.length === 0) {
    console.log('  None found');
  } else {
    working.forEach(r => console.log(`  ID ${r.id}: ${r.name} (${r.urlType} URLs)`));
  }

  console.log('\nClients requiring login (might work with proper auth):');
  loginRequired.slice(0, 10).forEach(r => console.log(`  ID ${r.id}: ${r.name}`));
}

main().catch(console.error);
