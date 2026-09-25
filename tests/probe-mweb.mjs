#!/usr/bin/env node
/**
 * MWEB client probe - diagnose why MWEB streams are failing
 */

const VIDEO_ID = process.argv[2] || 'dQw4w9WgXcQ';

const CLIENTS = {
  MWEB: {
    clientName: 'MWEB',
    clientVersion: '2.20260213.00.00',
    clientId: '2',
    userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
  },
  WEB_REMIX: {
    clientName: 'WEB_REMIX',
    clientVersion: '1.20260213.01.00',
    clientId: '67',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0',
  },
  VISIONOS: {
    clientName: 'VISIONOS',
    clientVersion: '0.1',
    clientId: '101',
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15',
  },
};

// Normal 44-char visitorData (31 bytes base64)
const CLEAN_VISITOR_DATA = 'Cgs1RnUxMWVROTNLSSiZwOnPBjIKCgJVUxIEGgAgTQ==';

// Bloated visitorData (like what the apps are sending)
const BLOATED_VISITOR_DATA = 'CgtZeXo4YWlKRVJQSSjSt6HRBjIKCgJVUxIEGgAgRmLfAgrcAjE5LllUPXFQaVpkVnZnX3NJakRaQi13X0RNZWhZREZSY0h3M1VYcUdqU1FTWFZKRk9CMWJ2ck13elp3SVVfSXlsMVZqdVl6YXl0Z00xc0Q1Q2pOMmVyTnRfYXlyRjFUS0dFbjhNWTE3SkhFeUhxaFVNejMxYzYyRmNlTWtNM1JhZFRoZUJSbENsVGJSaU9NU0RrdDlwN24xVDI5aEZ3aWdPZUhSRnVqdHVDV0c2QmJJS1RwNEVpVXB0dEJtT3l3Mm4wSzRMS05hSXI0Xy1NTWFQM1ViX05VbklMZjRnMHl0TjF2Z09qM3J1akRXOXhMbHdYZHhNa2Y0Y1laSHJnamd6Vi0tYjdhc0JCZ3JnU1ktTFVPQ2JHOEZhdndmR1dNNGQwU04wS0xyQlB5aFRIUkExWDhsLTk1YW1DandEWnBnRzBNMWR5cmNrN1JrZEVaWmZCdnVYMmhXTm14dw==';

async function fetchPlayer(client, videoId, visitorData, signatureTimestamp = null, poToken = null) {
  const body = {
    context: {
      client: {
        clientName: client.clientName,
        clientVersion: client.clientVersion,
        hl: 'en',
        gl: 'US',
        visitorData: visitorData,
      },
    },
    videoId: videoId,
  };

  if (signatureTimestamp) {
    body.playbackContext = {
      contentPlaybackContext: {
        signatureTimestamp: signatureTimestamp,
      },
    };
  }

  if (poToken) {
    body.serviceIntegrityDimensions = {
      poToken: poToken,
    };
  }

  const url = `https://music.youtube.com/youtubei/v1/player?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false`;

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'User-Agent': client.userAgent,
      'Origin': 'https://music.youtube.com',
      'Referer': 'https://music.youtube.com/',
    },
    body: JSON.stringify(body),
  });

  return res.json();
}

async function testClient(clientName, visitorData, label, signatureTimestamp = null) {
  const client = CLIENTS[clientName];
  console.log(`\n=== ${clientName} with ${label} visitorData${signatureTimestamp ? ' + STS=' + signatureTimestamp : ''} ===`);
  console.log(`visitorData length: ${visitorData.length} chars`);

  try {
    const resp = await fetchPlayer(client, VIDEO_ID, visitorData, signatureTimestamp);

    const status = resp.playabilityStatus?.status;
    const reason = resp.playabilityStatus?.reason;
    console.log(`Status: ${status}`);
    if (reason) console.log(`Reason: ${reason}`);

    const formats = resp.streamingData?.adaptiveFormats || [];
    const audioFormats = formats.filter(f => f.mimeType?.startsWith('audio/'));

    console.log(`Audio formats: ${audioFormats.length}`);

    if (audioFormats.length > 0) {
      const f = audioFormats[0];
      const hasUrl = !!f.url;
      const hasCipher = !!f.signatureCipher;
      console.log(`First format: itag=${f.itag}, hasUrl=${hasUrl}, hasCipher=${hasCipher}`);

      if (hasUrl) {
        const testRes = await fetch(f.url, { method: 'HEAD' });
        console.log(`URL validation: ${testRes.status}`);
      } else if (hasCipher) {
        console.log(`signatureCipher present (needs deobfuscation)`);
      }
    }
  } catch (e) {
    console.log(`Error: ${e.message}`);
  }
}

async function main() {
  console.log(`Testing video: ${VIDEO_ID}`);
  console.log(`Clean visitorData: ${CLEAN_VISITOR_DATA.length} chars`);
  console.log(`Bloated visitorData: ${BLOATED_VISITOR_DATA.length} chars`);

  const STS = 20611; // player 69e2a55d's signatureTimestamp

  await testClient('MWEB', CLEAN_VISITOR_DATA, 'CLEAN', STS);

  await testClient('MWEB', BLOATED_VISITOR_DATA, 'BLOATED', STS);

  await testClient('WEB_REMIX', CLEAN_VISITOR_DATA, 'CLEAN', STS);

  // Test VISIONOS (no visitorData or STS needed)
  await testClient('VISIONOS', '', 'NONE');
}

main().catch(console.error);
