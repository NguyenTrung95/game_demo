// Catch any uncaught errors and print them
process.on('uncaughtException', (err) => {
  console.error('[FATAL] Uncaught exception:', err);
});
process.on('unhandledRejection', (err) => {
  console.error('[FATAL] Unhandled rejection:', err);
});

console.log('[BOOT] Starting game server...');
console.log('[BOOT] __dirname =', __dirname);
console.log('[BOOT] PORT =', process.env.PORT || 8787);
console.log('[BOOT] APP_URL =', process.env.APP_URL || '(not set)');

const path = require('path');
const express = require('express');
const { WebSocketServer } = require('ws');
const roomManager = require('./roomManager');
const tugOfWarRoomManager = require('./tugOfWarRoomManager');
const { PORT } = require('./serverInfo');

const app = express();

// Health check route for debugging
app.get('/health', (req, res) => {
  res.json({ status: 'ok', port: PORT, appUrl: process.env.APP_URL || 'not set' });
});

app.use('/host', express.static(path.join(__dirname, '..', 'frontend-web', 'host')));
app.use('/tug-of-war/host', express.static(path.join(__dirname, '..', 'frontend-web', 'tug-of-war', 'host')));
app.use('/tug-of-war', express.static(path.join(__dirname, '..', 'frontend-web', 'tug-of-war', 'player')));
app.use('/', express.static(path.join(__dirname, '..', 'frontend-web', 'player')));

const server = app.listen(PORT, () => {
  console.log(`[BOOT] Game Server listening on http://0.0.0.0:${PORT}`);
});

const wss = new WebSocketServer({ noServer: true });

// Handle WebSocket upgrade manually on specific paths
server.on('upgrade', (request, socket, head) => {
  const pathname = request.url || '';
  console.log(`[WS] Upgrade request path=${pathname}`);
  
  // Accept WebSocket on /ws, /tug-of-war-ws, or root /
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

wss.on('connection', (ws, req) => {
  console.log(`[WS] New connection from ${req.headers.host} path=${req.url}`);
  const url = req.url || '';
  const isTugOfWar = url.includes('/tug-of-war-ws');

  ws.on('message', (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (isTugOfWar) {
      switch (message.type) {
        case 'create_room':
          tugOfWarRoomManager.createRoom(ws, req).catch((error) => {
            console.error('Failed to create room:', error);
          });
          break;
        case 'join_room':
          tugOfWarRoomManager.joinRoom(ws, message.payload);
          break;
        case 'switch_team':
          tugOfWarRoomManager.switchTeam(ws, message.payload);
          break;
        case 'report_taps':
          tugOfWarRoomManager.reportTapCount(ws, message.payload);
          break;
        case 'force_start':
          tugOfWarRoomManager.forceStart(ws);
          break;
        default:
          break;
      }
    } else {
      switch (message.type) {
        case 'create_room':
          roomManager.createRoom(ws, req).catch((error) => {
            console.error('Failed to create room:', error);
          });
          break;
        case 'join_room':
          roomManager.joinRoom(ws, message.payload);
          break;
        case 'report_taps':
          roomManager.reportTapCount(ws, message.payload);
          break;
        case 'force_start':
          roomManager.forceStart(ws);
          break;
        default:
          break;
      }
    }
  });

  ws.on('close', () => {
    if (isTugOfWar) {
      tugOfWarRoomManager.handleDisconnect(ws);
    } else {
      roomManager.handleDisconnect(ws);
    }
  });
});
