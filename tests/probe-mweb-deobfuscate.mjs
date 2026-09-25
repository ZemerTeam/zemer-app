#!/usr/bin/env node
/**
 * Full MWEB deobfuscation test - extracts cipher functions from player.js and tests
 */

import vm from 'vm';

const VIDEO_ID = process.argv[2] || 'dQw4w9WgXcQ';
const STS = 20611;

const MWEB_UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

async function getPlayerResponse(videoId) {
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
      contentPlaybackContext: { signatureTimestamp: STS },
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

async function getPlayerJs() {
  // Get player hash from a video page
  const html = await fetch('https://www.youtube.com/watch?v=dQw4w9WgXcQ', {
    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
  }).then(r => r.text());

  const match = html.match(/\/s\/player\/([a-f0-9]+)\/player_ias/);
  if (!match) throw new Error('Could not find player hash');

  const hash = match[1];
  console.log(`Player hash: ${hash}`);

  const jsUrl = `https://www.youtube.com/s/player/${hash}/player_ias.vflset/en_US/base.js`;
  const js = await fetch(jsUrl).then(r => r.text());

  return { js, hash };
}

function extractCipherFunctions(js) {
  // Find the main cipher function (splits string, applies transforms)
  // Pattern: var XX={...helper functions...};var YY=function(a){a=a.split("");XX.func(a,N);...;return a.join("")}

  // First find the helper object with reverse/swap/splice operations
  const helperPattern = /var (\w+)=\{(\w+):function\(a\)\{a\.reverse\(\)\},(\w+):function\(a,b\)\{var c=a\[0\];a\[0\]=a\[b%a\.length\];a\[b%a\.length\]=c\},(\w+):function\(a,b\)\{a\.splice\(0,b\)\}\}/;
  const helperMatch = js.match(helperPattern);

  if (!helperMatch) {
    // Try alternate pattern
    const altPattern = /var (\w+)=\{(\w+):function\(a,b\)\{a\.splice\(0,b\)\},(\w+):function\(a\)\{a\.reverse\(\)\},(\w+):function\(a,b\)\{var c=a\[0\];a\[0\]=a\[b%a\.length\];a\[b%a\.length\]=c\}\}/;
    const altMatch = js.match(altPattern);
    if (altMatch) {
      console.log('Found helper object (alt pattern):', altMatch[1]);
    } else {
      console.log('Could not find helper object with known pattern');
      return null;
    }
  } else {
    console.log('Found helper object:', helperMatch[1]);
  }

  // Find the main function that uses this helper
  const helper = helperMatch?.[1] || 'XX';
  const mainPattern = new RegExp(`(\\w+)=function\\(a\\)\\{a=a\\.split\\(""\\);(${helper}\\.[\\w.()\\[\\],;\\s]+)return a\\.join\\(""\\)\\}`);
  const mainMatch = js.match(mainPattern);

  if (!mainMatch) {
    console.log('Could not find main cipher function');
    return null;
  }

  console.log('Found main cipher function:', mainMatch[1]);
  console.log('Operations:', mainMatch[2].substring(0, 100) + '...');

  // Return code to execute
  return {
    helperCode: helperMatch?.[0] || '',
    mainCode: mainMatch[0],
    funcName: mainMatch[1],
  };
}

function extractNTransform(js) {
  // N-transform is trickier - look for function that transforms 'n' parameter
  // Usually involves a large array and complex operations

  // Look for the enhanced_except pattern or similar
  const patterns = [
    /(\w+)=function\(a\)\{var b=a\.split\(""\),c=\[/,
    /var (\w+)=\[.*?\];(\w+)=function\(a\)\{/,
  ];

  for (const pattern of patterns) {
    const match = js.match(pattern);
    if (match) {
      console.log('Found potential n-transform function:', match[1] || match[2]);
      return match[0];
    }
  }

  console.log('Could not find n-transform function');
  return null;
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
  console.log('=== MWEB Full Deobfuscation Test ===\n');

  console.log('1. Fetching player response...');
  const resp = await getPlayerResponse(VIDEO_ID);
  console.log(`   Status: ${resp.playabilityStatus?.status}`);

  if (resp.playabilityStatus?.status !== 'OK') {
    console.log(`   Reason: ${resp.playabilityStatus?.reason}`);
    return;
  }

  const format = resp.streamingData?.adaptiveFormats?.find(f => f.mimeType?.startsWith('audio/'));
  if (!format?.signatureCipher) {
    console.log('   No signatureCipher in format');
    if (format?.url) {
      console.log('   Has direct URL, testing...');
      await testUrl(format.url, '   Direct URL');
    }
    return;
  }

  // Parse signatureCipher
  const params = new URLSearchParams(format.signatureCipher);
  const baseUrl = params.get('url');
  const encryptedSig = params.get('s');
  const sigParam = params.get('sp') || 'sig';

  console.log(`   Base URL: ${baseUrl?.substring(0, 60)}...`);
  console.log(`   Encrypted sig length: ${encryptedSig?.length}`);
  console.log(`   Sig param name: ${sigParam}`);

  console.log('\n2. Fetching player.js...');
  const { js, hash } = await getPlayerJs();

  console.log('\n3. Extracting cipher functions...');
  const cipher = extractCipherFunctions(js);

  if (!cipher) {
    console.log('   Could not extract cipher - trying hardcoded for hash', hash);

    // Use hardcoded config for known hash
    if (hash === '69e2a55d') {
      console.log('   Using hardcoded config for 69e2a55d');
      // Based on player_configs.json
      // sigJsExpression = "Jf(20,3699,INPUT)"
      console.log('   This hash uses jsExpression-based cipher');
      console.log('   Cannot easily test without the full VM setup');
    }
    return;
  }

  console.log('\n4. Testing URL without signature...');
  await testUrl(baseUrl, '   Base URL (no sig)');

  console.log('\n[Full deobfuscation requires running JS in sandbox]');
  console.log('[The app uses WebView for this]');
}

main().catch(console.error);
