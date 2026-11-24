// PERF-01 load profile (run inside AWS so client bandwidth does not limit it).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '40');
const HOLD = __ENV.HOLD || '6m';
const upload = new Trend('upload_ms', true);
const read = new Trend('read_ms', true);
const errors = new Rate('errors');
const body = 'x'.repeat(100 * 1024);

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus', startVUs: 0,
      stages: [{ duration: '2m', target: VUS }, { duration: HOLD, target: VUS }, { duration: '1m', target: 0 }],
      gracefulRampDown: '30s',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
};

export function setup() {
  const email = `perf-${Date.now()}@example.com`;
  http.post(`${BASE}/api/auth/signup`, JSON.stringify({ email, password: 'Password123!', name: 'perf' }), { headers: { 'Content-Type': 'application/json' } });
  const token = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: 'Password123!' }), { headers: { 'Content-Type': 'application/json' } }).json('data.accessToken');
  const res = http.post(`${BASE}/api/files`, { file: http.file('seed', 'seed.txt', 'text/plain') }, { headers: { Authorization: `Bearer ${token}` } });
  return { token, fileId: res.json('data.fileId') };
}

export default function (data) {
  const auth = { headers: { Authorization: `Bearer ${data.token}` } };
  const r = Math.random();
  let res;
  if (r < 0.7) {
    res = http.get(`${BASE}/api/files/${data.fileId}`, Object.assign({ tags: { name: 'GET /api/files/{id}' } }, auth));
    read.add(res.timings.duration);
  } else if (r < 0.9) {
    res = http.get(`${BASE}/api/files`, Object.assign({ tags: { name: 'GET /api/files' } }, auth));
    read.add(res.timings.duration);
  } else {
    res = http.post(`${BASE}/api/files`, { file: http.file(body, 'perf.txt', 'text/plain') }, Object.assign({ tags: { name: 'POST /api/files' } }, auth));
    upload.add(res.timings.duration);
  }
  errors.add(!check(res, { 'status 200': (x) => x.status === 200 }));
  sleep(0.1);
}

export function handleSummary(d) {
  const m = d.metrics;
  const out = {
    requests: m.http_reqs.values.count, rps: m.http_reqs.values.rate,
    error_rate: m.errors.values.rate,
    read_p50: m.read_ms.values['p(50)'], read_p95: m.read_ms.values['p(95)'], read_p99: m.read_ms.values['p(99)'],
    upload_p50: m.upload_ms.values['p(50)'], upload_p95: m.upload_ms.values['p(95)'],
    // ARCH-01: upload count to normalise NAT bytes (output only, load unchanged).
    uploads: m.upload_ms.values.count, data_sent: m.data_sent.values.count, data_received: m.data_received.values.count,
  };
  return { stdout: 'PERF_SUMMARY ' + JSON.stringify(out) + '\n' };
}
