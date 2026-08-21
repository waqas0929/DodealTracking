# Local mock server — test the agent on a real phone

Lets you run the whole flow (login -> policy -> tracking -> heartbeat -> meetings) without the real
Dodeal backend. Phone and computer must be on the SAME Wi-Fi.

## Test account (type into the app login screen)
- Company ID: `DODEAL-TEST`
- Username:   `agent1`
- Password:   `test1234`

(Any other credentials return 401, so you can test the wrong-login path too.)

## 1. Run the server
Needs Node.js (any recent version). No install step.
```
cd mock-server
node server.js
```
It prints the test account and listens on port 8080.

## 2. Find this computer's LAN IP
- Windows: `ipconfig`  -> "IPv4 Address" (e.g. 192.168.1.42)
- macOS:   `ipconfig getifaddr en0`
- Linux:   `hostname -I`

## 3. Point the app at it
In `app/src/main/java/com/dodeal/deviceagent/config/EnvironmentConfig.kt`, STAGING block:
```
host = "http://<THAT-IP>:8080",
```
Rebuild the debug APK and install it on the phone. (Debug builds allow http:// via
`app/src/debug/AndroidManifest.xml`; release stays https-only.)

## 4. Test
- Open the app, log in with the test account -> status screen fills in (policy v14, tracking on).
- Tap Start tracking, grant location -> the server console prints location points arriving.
- Try Check in / Check out with any Meeting ID.
- Watch the server terminal: every request the app makes is logged.

## Firewall
If the phone can't reach it, allow inbound TCP 8080 on the computer (Windows Defender Firewall /
macOS firewall), and confirm both devices are on the same network (not guest Wi-Fi).

## Tips
- To test a server->device command, edit `server.js`: in the `/commands` handler, return the sample
  SYNC_POLICY command instead of an empty list.
- The policy has working hours 00:00–23:59 so tracking is always active while testing.
