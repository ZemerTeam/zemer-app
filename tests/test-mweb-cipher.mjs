#!/usr/bin/env node
/**
 * Test MWEB stream with full cipher deobfuscation using cipher.mjs
 */

import { createCipher } from './cipher.mjs';

const VIDEO_ID = process.argv[2] || 'dQw4w9WgXcQ';

const MWEB_UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

async function getPlayerResponse(videoId, sts) {
  const body = {
    context: {
      client: {
        clientName: 'MWEB',
        clientVersion: '2.20260213.00.00',
        hl: 'en',
        gl: 'US',
      },
    },
    videoId: videoId,
    playbackContext: {
      contentPlaybackContext: { signatureTimestamp: sts },
    },
  };

  const res = await fetch('https://music.youtube.com/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'User-Agent': MWEB_UA,
      'Origin': 'https://music.youtube.com',
    },
    body: JSON.stringify(body),
  });
  return res.json();
}

async function testUrl(url, label) {
  try {
    const res = await fetch(url, {
      method: 'HEAD',
      headers: { 'User-Agent': MWEB_UA },
    });
    console.log(`${label}: ${res.status}`);
    return res.status;
  } catch (e) {
    console.log(`${label}: ERROR - ${e.message}`);
    return 0;
  }
}

async function main() {
  console.log('=== MWEB Stream Test with Full Cipher ===\n');
  console.log(`Video: ${VIDEO_ID}\n`);

  console.log('1. Creating cipher from player.js...');
  const cipher = await createCipher({ verbose: true });
  console.log(`   Hash: ${cipher.hash}`);
  console.log(`   STS: ${cipher.sts}`);
  console.log(`   Sig available: ${cipher.sigAvailable}`);
  console.log(`   N-transform available: ${cipher.nAvailable}`);

  console.log('\n2. Fetching MWEB player response...');
  const resp = await getPlayerResponse(VIDEO_ID, cipher.sts);
  console.log(`   Status: ${resp.playabilityStatus?.status}`);

  if (resp.playabilityStatus?.status !== 'OK') {
    console.log(`   Reason: ${resp.playabilityStatus?.reason}`);
    return;
  }

  const format = resp.streamingData?.adaptiveFormats?.find(f => f.mimeType?.startsWith('audio/'));
  if (!format) {
    console.log('   No audio format found');
    return;
  }

  console.log(`   Format: itag=${format.itag}, ${format.mimeType}`);

  if (format.url) {
    console.log('\n3. Direct URL found');
    await testUrl(format.url, '   Direct URL');
    return;
  }

  if (!format.signatureCipher) {
    console.log('   No URL or signatureCipher');
    return;
  }

  // Parse signatureCipher
  const params = new URLSearchParams(format.signatureCipher);
  const baseUrl = params.get('url');
  const encSig = params.get('s');
  const sigParam = params.get('sp') || 'sig';

  console.log(`\n3. SignatureCipher found`);
  console.log(`   Sig param: ${sigParam}`);
  console.log(`   Encrypted sig length: ${encSig?.length}`);

  console.log('\n4. Deobfuscating stream URL...');
  let streamUrl;
  try {
    streamUrl = cipher.deobfuscateStreamUrl(format.signatureCipher, VIDEO_ID);
    console.log(`   Deobfuscated URL length: ${streamUrl?.length}`);
  } catch (e) {
    console.log(`   Deobfuscation failed: ${e.message}`);
    return;
  }

  console.log('\n5. Applying n-transform...');
  let finalUrl;
  try {
    finalUrl = cipher.transformNParamInUrl(streamUrl);
    console.log(`   N-transformed URL length: ${finalUrl?.length}`);
  } catch (e) {
    console.log(`   N-transform failed: ${e.message}`);
    finalUrl = streamUrl;
  }

  console.log('\n6. Testing URL (without pot=)...');
  const status = await testUrl(finalUrl, '   HEAD request');

  if (status === 200) {
    console.log('\n   SUCCESS! MWEB stream works without pot=');
  } else if (status === 403) {
    console.log('\n   403 Forbidden - might need pot= or cipher is wrong');
    console.log('   Checking n-param was transformed...');
    const urlObj = new URL(finalUrl);
    const n = urlObj.searchParams.get('n');
    console.log(`   n param: ${n?.substring(0, 20)}...`);
  }
}

main().catch(console.error);
