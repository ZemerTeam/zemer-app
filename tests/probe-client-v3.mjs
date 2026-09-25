#!/usr/bin/env node
// Test more clients with the www.youtube.com endpoint

const TEST_VIDEO_ID = 'dQw4w9WgXcQ';

const CLIENTS_TO_TEST = [
  // Re-test TVOS with different versions
  {
    name: 'TVOS_v1',
    config: {
      clientName: 'TVOS',
      clientVersion: '1.0',
    },
    endpoint: 'youtube'
  },
  // Test Web Embedded Player
  {
    name: 'WEB_EMBEDDED_PLAYER',
    config: {
      clientName: 'WEB_EMBEDDED_PLAYER',
      clientVersion: '1.0',
    },
    endpoint: 'youtube'
  },
  // MWEB (mobile web YouTube)
  {
    name: 'MWEB',
    config: {
      clientName: 'MWEB',
      clientVersion: '2.20240101.00.00',
      userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36',
    },
    endpoint: 'youtube'
  },
  // TVHTML5 Cast
  {
    name: 'TVHTML5_CAST',
    config: {
      clientName: 'TVHTML5_CAST',
      clientVersion: '1.0',
    },
    endpoint: 'youtube'
  },
  // Test ANDROID_TESTSUITE (sometimes bypasses restrictions)
  {
    name: 'ANDROID_TESTSUITE',
    config: {
      clientName: 'ANDROID_TESTSUITE',
      clientVersion: '1.9',
      userAgent: 'com.google.android.youtube/',
      osName: 'Android',
      osVersion: '14',
      androidSdkVersion: '34',
    },
    endpoint: 'youtube'
  },
  // iOS Testsuite
  {
    name: 'IOS_TESTSUITE',
    config: {
      clientName: 'IOS_TESTSUITE',
      clientVersion: '1.9',
    },
    endpoint: 'youtube'
  },
  // Try ANDROID_LITE
  {
    name: 'ANDROID_LITE',
    config: {
      clientName: 'ANDROID_LITE',
      clientVersion: '3.01',
      osName: 'Android',
      osVersion: '12',
    },
    endpoint: 'youtube'
  },
  // MEDIA_CONNECT_FRONTEND (ID 59)
  {
    name: 'MEDIA_CONNECT_FRONTEND',
    config: {
      clientName: 'MEDIA_CONNECT_FRONTEND',
      clientVersion: '0.1',
    },
    endpoint: 'youtube'
  },
  // TVHTML5_YONGLE (ID 80) - some regional variant?
  {
    name: 'TVHTML5_YONGLE',
    config: {
      clientName: 'TVHTML5_YONGLE',
      clientVersion: '1.0',
    },
    endpoint: 'youtube'
  },
  // Test numeric ID directly with TVHTML5 user agent (what worked in first scan)
  {
    name: 'ID_75_RAW',
    config: {
      clientName: '75',
      clientVersion: '1.0',
      userAgent: 'Mozilla/5.0 (SMART-TV; Linux; Tizen) AppleWebKit/537.36',
    },
    endpoint: 'youtube'
  },
];

async function testClient(client) {
  const baseUrl = client.endpoint === 'music'
    ? 'https://music.youtube.com/youtubei/v1/player'
    : 'https://www.youtube.com/youtubei/v1/player';

  const body = {
    context: {
      client: {
        clientName: client.config.clientName,
        clientVersion: client.config.clientVersion,
        hl: 'en',
        gl: 'US',
        ...(client.config.osName && { osName: client.config.osName }),
        ...(client.config.osVersion && { osVersion: client.config.osVersion }),
        ...(client.config.androidSdkVersion && { androidSdkVersion: client.config.androidSdkVersion }),
      }
    },
    videoId: TEST_VIDEO_ID,
  };

  try {
    const res = await fetch(
      `${baseUrl}?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'User-Agent': client.config.userAgent || 'Mozilla/5.0',
        },
        body: JSON.stringify(body),
      }
    );

    const data = await res.json();

    const status = data.playabilityStatus?.status || 'N/A';
    const hasStreams = !!(data.streamingData?.formats?.length || data.streamingData?.adaptiveFormats?.length);

    if (hasStreams) {
      const adaptive = data.streamingData.adaptiveFormats || [];
      const sample = adaptive[0] || data.streamingData.formats?.[0];
      const urlType = sample?.url ? (sample.url.includes('spc=') ? 'spc-gated' : 'direct') : 'ciphered';

      console.log(`✓ ${client.name}: ${status} - ${adaptive.length} adaptive formats (${urlType})`);

      // HEAD check on first URL
      if (sample?.url) {
        try {
          const head = await fetch(sample.url, { method: 'HEAD' });
          console.log(`  └─ HEAD: ${head.status} ${head.status === 200 ? '← WORKS!' : ''}`);
        } catch {}
      }
    } else {
      console.log(`✗ ${client.name}: ${status}${data.playabilityStatus?.reason ? ` - ${data.playabilityStatus.reason.substring(0,40)}` : ''}`);
    }

    return { name: client.name, status, hasStreams };
  } catch (e) {
    console.log(`✗ ${client.name}: ERROR - ${e.message}`);
    return { name: client.name, status: 'ERROR', hasStreams: false };
  }
}

async function main() {
  console.log('Testing more YouTube clients...\n');

  for (const client of CLIENTS_TO_TEST) {
    await testClient(client);
    await new Promise(r => setTimeout(r, 250));
  }
}

main().catch(console.error);
