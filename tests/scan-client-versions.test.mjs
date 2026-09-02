// Unit tests for the yt-dlp identity parser and the drift diff (no network):
//   node --test tests/scan-client-versions.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseYtdlpClients, versionDrift, IDENTITY_FIELDS } from "./scan-client-versions.mjs";

const PY = `
INNERTUBE_CLIENTS = {
    'web': {
        'INNERTUBE_CONTEXT': {
            'client': {
                'clientName': 'WEB',
                'clientVersion': '2.20260708.00.00',
            },
        },
        'INNERTUBE_CONTEXT_CLIENT_NAME': 1,
    },
    'web_music': {
        'INNERTUBE_HOST': 'music.youtube.com',
        'INNERTUBE_CONTEXT': {
            'client': {
                'clientName': 'WEB_REMIX',
                'clientVersion': '1.20260707.12.00',
            },
        },
        'INNERTUBE_CONTEXT_CLIENT_NAME': 67,
    },
    'android_vr': {
        'INNERTUBE_CONTEXT': {
            'client': {
                'clientName': 'ANDROID_VR',
                'clientVersion': '1.65.10',
                'deviceMake': 'Oculus',
                'deviceModel': 'Quest 3',
                'androidSdkVersion': 32,
                'userAgent': 'com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
                'osName': 'Android',
                'osVersion': '12L',
            },
        },
        'INNERTUBE_CONTEXT_CLIENT_NAME': 28,
    },
    'weird': {
        'INNERTUBE_CONTEXT': {
            'client': {
                'clientName': 'WEIRD',
            },
        },
    },
    'tv_simply': {
        'INNERTUBE_CONTEXT': {
            'client': {
                'clientName': 'TVHTML5_SIMPLY',
                'clientVersion': '1.0',
            },
        },
        'INNERTUBE_CONTEXT_CLIENT_NAME': 75,
    },
}
`;

test("parseYtdlpClients reads every identity field, numbers as strings, and skips a dict without a version", () => {
  const y = parseYtdlpClients(PY);
  assert.deepEqual(Object.keys(y).sort(), ["android_vr", "tv_simply", "web", "web_music"]);
  assert.deepEqual(y.web_music, { clientName: "WEB_REMIX", clientVersion: "1.20260707.12.00" });
  assert.equal(y.android_vr.androidSdkVersion, "32");
  assert.equal(y.android_vr.deviceModel, "Quest 3");
  assert.equal(y.android_vr.osVersion, "12L");
  for (const f of IDENTITY_FIELDS) assert.ok(f in y.android_vr, f);
  // The INNERTUBE_CONTEXT_CLIENT_NAME (an int outside the client dict) is never mistaken for a field.
  assert.equal(y.web_music.clientId, undefined);
});

test("versionDrift: pinned (no mirrors) never compares, unmapped keys and clientName mismatches are reported, changes are per field", () => {
  const y = parseYtdlpClients(PY);
  const clients = [
    { key: "WEB_REMIX", clientName: "WEB_REMIX", clientVersion: "1.20260213.01.00", userAgent: "Firefox", mirrors: "web_music" },
    { key: "VISIONOS_0_1", clientName: "VISIONOS", clientVersion: "0.1" },                       // pinned
    { key: "TVHTML5_SIMPLY", clientName: "TVHTML5_SIMPLY", clientVersion: "1.0", mirrors: "tv_simply" },
    { key: "GHOST", clientName: "GHOST", clientVersion: "1", mirrors: "no_such_key" },
    { key: "WRONG", clientName: "WEB_CREATOR", clientVersion: "1", mirrors: "web_music" },          // clientName mismatch
    { key: "VR", clientName: "ANDROID_VR", clientVersion: "1.65.10", osName: "Android", mirrors: "android_vr" },
  ];
  const d = versionDrift(clients, y);
  assert.deepEqual(d.pinned, ["VISIONOS_0_1"]);
  assert.deepEqual(d.unmapped, ["GHOST", "WRONG"]);
  assert.deepEqual(d.matched.map((m) => m.key), ["TVHTML5_SIMPLY"]);
  const wr = d.drift.find((x) => x.key === "WEB_REMIX");
  // yt-dlp sets no userAgent for web_music: ours is kept, only clientVersion changes.
  assert.deepEqual(wr.changes, { clientVersion: { from: "1.20260213.01.00", to: "1.20260707.12.00" } });
  assert.deepEqual(wr.fields, { clientVersion: "1.20260707.12.00" });
  const vr = d.drift.find((x) => x.key === "VR");
  assert.deepEqual(Object.keys(vr.changes).sort(), ["androidSdkVersion", "deviceMake", "deviceModel", "osVersion", "userAgent"]);
  assert.equal(vr.changes.deviceMake.from, null);
});

test("parseYtdlpClients on an empty or unrelated source yields nothing (never throws)", () => {
  assert.deepEqual(parseYtdlpClients(""), {});
  assert.deepEqual(parseYtdlpClients("def foo():\n    return 1\n"), {});
});
