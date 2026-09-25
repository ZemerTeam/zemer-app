#!/usr/bin/env node
/**
 * Extended probe - test more client IDs including embedded players
 * Focus on finding clients with direct URLs that don't need cipher/poToken
 */

import { createHash } from 'crypto';

const TEST_VIDEO_ID = 'dQw4w9WgXcQ';
const COOKIE = 'HSID=AGohAxISFIyXLqm7v; SSID=AAozZv1auInAw5clE; APISID=xpa2Z4eli5XIVPaw/ANAXCictCR85fx-p6; SAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; SID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSv96zdFIJGfiZHY0O5iEBPogACgYKAdESARASFQHGX2Mia26Yo3Tnz8PTG_cE0Lgj2BoVAUF8yKorVf-A4rhnpftYlHtY1TWN0076';
const VISITOR_DATA = 'Cgs1RnUxMWVROTNLSSiZwOnPBjIKCgJVUxIEGgAgTQ==';
const ORIGIN = 'https://music.youtube.com';

const KNOWN_IDS = new Set([1, 3, 5, 7, 14, 28, 62, 67, 85, 101]);

function generateSapiSidHash(origin) {
  const sapisid = COOKIE.match(/SAPISID=([^;]+)/)?.[1];
  if (!sapisid) return null;
  const timestamp = Math.floor(Date.now() / 1000);
  const hash = createHash('sha1').update(`${timestamp} ${sapisid} ${origin}`).digest('hex');
  return `SAPISIDHASH ${timestamp}_${hash}`;
}

// Extended client list with various user agents and embedded contexts
const EXTENDED_CLIENTS = [
  // Embedded players (might bypass restrictions)
  { id: '85', name: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', version: '2.0', embedded: true },
  { id: '39', name: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER_OVERRIDE', version: '1.0', embedded: true },
  { id: '21', name: 'WEB_PRODUCER_EMBEDDED_PLAYER', version: '1.0', embedded: true },
  { id: '58', name: 'MWEB_EMBEDDED_PLAYER', version: '1.0', embedded: true },
  { id: '82', name: 'WEB_MUSIC_EMBEDDED_PLAYER', version: '1.0', embedded: true },

  // TV/Living room devices
  { id: '6', name: 'TVAPPLE', version: '1.0' },
  { id: '10', name: 'TVHTML5_AUDIO', version: '2.0' },
  { id: '23', name: 'TVHTML5_KIDS', version: '1.0' },
  { id: '72', name: 'TVHTML5_UNPLUGGED', version: '1.0' },
  { id: '80', name: 'TVHTML5_YONGLE', version: '1.0' },

  // Gaming consoles
  { id: '13', name: 'XBOX', version: '1.0' },

  // Kids variants (sometimes less restricted)
  { id: '18', name: 'ANDROID_KIDS', version: '9.10.0', osName: 'Android', osVersion: '14' },
  { id: '66', name: 'IOS_KIDS', version: '9.10.0' },
  { id: '87', name: 'WEB_KIDS', version: '2.1' },

  // Other mobile variants
  { id: '31', name: 'IOS_UNPLUGGED', version: '8.0' },
  { id: '29', name: 'ANDROID_UNPLUGGED', version: '8.0', osName: 'Android', osVersion: '14' },

  // Producer/Creator variants
  { id: '15', name: 'IOS_CREATOR', version: '24.0' },
  { id: '60', name: 'ANDROID_VR_CREATOR', version: '1.0' },
  { id: '76', name: 'ANDROID_PRODUCER', version: '1.0' },

  // Testsuite (sometimes bypasses)
  { id: '32', name: 'IOS_TESTSUITE', version: '1.9' },

  // Special
  { id: '59', name: 'MEDIA_CONNECT_FRONTEND', version: '0.1' },
  { id: '65', name: 'GOOGLE_ASSISTANT', version: '0.1' },
  { id: '77', name: 'MUSIC_INTEGRATIONS', version: '0.1' },
  { id: '78', name: 'GOOGLE_MEDIA_ACTIONS', version: '0.1' },
  { id: '88', name: 'WEB_HEROES', version: '1.0' },
  { id: '96', name: 'GOOGLE_LENS', version: '1.0' },

  // Sports
  { id: '92', name: 'ANDROID_SPORTS', version: '1.0' },
  { id: '93', name: 'IOS_SPORTS', version: '1.0' },
  { id: '95', name: 'WEB_SPORTS', version: '1.0' },

  // Higher IDs (potentially newer)
  { id: '102', name: 'ANDROID_GO', version: '3.01' },
  { id: '103', name: 'ANDROID_GO_MEDIA_SERVICE', version: '1.0' },
  { id: '104', name: 'UNKNOWN_104', version: '1.0' },
  { id: '105', name: 'UNKNOWN_105', version: '1.0' },
  { id: '110', name: 'UNKNOWN_110', version: '1.0' },
  { id: '115', name: 'UNKNOWN_115', version: '1.0' },
  { id: '120', name: 'UNKNOWN_120', version: '1.0' },
];

async function testClient(client) {
  const body = {
    context: {
      client: {
        clientName: client.name,
        clientVersion: client.version,
        hl: 'en',
        gl: 'US',
        visitorData: VISITOR_DATA,
        ...(client.osName && { osName: client.osName }),
        ...(client.osVersion && { osVersion: client.osVersion }),
      },
      ...(client.embedded && {
        thirdParty: {
          embedUrl: `https://www.youtube.com/watch?v=${TEST_VIDEO_ID}`
        }
      }),
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
    'Cookie': COOKIE,
    'Authorization': generateSapiSidHash(ORIGIN),
  };

  try {
    const res = await fetch(`https://music.youtube.com/youtubei/v1/player?prettyPrint=false`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });

    const data = await res.json();
    const status = data.playabilityStatus?.status;
    const adaptive = data.streamingData?.adaptiveFormats || [];

    if (adaptive.length === 0) {
      return { ...client, status, hasStreams: false, reason: data.playabilityStatus?.reason?.substring(0, 40) };
    }

    const sample = adaptive[0];
    const urlType = sample?.url ? 'direct' : 'cipher';
    const hasSpc = sample?.url?.includes('spc=');

    let headStatus = null;
    if (sample?.url) {
      try {
        const head = await fetch(sample.url, { method: 'HEAD', signal: AbortSignal.timeout(5000) });
        headStatus = head.status;
      } catch { headStatus = 'err'; }
    }

    const audioFormats = adaptive.filter(f => f.mimeType?.startsWith('audio/'));
    const bestAudio = audioFormats.length > 0
      ? audioFormats.reduce((a, b) => (a.bitrate > b.bitrate ? a : b))
      : null;

    return {
      ...client,
      status,
      hasStreams: true,
      adaptiveCount: adaptive.length,
      audioCount: audioFormats.length,
      urlType,
      hasSpc,
      headStatus,
      bestBitrate: bestAudio ? Math.round(bestAudio.bitrate / 1000) : null,
    };
  } catch (e) {
    return { ...client, status: 'ERROR', hasStreams: false, error: e.message?.substring(0, 30) };
  }
}

async function main() {
  console.log('=== Extended YouTube Client Probe ===\n');

  const working = [];
  const loginRequired = [];

  for (const client of EXTENDED_CLIENTS) {
    const result = await testClient(client);
    const marker = KNOWN_IDS.has(parseInt(client.id)) ? '[KNOWN]' : '[NEW?]';

    if (result.hasStreams) {
      const quality = result.urlType === 'direct' && result.headStatus === 200 ? '★' : ' ';
      console.log(`${quality}✓ ${marker} ${result.name} (${result.id}): ${result.urlType}${result.hasSpc ? '+spc' : ''} HEAD:${result.headStatus} (${result.audioCount} audio, ${result.bestBitrate}kbps)`);
      if (!KNOWN_IDS.has(parseInt(client.id))) working.push(result);
    } else if (result.status === 'LOGIN_REQUIRED') {
      console.log(`? ${marker} ${result.name} (${result.id}): needs login`);
      loginRequired.push(result);
    } else {
      // Skip bare N/A / ERROR / UNPLAYABLE results that carry no reason
      if (!['N/A', 'ERROR', 'UNPLAYABLE'].includes(result.status) || result.reason) {
        console.log(`✗ ${marker} ${result.name} (${result.id}): ${result.status || 'N/A'}${result.reason ? ' - ' + result.reason : ''}`);
      }
    }

    await new Promise(r => setTimeout(r, 150));
  }

  console.log('\n========== RESULTS ==========\n');

  const directNoSpc = working.filter(r => r.urlType === 'direct' && !r.hasSpc && r.headStatus === 200);
  const directWithSpc = working.filter(r => r.urlType === 'direct' && r.hasSpc && r.headStatus === 200);
  const cipher = working.filter(r => r.urlType === 'cipher');

  console.log('★ BEST: New clients with direct URLs, no spc gate, HEAD 200:');
  if (directNoSpc.length === 0) {
    console.log('  None found\n');
  } else {
    directNoSpc.forEach(r => console.log(`  ${r.name} (${r.id}): ${r.bestBitrate}kbps`));
    console.log('');
  }

  console.log('GOOD: New clients with direct URLs + spc (needs poToken but no cipher):');
  if (directWithSpc.length === 0) {
    console.log('  None found\n');
  } else {
    directWithSpc.forEach(r => console.log(`  ${r.name} (${r.id}): ${r.bestBitrate}kbps`));
    console.log('');
  }

  console.log('OK: New clients with ciphered URLs (needs deobfuscation):');
  if (cipher.length === 0) {
    console.log('  None found');
  } else {
    cipher.forEach(r => console.log(`  ${r.name} (${r.id}): ${r.bestBitrate}kbps`));
  }
}

main().catch(console.error);
