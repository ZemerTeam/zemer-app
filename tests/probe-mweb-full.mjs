#!/usr/bin/env node
/**
 * Full MWEB stream test - fetch player response, deobfuscate cipher, test URL
 */

import { execSync } from 'child_process';
import { createHash } from 'crypto';

const VIDEO_ID = process.argv[2] || 'dQw4w9WgXcQ';
const STS = 20611;

const MWEB = {
  clientName: 'MWEB',
  clientVersion: '2.20260213.00.00',
  userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
};

async function getPlayerResponse(videoId) {
  const body = {
    context: {
      client: {
        clientName: MWEB.clientName,
        clientVersion: MWEB.clientVersion,
        hl: 'en',
        gl: 'US',
      },
    },
    videoId: videoId,
    playbackContext: {
      contentPlaybackContext: { signatureTimestamp: STS },
    },
  };

  const res = await fetch('https://music.youtube.com/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'User-Agent': MWEB.userAgent,
      'Origin': 'https://music.youtube.com',
    },
    body: JSON.stringify(body),
  });
  return res.json();
}

async function getPlayerJs() {
  // Get YouTube homepage to find player.js URL
  const html = await fetch('https://www.youtube.com/', {
    headers: { 'User-Agent': MWEB.userAgent },
  }).then(r => r.text());

  const match = html.match(/\/s\/player\/([a-f0-9]+)\/player_ias\.vflset/);
  if (!match) throw new Error('Could not find player.js URL');

  const playerUrl = `https://www.youtube.com/s/player/${match[1]}/player_ias.vflset/en_US/base.js`;
  console.log(`Player.js: ${playerUrl}`);

  const js = await fetch(playerUrl).then(r => r.text());
  const hash = createHash('md5').update(js.substring(0, 10000)).digest('hex').substring(0, 8);
  console.log(`Player hash: ${hash}`);

  return { js, hash, playerUrl };
}

// Simple cipher function extractor (simplified - may not work for all players)
function extractCipherFunction(js) {
  // Find the signature function
  const sigMatch = js.match(/\b([a-zA-Z0-9$]+)\s*=\s*function\s*\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\s*\(\s*""\s*\)/);
  if (!sigMatch) return null;

  const funcName = sigMatch[1];
  const funcMatch = js.match(new RegExp(`${funcName.replace(/\$/g, '\\$')}\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{[^}]+\\}`));
  if (!funcMatch) return null;

  // Find the helper object
  const helperMatch = funcMatch[0].match(/([a-zA-Z0-9$]+)\.[a-zA-Z0-9$]+\(/);
  if (!helperMatch) return null;

  const helperName = helperMatch[1];
  const helperObjMatch = js.match(new RegExp(`var ${helperName.replace(/\$/g, '\\$')}\\s*=\\s*\\{[\\s\\S]*?\\}\\s*;`));

  return {
    mainFunc: funcMatch[0],
    helperObj: helperObjMatch ? helperObjMatch[0] : '',
    funcName,
    helperName,
  };
}

async function testUrl(url, ua) {
  try {
    const res = await fetch(url, { method: 'HEAD', headers: { 'User-Agent': ua } });
    return res.status;
  } catch (e) {
    return `ERROR: ${e.message}`;
  }
}

async function main() {
  console.log(`\n=== MWEB Stream Test ===`);
  console.log(`Video: ${VIDEO_ID}\n`);

  console.log('1. Fetching player response...');
  const resp = await getPlayerResponse(VIDEO_ID);
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
    console.log('\n2. Direct URL found (no deobfuscation needed)');
    const status = await testUrl(format.url, MWEB.userAgent);
    console.log(`   Validation: ${status}`);
    return;
  }

  if (!format.signatureCipher) {
    console.log('   No URL or signatureCipher found');
    return;
  }

  console.log('\n2. SignatureCipher found, need deobfuscation');
  const params = new URLSearchParams(format.signatureCipher);
  const baseUrl = params.get('url');
  const sig = params.get('s');
  const sp = params.get('sp') || 'sig';

  console.log(`   Base URL: ${baseUrl?.substring(0, 60)}...`);
  console.log(`   Signature param: ${sp}`);
  console.log(`   Encrypted sig length: ${sig?.length}`);

  console.log('\n3. Fetching player.js...');
  const { js, hash } = await getPlayerJs();

  console.log('\n4. Attempting cipher extraction (simplified)...');
  const cipher = extractCipherFunction(js);
  if (!cipher) {
    console.log('   Could not extract cipher function');
    console.log('   (The app uses a more sophisticated extractor)');
    console.log('\n   Testing base URL without signature:');
    const status = await testUrl(baseUrl, MWEB.userAgent);
    console.log(`   Result: ${status} (expected 403 without sig)`);
    return;
  }

  console.log(`   Found function: ${cipher.funcName}`);
  console.log(`   Helper: ${cipher.helperName}`);

  // Note: Actually running the cipher requires eval or vm, skipping for safety
  console.log('\n5. Cipher deobfuscation requires JS execution');
  console.log('   (Would need to eval the cipher functions)');
  console.log('\n   The app uses WebView-based cipher execution.');
  console.log('   If MWEB gets 403, the issue is likely:');
  console.log('   - pot= token mismatch');
  console.log('   - n-transform issue');
  console.log('   - MWEB-specific server validation');
}

main().catch(console.error);
