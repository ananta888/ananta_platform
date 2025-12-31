const http = require("http");
const path = require("path");
const { spawn } = require("child_process");
const WebSocket = require("ws");
const PORT = process.env.PORT || 8080;
let iceServers = [];
let turnProcess = null;

if (process.env.ICE_SERVERS) {
  try {
    const parsed = JSON.parse(process.env.ICE_SERVERS);
    if (Array.isArray(parsed)) {
      iceServers = parsed;
    }
  } catch (err) {
    console.warn("Invalid ICE_SERVERS JSON:", err.message);
  }
}

function startTurnServer() {
  if (!process.env.START_TURN) return;
  const bin = process.env.TURN_SERVER_BIN || "turnserver";
  const configPath = process.env.TURN_CONFIG || path.join(__dirname, "turnserver.conf");
  console.log(`Starting TURN server: ${bin} -c ${configPath}`);
  turnProcess = spawn(bin, ["-c", configPath], {
    stdio: "inherit",
    shell: process.platform === "win32",
  });
  turnProcess.on("exit", (code) => {
    console.warn(`TURN server exited with code ${code}`);
    turnProcess = null;
  });
  turnProcess.on("error", (err) => {
    console.warn(`TURN server failed to start: ${err.message}`);
    turnProcess = null;
  });
}

function stopTurnServer() {
  if (!turnProcess) return;
  try {
    turnProcess.kill();
  } catch (err) {
    console.warn(`Failed to stop TURN server: ${err.message}`);
  }
  turnProcess = null;
}

const server = http.createServer((req, res) => {
  if (req.url === "/peers") {
    const peers = Array.from(clientsByPublicKey.values()).map((client) => ({
      peerId: client.peerId || "",
      publicKey: client.publicKey || "",
      visibility: client.visibility || "private",
    }));
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ peers }, null, 2));
    return;
  }
  if (req.url === "/public-peers") {
    const peers = Array.from(clientsByPublicKey.values())
      .filter((client) => client.visibility === "public")
      .map((client) => ({
        peerId: client.peerId || "",
        publicKey: client.publicKey || "",
        visibility: client.visibility || "public",
      }));
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ peers }, null, 2));
    return;
  }
  if (req.url === "/ice-servers") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ iceServers }, null, 2));
    return;
  }
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("Ananta signaling server\n");
});

const wss = new WebSocket.Server({ server });

const clientsByPublicKey = new Map();

function broadcastPublicPeers() {
  const peers = Array.from(clientsByPublicKey.values())
    .filter((client) => client.visibility === "public")
    .map((client) => ({
      peerId: client.peerId || "",
      publicKey: client.publicKey || "",
      visibility: client.visibility || "public",
    }));
  wss.clients.forEach((client) => {
    safeSend(client, { type: "public_peers", peers });
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
        safeSend(ws, { type: "ice_servers", iceServers });
        broadcastPublicPeers();
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
      case "relay": {
        const targets = Array.isArray(msg.toPeers)
          ? msg.toPeers.map((peer) => String(peer || "").trim()).filter(Boolean)
          : [];
        const single = String(msg.to || msg.toPublicKey || "").trim();
        if (single) targets.push(single);
        if (targets.length === 0) {
          console.warn("relay: missing_target");
          safeSend(ws, { type: "error", message: "missing_target" });
          return;
        }
        const payload = typeof msg.payload === "string" ? msg.payload : null;
        console.log(`relay: from=${ws.publicKey} targets=${targets.length} payload=${payload ? payload.length : 0}`);
        targets.forEach((targetKey) => {
          const target = clientsByPublicKey.get(targetKey);
          if (!target) {
            console.warn(`relay: target_not_found ${targetKey}`);
            safeSend(ws, { type: "error", message: "target_not_found", to: targetKey });
            return;
          }
          safeSend(target, {
            type: "relay",
            from: ws.peerId,
            fromPublicKey: ws.publicKey,
            payload,
          });
        });
        return;
      }
      case "list": {
        safeSend(ws, { type: "peers", peers: Array.from(clientsByPublicKey.keys()) });
        return;
      }
      case "list_public": {
        const peers = Array.from(clientsByPublicKey.values())
          .filter((client) => client.visibility === "public")
          .map((client) => ({
            peerId: client.peerId || "",
            publicKey: client.publicKey || "",
            visibility: client.visibility || "public",
          }));
        safeSend(ws, { type: "public_peers", peers });
        return;
      }
      default: {
        safeSend(ws, { type: "error", message: "unknown_type" });
      }
    }
  });

  ws.on("close", () => {
    unregisterPeer(ws.publicKey);
    broadcastPublicPeers();
  });

  ws.on("error", () => {
    unregisterPeer(ws.publicKey);
    broadcastPublicPeers();
  });
});

server.listen(PORT, () => {
  console.log(`Signaling server listening on :${PORT}`);
  startTurnServer();
});

process.on("SIGINT", () => {
  stopTurnServer();
  process.exit(0);
});

process.on("SIGTERM", () => {
  stopTurnServer();
  process.exit(0);
});
