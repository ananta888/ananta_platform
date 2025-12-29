# Ananta Signaling Server (WebSocket)

This is a minimal WebSocket signaling server for WebRTC peer discovery and SDP/ICE exchange.

## Install

```bash
npm install
```

## Run

```bash
npm start
```

Server listens on `http://localhost:8080` by default.

## Protocol

Register (public key required):
```json
{ "type": "register", "peerId": "alice", "publicKey": "BASE64_PUBLIC_KEY" }
```

Send signal (by public key):
```json
{ "type": "signal", "to": "BASE64_PUBLIC_KEY", "payload": { "sdp": "...", "ice": "..." } }
```

List peers:
```json
{ "type": "list" }
```

Incoming signal:
```json
{ "type": "signal", "from": "alice", "fromPublicKey": "BASE64_PUBLIC_KEY", "payload": { "sdp": "..." } }
```
