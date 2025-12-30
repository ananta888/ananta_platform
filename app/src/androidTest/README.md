# Android UI Tests (Peer Connections)

These tests use AndroidX Test + UIAutomator (open source) and validate:
- No auto-connect when opening "Peer Connections".
- Manual Ping + Confirm handshake between two devices.

## Prerequisites
- Two devices or emulators on the same network.
- A signaling server reachable by both devices (see `signaling-server/server.js`).
- Both devices have an identity (the test creates one if missing).

## Start signaling server (example)
From repo root:
```
node signaling-server/server.js
```
For emulators, default dev URL is `ws://10.0.2.2:8080`.

## Run tests
Run on each device with a different role and the *peerKeyPrefix* of the other device.

Responder device:
```
gradlew.bat :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.role=responder -Pandroid.testInstrumentationRunnerArguments.peerKeyPrefix=ABCDEF12
```

Initiator device:
```
gradlew.bat :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.role=initiator -Pandroid.testInstrumentationRunnerArguments.peerKeyPrefix=ABCDEF12
```

Notes:
- `peerKeyPrefix` should match the other device's public key prefix shown on "Device Pairing" (e.g. `Key: ABCDEF12...`).
- The responder test waits for "Request: incoming" and confirms it.
- The initiator test sends a "Ping" from "Device Pairing" and verifies "Request: outgoing".
