// Dodeal Tracking — local mock server (multi-tenant auth + device tracking).
// Pure Node (no npm install). Serves CMS lookup, tenant login, and device endpoints on one box.
//
//   node server.js
//
// TEST LOGIN:  Client ID: DODEAL-TEST   Login ID: agent1   Password: test1234
// (CMS accepts any Client ID and returns the mock host as the tenant domain.)

const http = require('http');
const PORT = parseInt(process.argv[2] || '8080', 10);
const TEST = { user: 'agent1', pass: 'test1234' };

const iso = (o = 0) => new Date(Date.now() + o).toISOString();
const send = (res, code, obj) => {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(obj === undefined ? '{}' : JSON.stringify(obj));
};
const readBody = (req) => new Promise((resolve) => {
  let d = ''; req.on('data', c => d += c);
  req.on('end', () => { try { resolve(d ? JSON.parse(d) : {}); } catch { resolve({}); } });
});

function policy() {
  const allDay = { enabled: true, start: '00:00', end: '23:59' };
  return {
    policy_version: '14', tracking_enabled: true, timezone: 'Asia/Dubai',
    working_hours: { monday: allDay, tuesday: allDay, wednesday: allDay, thursday: allDay, friday: allDay, saturday: allDay, sunday: allDay },
    location_interval_seconds: 15, minimum_distance_meters: 10, high_accuracy_enabled: true,
    heartbeat_interval_seconds: 300, batch_upload_interval_seconds: 60, offline_queue_limit: 10000,
    geofencing_enabled: true, meeting_checkin_enabled: true, device_commands_enabled: true,
    geofences: [{ geofence_id: 'gf_office', name: 'Head office', latitude: 25.2048, longitude: 55.2708, radius_meters: 150, enabled: true }],
  };
}

const server = http.createServer(async (req, res) => {
  const url = req.url.split('?')[0];
  const body = await readBody(req);
  console.log(`${new Date().toLocaleTimeString()}  ${req.method} ${url}`);
  if (url.endsWith('/location/batch')) console.log(`    -> batch ${((body.events) || []).length} event(s)`);

  const p = url.replace(/^\/api/, '');

  // Step 1: CMS client lookup. Return THIS host as the tenant domain so tenant calls come back here.
  const m = p.match(/^\/crmclients\/(.+)$/);
  if (req.method === 'GET' && m) {
    return send(res, 200, {
      client_id: m[1],
      client_name: 'Test Company',
      client_domain: req.headers.host,   // e.g. 192.168.1.100:8080
      package: null,
      status: 'active',
    });
  }

  // Step 3: tenant login.
  if (req.method === 'POST' && p === '/login') {
    if (body.loginId === TEST.user && body.password === TEST.pass) {
      return send(res, 200, { token: 'mock_token_' + Date.now(), expires_in: 43200, device_id: 'dv_' + body.loginId });
    }
    return send(res, 401, { error: 'invalid_credentials' });
  }

  // Device endpoints.
  if (req.method === 'POST' && p === '/addLocation') { console.log(`    -> addLocation ${body.latitude},${body.longitude}`); return send(res, 200, { success: true }); }
  if (req.method === 'GET' && p === '/device/policy') return send(res, 200, policy());
  if (req.method === 'GET' && p === '/device/commands') return send(res, 200, { commands: [] });
  // location, location/batch, heartbeat, events, commands/{id}/ack, geofence/event, meeting/* -> 200 {}
  return send(res, 200, {});
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('========================================================');
  console.log(` Dodeal Tracking mock on http://0.0.0.0:${PORT}`);
  console.log(' LOGIN  Client ID: DODEAL-TEST  Login ID: agent1  Password: test1234');
  console.log(` Set STAGING cmsBaseUrl + host to  http://<LAN-IP>:${PORT}`);
  console.log('========================================================');
});
