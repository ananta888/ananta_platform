const http = require("http");
const WebSocket = require("ws");

const PORT = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("Ananta signaling server\n");
});

const wss = new WebSocket.Server({ server });

const clientsById = new Map();

function safeSend(ws, payload) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function unregisterPeer(peerId) {
  if (!peerId) return;
  const existing = clientsById.get(peerId);
  if (existing && existing.peerId === peerId) {
    clientsById.delete(peerId);
  }
}

wss.on("connection", (ws) => {
  ws.peerId = null;

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
        if (!peerId) {
          safeSend(ws, { type: "error", message: "missing_peer_id" });
          return;
        }
        if (clientsById.has(peerId)) {
          safeSend(ws, { type: "error", message: "peer_id_in_use" });
          return;
        }
        ws.peerId = peerId;
        clientsById.set(peerId, ws);
        safeSend(ws, { type: "registered", peerId });
        return;
      }
      case "signal": {
        const to = String(msg.to || "").trim();
        if (!to) {
          safeSend(ws, { type: "error", message: "missing_target" });
          return;
        }
        const target = clientsById.get(to);
        if (!target) {
          safeSend(ws, { type: "error", message: "target_not_found", to });
          return;
        }
        safeSend(target, {
          type: "signal",
          from: ws.peerId,
          payload: msg.payload || null,
        });
        return;
      }
      case "list": {
        safeSend(ws, { type: "peers", peers: Array.from(clientsById.keys()) });
        return;
      }
      default: {
        safeSend(ws, { type: "error", message: "unknown_type" });
      }
    }
  });

  ws.on("close", () => {
    unregisterPeer(ws.peerId);
  });

  ws.on("error", () => {
    unregisterPeer(ws.peerId);
  });
});

server.listen(PORT, () => {
  console.log(`Signaling server listening on :${PORT}`);
});
