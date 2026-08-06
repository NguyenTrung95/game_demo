const path = require('path');
const express = require('express');
const { WebSocketServer } = require('ws');
const roomManager = require('./roomManager');
const tugOfWarRoomManager = require('./tugOfWarRoomManager');
const { PORT } = require('./serverInfo');

const app = express();
app.use('/host', express.static(path.join(__dirname, '..', 'frontend-web', 'host')));
app.use('/tug-of-war/host', express.static(path.join(__dirname, '..', 'frontend-web', 'tug-of-war', 'host')));
app.use('/tug-of-war', express.static(path.join(__dirname, '..', 'frontend-web', 'tug-of-war', 'player')));
app.use('/', express.static(path.join(__dirname, '..', 'frontend-web', 'player')));

const server = app.listen(PORT, () => {
  console.log(`Game Server listening on http://0.0.0.0:${PORT}`);
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
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
