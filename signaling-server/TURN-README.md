# TURN quickstart

1) Install `coturn` so `turnserver` is on PATH.
2) Edit `signaling-server/turnserver.conf` (realm, user, ports).
3) Start both servers:
```
npm run start:turn
```
4) Provide ICE servers:
```
set ICE_SERVERS=[{"urls":["stun:stun.l.google.com:19302"]},{"urls":["turn:turn.example.com:3478"],"username":"user","credential":"pass"}]
```

Open TURN ports on the TURN host (3478/udp+tcp, optional 5349/tcp, relay range).
