#!/usr/bin/env node
/**
 * Test if MWEB streams work WITHOUT pot= (just signature + n-transform)
 * This mimics what older YouTube clients did before poToken was required.
 */

const VIDEO_ID = process.argv[2] || 'dQw4w9WgXcQ';
const STS = 20611;

const MWEB = {
  clientName: 'MWEB',
  clientVersion: '2.20260213.00.00',
  userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
};

const VISIONOS = {
  clientName: 'VISIONOS',
  clientVersion: '0.1',
  userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15',
};

async function getPlayerResponse(client, videoId, useSts = true) {
  const body = {
    context: {
      client: {
        clientName: client.clientName,
        clientVersion: client.clientVersion,
        hl: 'en',
        gl: 'US',
      },
    },
    videoId: videoId,
  };

  if (useSts) {
    body.playbackContext = {
      contentPlaybackContext: { signatureTimestamp: STS },
    };
  }

  const res = await fetch('https://music.youtube.com/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'User-Agent': client.userAgent,
      'Origin': 'https://music.youtube.com',
    },
    body: JSON.stringify(body),
  });

  return res.json();
}

async function testUrl(url, ua, label) {
  try {
    const res = await fetch(url, { method: 'HEAD', headers: { 'User-Agent': ua } });
    console.log(`  ${label}: ${res.status}`);
    return res.status;
  } catch (e) {
    console.log(`  ${label}: ERROR - ${e.message}`);
    return 0;
  }
}

async function main() {
  console.log(`Testing video: ${VIDEO_ID}\n`);

  console.log('=== VISIONOS (no STS, direct URLs expected) ===');
  const vResp = await getPlayerResponse(VISIONOS, VIDEO_ID, false);
  console.log(`Status: ${vResp.playabilityStatus?.status}`);
  const vFormat = vResp.streamingData?.adaptiveFormats?.find(f => f.mimeType?.startsWith('audio/'));
  if (vFormat?.url) {
    console.log(`Has direct URL: yes`);
    await testUrl(vFormat.url, VISIONOS.userAgent, 'VISIONOS UA');
    await testUrl(vFormat.url, MWEB.userAgent, 'MWEB UA');
  }

  console.log('\n=== MWEB (with STS, signatureCipher expected) ===');
  const mResp = await getPlayerResponse(MWEB, VIDEO_ID, true);
  console.log(`Status: ${mResp.playabilityStatus?.status}`);
  const mFormat = mResp.streamingData?.adaptiveFormats?.find(f => f.mimeType?.startsWith('audio/'));

  if (mFormat?.url) {
    console.log(`Has direct URL: yes (unexpected for MWEB)`);
    await testUrl(mFormat.url, MWEB.userAgent, 'Direct URL');
  } else if (mFormat?.signatureCipher) {
    console.log(`Has signatureCipher: yes (needs deobfuscation)`);
    const params = new URLSearchParams(mFormat.signatureCipher);
    const baseUrl = params.get('url');
    const sig = params.get('s');
    console.log(`Signature length: ${sig?.length}`);
    console.log(`\nNote: Without proper cipher deobfuscation, cannot test stream.`);
    console.log(`The 403 error in the app is likely due to:`);
    console.log(`  1. Incorrect cipher deobfuscation`);
    console.log(`  2. Wrong n-transform`);
    console.log(`  3. Invalid or mismatched pot= token`);
    console.log(`  4. Server-side MWEB-specific validation`);
  } else {
    console.log(`No URL or signatureCipher found`);
  }
}

main().catch(console.error);
