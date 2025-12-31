# TURN setup for Ananta signaling server

This repository can serve ICE/TURN config to clients and optionally start a local
coturn process alongside the signaling server.

## 1) Install coturn

Linux (Debian/Ubuntu):
```
sudo apt install coturn
```

macOS (Homebrew):
```
brew install coturn
```

Windows:
- Use WSL or install a coturn Windows build. Ensure `turnserver` is on PATH.

## 2) Configure TURN

Edit `signaling-server/turnserver.conf`:
- `realm` should match your TURN domain.
- `user=user:pass` should be replaced (or use TURN REST auth).
- `min-port` / `max-port` should match your firewall rules.
- If you have a public IP, set `relay-ip` to it.
- For TLS, add `cert=/path/fullchain.pem` and `pkey=/path/privkey.pem`.

## 3) Open ports on the TURN host

Allow inbound:
- 3478/udp and 3478/tcp (TURN)
- 5349/tcp (TURN over TLS, optional)
- Relay port range (default here: 49160-49200/udp)

The signaling server only needs its own HTTP/WS port (e.g. 8080).

## 4) Start signaling + TURN together

From repo root:
```
set START_TURN=1
set TURN_CONFIG=signaling-server/turnserver.conf
set TURN_SERVER_BIN=turnserver
node signaling-server/server.js
```

On macOS/Linux:
```
START_TURN=1 TURN_CONFIG=signaling-server/turnserver.conf TURN_SERVER_BIN=turnserver node signaling-server/server.js
```

If `START_TURN` is not set, only the signaling server starts.

## 5) Provide ICE servers to clients

Set ICE servers via environment (served at `/ice-servers` and pushed on register):
```
set ICE_SERVERS=[{"urls":["stun:stun.l.google.com:19302"]},{"urls":["turn:turn.example.com:3478"],"username":"user","credential":"pass"}]
```

This app will use that list automatically.

## Notes

- TURN is a relay fallback; direct P2P is still preferred by WebRTC.
- For production, use TURN REST auth to issue short-lived credentials.
