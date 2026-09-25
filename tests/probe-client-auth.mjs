#!/usr/bin/env node
// Probe clients with authentication using innertube cookies

import { createHash } from 'crypto';

const TEST_VIDEO_ID = 'dQw4w9WgXcQ';

const COOKIE = 'HSID=AGohAxISFIyXLqm7v; SSID=AAozZv1auInAw5clE; APISID=xpa2Z4eli5XIVPaw/ANAXCictCR85fx-p6; SAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; __Secure-1PAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; __Secure-3PAPISID=-cfPwuryMXooHWV1/AnDAwhQoOTd-ViLzM; SID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSv96zdFIJGfiZHY0O5iEBPogACgYKAdESARASFQHGX2Mia26Yo3Tnz8PTG_cE0Lgj2BoVAUF8yKorVf-A4rhnpftYlHtY1TWN0076; __Secure-1PSID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSvUPiNNAtKBp_0ut95Tv5f_gACgYKAVkSARASFQHGX2MiT5hqZ0WSBgIiPYt70f4r0BoVAUF8yKomQ7lgfpOscdzdbCcy9LFd0076; __Secure-3PSID=g.a0009ghS6p3oUS4C2Eki08QHHaOmrp5xmpcIyKijWploXYRDfcSvQU6hyjDXjGZRX10FNv4KUgACgYKAe8SARASFQHGX2MixWG--dDhHAxujfCwGDwEWxoVAUF8yKp8BHhUPPBltADAGpJmP8zl0076';
const VISITOR_DATA = 'Cgs1RnUxMWVROTNLSSiZwOnPBjIKCgJVUxIEGgAgTQ%3D%3D';

function generateSapiSidHash(origin) {
  const sapisid = COOKIE.match(/SAPISID=([^;]+)/)?.[1];
  if (!sapisid) return null;
  const timestamp = Math.floor(Date.now() / 1000);
  const hash = createHash('sha1').update(`${timestamp} ${sapisid} ${origin}`).digest('hex');
  return `SAPISIDHASH ${timestamp}_${hash}`;
}

const CLIENTS_TO_TEST = [
  // Clients that require login - test with auth
  {
    name: 'ANDROID_MUSIC',
    config: {
      clientName: 'ANDROID_MUSIC',
      clientVersion: '7.27.52',
      osName: 'Android',
      osVersion: '14',
      androidSdkVersion: '34',
    }
  },
  {
    name: 'IOS_MUSIC',
    config: {
      clientName: 'IOS_MUSIC',
      clientVersion: '7.27.0',
      osName: 'iOS',
      osVersion: '17.2',
    }
  },
  // TVOS with auth
  {
    name: 'TVOS',
    config: {
      clientName: 'TVOS',
      clientVersion: '1.0',
    }
  },
  // Test WEB_MUSIC (ID 89)
  {
    name: 'WEB_MUSIC',
    config: {
      clientName: 'WEB_MUSIC',
      clientVersion: '1.0',
    }
  },
  // MWEB
  {
    name: 'MWEB',
    config: {
      clientName: 'MWEB',
      clientVersion: '2.20250101.00.00',
    }
  },
  // TVHTML5 (a former app client; verify behavior)
  {
    name: 'TVHTML5',
    config: {
      clientName: 'TVHTML5',
      clientVersion: '7.20250101.00.00',
    }
  },
  // TVHTML5_SIMPLY
  {
    name: 'TVHTML5_SIMPLY',
    config: {
      clientName: 'TVHTML5_SIMPLY',
      clientVersion: '1.0',
    }
  },
  // WEB_KIDS
  {
    name: 'WEB_KIDS',
    config: {
      clientName: 'WEB_KIDS',
      clientVersion: '2.1',
    }
  },
  // ANDROID_UNPLUGGED (YouTube TV)
  {
    name: 'ANDROID_UNPLUGGED',
    config: {
      clientName: 'ANDROID_UNPLUGGED',
      clientVersion: '8.01',
      osName: 'Android',
      osVersion: '14',
    }
  },
  // XBOX
  {
    name: 'XBOX',
    config: {
      clientName: 'XBOX',
      clientVersion: '1.0',
    }
  },
];

async function testClient(client) {
  const origin = 'https://music.youtube.com';
  const authHeader = generateSapiSidHash(origin);

  const body = {
    context: {
      client: {
        clientName: client.config.clientName,
        clientVersion: client.config.clientVersion,
        hl: 'en',
        gl: 'US',
        visitorData: decodeURIComponent(VISITOR_DATA),
        ...(client.config.osName && { osName: client.config.osName }),
        ...(client.config.osVersion && { osVersion: client.config.osVersion }),
        ...(client.config.androidSdkVersion && { androidSdkVersion: client.config.androidSdkVersion }),
      }
    },
    videoId: TEST_VIDEO_ID,
  };

  try {
    const headers = {
      'Content-Type': 'application/json',
      'Cookie': COOKIE,
      'Origin': origin,
      'X-Origin': origin,
    };
    if (authHeader) {
      headers['Authorization'] = authHeader;
    }

    const res = await fetch(
      `https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8`,
      {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      }
    );

    const data = await res.json();

    const status = data.playabilityStatus?.status || 'N/A';
    const hasStreams = !!(data.streamingData?.formats?.length || data.streamingData?.adaptiveFormats?.length);

    if (hasStreams) {
      const adaptive = data.streamingData.adaptiveFormats || [];
      const audioFormats = adaptive.filter(f => f.mimeType?.startsWith('audio/'));
      const sample = adaptive[0] || data.streamingData.formats?.[0];
      const urlType = sample?.url ? (sample.url.includes('spc=') ? 'spc' : 'direct') : 'cipher';

      console.log(`✓ ${client.name}: ${status}`);
      console.log(`  └─ ${adaptive.length} adaptive (${audioFormats.length} audio), URLs: ${urlType}`);

      // HEAD check
      if (sample?.url) {
        try {
          const head = await fetch(sample.url, { method: 'HEAD' });
          console.log(`  └─ HEAD: ${head.status}${head.status === 200 ? ' ← STREAMS OK!' : ''}`);
        } catch (e) {
          console.log(`  └─ HEAD: failed (${e.message})`);
        }
      }

      return { name: client.name, works: true, urlType };
    } else {
      const reason = data.playabilityStatus?.reason || '';
      console.log(`✗ ${client.name}: ${status}${reason ? ` - ${reason.substring(0,50)}` : ''}`);
      return { name: client.name, works: false };
    }
  } catch (e) {
    console.log(`✗ ${client.name}: ERROR - ${e.message}`);
    return { name: client.name, works: false };
  }
}

async function main() {
  console.log('Testing YouTube clients with authentication...\n');

  const results = [];
  for (const client of CLIENTS_TO_TEST) {
    const result = await testClient(client);
    results.push(result);
    await new Promise(r => setTimeout(r, 300));
  }

  console.log('\n========== POTENTIALLY USEFUL NEW CLIENTS ==========');
  const working = results.filter(r => r.works);
  if (working.length === 0) {
    console.log('No new working clients found.');
  } else {
    working.forEach(r => {
      const inApp = ['TVHTML5'].includes(r.name);
      console.log(`${inApp ? '[IN APP]' : '[NEW]'} ${r.name} - URLs: ${r.urlType}`);
    });
  }
}

main().catch(console.error);
