#!/usr/bin/env node
/**
 * Proper client probe mimicking the app's InnerTube requests
 * - Uses correct headers (X-Goog-Api-Format-Version, X-YouTube-Client-Name, etc.)
 * - Uses music.youtube.com endpoint
 * - Includes authentication when available
 * - Tests cipher deobfuscation capability
 */

import { createHash } from 'crypto';
import { URLSearchParams } from 'url';

const TEST_VIDEO_ID = 'dQw4w9WgXcQ';

// From innertube_cookie.txt
const COOKIE = 'HSID=AGohAxISFIyXLqm7v; SSID=AAozZv1auInAw5clE; APISID=xpa2Z4eli5XIVPaw/ANAXCictCR85fx-p6; SAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; SID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSv96zdFIJGfiZHY0O5iEBPogACgYKAdESARASFQHGX2Mia26Yo3Tnz8PTG_cE0Lgj2BoVAUF8yKorVf-A4rhnpftYlHtY1TWN0076';
const VISITOR_DATA = 'Cgs1RnUxMWVROTNLSSiZwOnPBjIKCgJVUxIEGgAgTQ==';

const ORIGIN = 'https://music.youtube.com';
const API_URL = 'https://music.youtube.com/youtubei/v1/player';

const KNOWN_IDS = new Set([1, 3, 5, 7, 14, 28, 62, 67, 85, 101]);

const CLIENTS = {
  // App / former-app clients (for verification)
  WEB_REMIX: { id: '67', name: 'WEB_REMIX', version: '1.20260213.01.00', loginSupported: true },
  VISIONOS: { id: '101', name: 'VISIONOS', version: '0.1', loginSupported: false },
  TVHTML5: { id: '7', name: 'TVHTML5', version: '7.20260213.00.00', loginSupported: true },
  IOS: { id: '5', name: 'IOS', version: '21.03.1', loginSupported: false },
  ANDROID_VR: { id: '28', name: 'ANDROID_VR', version: '1.61.48', loginSupported: false },

  // Potentially new clients to test
  MWEB: { id: '2', name: 'MWEB', version: '2.20260213.00.00', loginSupported: true },
  ANDROID_MUSIC: { id: '26', name: 'ANDROID_MUSIC', version: '7.27.52', loginSupported: true },
  IOS_MUSIC: { id: '27', name: 'IOS_MUSIC', version: '7.27.0', loginSupported: true },
  TVOS: { id: '100', name: 'TVOS', version: '1.0', loginSupported: false },
  ANDROID_LITE: { id: '30', name: 'ANDROID_LITE', version: '3.26.1', loginSupported: false },
  TVHTML5_SIMPLY: { id: '16', name: 'TVHTML5_SIMPLY', version: '1.0', loginSupported: false },
  TVHTML5_CAST: { id: '84', name: 'TVHTML5_CAST', version: '1.0', loginSupported: false },
  CHROMECAST: { id: '91', name: 'CHROMECAST', version: '0.1', loginSupported: false },
  ANDROID_EMBEDDED: { id: '4', name: 'ANDROID_EMBEDDED_PLAYER', version: '21.03.38', loginSupported: false },
  IOS_EMBEDDED: { id: '36', name: 'IOS_EMBEDDED_PLAYER', version: '21.03.1', loginSupported: false },
  MWEB_MUSIC: { id: '74', name: 'MWEB_MUSIC', version: '1.0', loginSupported: true },
  WEB_EMBEDDED: { id: '38', name: 'WEB_EMBEDDED_PLAYER', version: '1.0', loginSupported: false },
  ANDROID_TESTSUITE: { id: '20', name: 'ANDROID_TESTSUITE', version: '1.9', loginSupported: false },
};

function generateSapiSidHash(origin) {
  const sapisid = COOKIE.match(/SAPISID=([^;]+)/)?.[1];
  if (!sapisid) return null;
  const timestamp = Math.floor(Date.now() / 1000);
  const hash = createHash('sha1').update(`${timestamp} ${sapisid} ${origin}`).digest('hex');
  return `SAPISIDHASH ${timestamp}_${hash}`;
}

async function testClient(client, useAuth = true) {
  const body = {
    context: {
      client: {
        clientName: client.name,
        clientVersion: client.version,
        hl: 'en',
        gl: 'US',
        visitorData: VISITOR_DATA,
      }
    },
    videoId: TEST_VIDEO_ID,
  };

  const headers = {
    'Content-Type': 'application/json',
    'X-Goog-Api-Format-Version': '1',
    'X-YouTube-Client-Name': client.id,
    'X-YouTube-Client-Version': client.version,
    'X-Origin': ORIGIN,
    'Referer': `${ORIGIN}/`,
    'X-Goog-Visitor-Id': VISITOR_DATA,
  };

  if (useAuth && client.loginSupported) {
    headers['Cookie'] = COOKIE;
    const authHeader = generateSapiSidHash(ORIGIN);
    if (authHeader) headers['Authorization'] = authHeader;
  }

  try {
    const res = await fetch(`${API_URL}?prettyPrint=false`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });

    const data = await res.json();
    const status = data.playabilityStatus?.status;
    const adaptive = data.streamingData?.adaptiveFormats || [];
    const formats = data.streamingData?.formats || [];

    const result = {
      name: client.name,
      id: client.id,
      status,
      hasStreams: adaptive.length > 0 || formats.length > 0,
      adaptiveCount: adaptive.length,
      formatCount: formats.length,
      isKnown: KNOWN_IDS.has(parseInt(client.id)),
    };

    if (result.hasStreams) {
      const sample = adaptive[0] || formats[0];
      result.urlType = sample?.url ? 'direct' : sample?.signatureCipher ? 'cipher' : 'unknown';

      // Check for spc parameter (poToken gate)
      if (sample?.url) {
        result.hasSpc = sample.url.includes('spc=');
      }

      // Count audio formats
      const audioFormats = adaptive.filter(f => f.mimeType?.startsWith('audio/'));
      result.audioCount = audioFormats.length;

      // Get best audio quality
      if (audioFormats.length > 0) {
        const best = audioFormats.reduce((a, b) => (a.bitrate > b.bitrate ? a : b));
        result.bestAudio = `${best.mimeType?.split(';')[0]} @ ${Math.round(best.bitrate / 1000)}kbps`;
      }

      // Quick HEAD check for direct URLs
      if (sample?.url) {
        try {
          const head = await fetch(sample.url, { method: 'HEAD', signal: AbortSignal.timeout(5000) });
          result.headStatus = head.status;
        } catch {
          result.headStatus = 'failed';
        }
      }
    } else {
      result.reason = data.playabilityStatus?.reason?.substring(0, 60);
    }

    return result;
  } catch (e) {
    return { name: client.name, id: client.id, status: 'ERROR', hasStreams: false, error: e.message };
  }
}

async function main() {
  console.log('=== YouTube Client Probe (proper InnerTube format) ===\n');
  console.log('Testing with music.youtube.com endpoint + auth headers\n');

  const results = [];

  for (const [key, client] of Object.entries(CLIENTS)) {
    const result = await testClient(client);
    results.push(result);

    const marker = result.isKnown ? '[KNOWN]' : '[NEW?]';
    const streamInfo = result.hasStreams
      ? `${result.adaptiveCount} adaptive (${result.audioCount} audio), ${result.urlType}${result.hasSpc ? '+spc' : ''}, HEAD:${result.headStatus}`
      : result.reason || 'no streams';

    const symbol = result.hasStreams ? '✓' : '✗';
    console.log(`${symbol} ${marker} ${client.name} (${client.id}): ${result.status || 'N/A'}`);
    if (result.hasStreams) {
      console.log(`   └─ ${streamInfo}`);
      if (result.bestAudio) console.log(`   └─ Best: ${result.bestAudio}`);
    } else if (result.reason) {
      console.log(`   └─ ${result.reason}`);
    }

    await new Promise(r => setTimeout(r, 200));
  }

  // Summary
  console.log('\n========== SUMMARY ==========\n');

  const newWorking = results.filter(r => r.hasStreams && !r.isKnown);
  const newDirect = newWorking.filter(r => r.urlType === 'direct' && r.headStatus === 200);

  console.log('NEW clients with working streams:');
  if (newWorking.length === 0) {
    console.log('  None found\n');
  } else {
    newWorking.forEach(r => {
      const quality = r.urlType === 'direct' && r.headStatus === 200 ? ' ← USABLE!' : '';
      console.log(`  ${r.name} (${r.id}): ${r.urlType}${r.hasSpc ? '+spc' : ''}, HEAD:${r.headStatus}${quality}`);
    });
    console.log('');
  }

  console.log('NEW clients with DIRECT URLs that HEAD 200 (best candidates):');
  if (newDirect.length === 0) {
    console.log('  None found');
  } else {
    newDirect.forEach(r => {
      console.log(`  ★ ${r.name} (${r.id}): ${r.bestAudio}`);
    });
  }
}

main().catch(console.error);
