const http = require("http");
const WebSocket = require("ws");

const PORT = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("Ananta signaling server\n");
});

const wss = new WebSocket.Server({ server });

const clientsByPublicKey = new Map();

function broadcastPublicKeys() {
  const keys = Array.from(clientsByPublicKey.values())
    .filter((client) => client.visibility === "public")
    .map((client) => client.publicKey);
  wss.clients.forEach((client) => {
    safeSend(client, { type: "public_keys", keys });
  });
}

function safeSend(ws, payload) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function unregisterPeer(publicKey) {
  if (!publicKey) return;
  const existing = clientsByPublicKey.get(publicKey);
  if (existing && existing.publicKey === publicKey) {
    clientsByPublicKey.delete(publicKey);
  }
}

wss.on("connection", (ws) => {
  ws.peerId = null;
  ws.publicKey = null;

  ws.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (err) {
      safeSend(ws, { type: "error", message: "invalid_json" });
      return;
    }

    switch (msg.type) {
      case "register": {
        const peerId = String(msg.peerId || "").trim();
        const publicKey = String(msg.publicKey || msg.publicKeyBase64 || "").trim();
        const visibility = String(msg.visibility || "private").trim().toLowerCase();
        const visibilityMode = visibility === "public" ? "public" : "private";
        if (!publicKey) {
          safeSend(ws, { type: "error", message: "missing_public_key" });
          return;
        }
        if (clientsByPublicKey.has(publicKey)) {
          safeSend(ws, { type: "error", message: "public_key_in_use" });
          return;
        }
        ws.peerId = peerId;
        ws.publicKey = publicKey;
        ws.visibility = visibilityMode;
        clientsByPublicKey.set(publicKey, ws);
        safeSend(ws, { type: "registered", peerId, publicKey, visibility: visibilityMode });
        broadcastPublicKeys();
        return;
      }
      case "signal": {
        const to = String(msg.to || "").trim();
        const toPublicKey = String(msg.toPublicKey || "").trim();
        if (!to) {
          safeSend(ws, { type: "error", message: "missing_target" });
          return;
        }
        const target = toPublicKey ? clientsByPublicKey.get(toPublicKey) : clientsByPublicKey.get(to);
        if (!target) {
          safeSend(ws, { type: "error", message: "target_not_found", to });
          return;
        }
        safeSend(target, {
          type: "signal",
          from: ws.peerId,
          fromPublicKey: ws.publicKey,
          payload: msg.payload || null,
        });
        return;
      }
      case "list": {
        safeSend(ws, { type: "peers", peers: Array.from(clientsByPublicKey.keys()) });
        return;
      }
      case "list_public": {
        const keys = Array.from(clientsByPublicKey.values())
          .filter((client) => client.visibility === "public")
          .map((client) => client.publicKey);
        safeSend(ws, { type: "public_keys", keys });
        return;
      }
      default: {
        safeSend(ws, { type: "error", message: "unknown_type" });
      }
    }
  });

  ws.on("close", () => {
    unregisterPeer(ws.publicKey);
    broadcastPublicKeys();
  });

  ws.on("error", () => {
    unregisterPeer(ws.publicKey);
    broadcastPublicKeys();
  });
});

server.listen(PORT, () => {
  console.log(`Signaling server listening on :${PORT}`);
});
