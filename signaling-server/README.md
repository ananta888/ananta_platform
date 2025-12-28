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

Register:
```json
{ "type": "register", "peerId": "alice" }
```

Send signal:
```json
{ "type": "signal", "to": "bob", "payload": { "sdp": "...", "ice": "..." } }
```

List peers:
```json
{ "type": "list" }
```

Incoming signal:
```json
{ "type": "signal", "from": "alice", "payload": { "sdp": "..." } }
```
