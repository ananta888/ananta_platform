const path = require("path");

process.env.START_TURN = process.env.START_TURN || "1";
process.env.TURN_CONFIG =
  process.env.TURN_CONFIG || path.join(__dirname, "turnserver.conf");

require("./server");
