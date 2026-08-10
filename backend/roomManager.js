const crypto = require('crypto');
const { buildJoinUrl } = require('./serverInfo');
const { generateQrDataUrl } = require('./qr');

const LOBBY_DURATION_MS = 30_000;
const KIOSK_RESTART_DELAY_MS = 8_000;
const MAX_PLAYERS_PER_ROOM = 100;

// Tap Race: người chơi chạm càng nhanh, vịt tiến càng xa. Đích = RACE_TARGET_TAPS lần chạm.
const RACE_TARGET_TAPS = 100;
const RACE_DURATION_MS = 35_000;
const RACE_TICK_MS = 200;
// Chống cheat (macro/multi-touch bất thường) — giới hạn ngầm, không báo lỗi cho người chơi
// (theo đúng nguyên tắc "rate limiting is invisible" — báo lỗi sẽ giống như bị phạt).
const MAX_TAPS_PER_SECOND = 12;
const TAP_RATE_GRACE = 3;

// Màu định danh riêng cho từng người chơi — để TV và điện thoại nhận ra "vịt của ai"
// nhất quán (không phải đoán qua tên). Lặp lại theo thứ tự tham gia khi vượt quá 8 người.
const PLAYER_COLORS = [
  '#e21b3c', '#1368ce', '#d89e00', '#26890c',
  '#a349a4', '#ff8c00', '#00b3a6', '#ff5ea8',
];

/** pin -> RoomState */
const rooms = new Map();

function sendJson(ws, type, payload) {
  if (!ws || ws.readyState !== ws.OPEN) return;
  ws.send(JSON.stringify({ type, payload }));
}

function broadcastToPlayers(room, type, payload) {
  for (const player of room.players.values()) {
    sendJson(player.ws, type, payload);
  }
}

function notifyHost(room, type, payload) {
  sendJson(room.hostWs, type, payload);
}

function notifyRoomStats(room) {
  notifyHost(room, 'room_stats', {
    connectedCount: room.players.size,
    maxCapacity: MAX_PLAYERS_PER_ROOM,
    phase: room.phase,
  });
}

function generateUniquePin() {
  let pin;
  do {
    pin = String(crypto.randomInt(0, 10_000)).padStart(4, '0');
  } while (rooms.has(pin));
  return pin;
}

function raceSnapshot(room) {
  return Array.from(room.players.values()).map((player) => ({
    playerId: player.playerId,
    nickname: player.nickname,
    taps: player.taps,
    position: player.position,
    color: player.color,
  }));
}

function clearLobbyTimer(room) {
  if (room.lobbyTimer) {
    clearTimeout(room.lobbyTimer);
    room.lobbyTimer = null;
  }
}

function clearRaceTimers(room) {
  if (room.raceTimer) {
    clearTimeout(room.raceTimer);
    room.raceTimer = null;
  }
  if (room.raceTickInterval) {
    clearInterval(room.raceTickInterval);
    room.raceTickInterval = null;
  }
}

function destroyRoom(pin) {
  const room = rooms.get(pin);
  if (!room) return;
  clearLobbyTimer(room);
  clearRaceTimers(room);
  rooms.delete(pin);
}

async function createRoom(hostWs, req) {
  const pin = generateUniquePin();
  const room = {
    pin,
    hostWs,
    players: new Map(),
    phase: 'lobby',
    lobbyTimer: null,
    raceTimer: null,
    raceTickInterval: null,
    raceStartTime: 0,
  };
  rooms.set(pin, room);
  hostWs.roomPin = pin;
  hostWs.role = 'host';

  // Use APP_URL env var (for cloud deploy) or fallback to LAN IP (local dev)
  const joinUrl = buildJoinUrl(pin);
  console.log('[Duck Race] Room created pin=' + pin + ' joinUrl=' + joinUrl);
  const qrDataUrl = await generateQrDataUrl(joinUrl);

  sendJson(hostWs, 'room_created', {
    pin,
    joinUrl,
    qrDataUrl,
    maxCapacity: MAX_PLAYERS_PER_ROOM,
    lobbyDurationMs: LOBBY_DURATION_MS,
    targetTaps: RACE_TARGET_TAPS,
  });
  notifyRoomStats(room);

  room.lobbyTimer = setTimeout(() => tryAutoStart(room), LOBBY_DURATION_MS);
}

/**
 * Kiosk tự vận hành: hết giờ lobby thì tự bắt đầu, không cần chờ ai bấm nút trên TV.
 * Host vẫn có thể bấm "Bắt đầu chơi ngay" để bỏ qua thời gian chờ này (xem forceStart).
 */
function tryAutoStart(room) {
  if (room.phase !== 'lobby') return;
  if (room.players.size === 0) {
    room.lobbyTimer = setTimeout(() => tryAutoStart(room), LOBBY_DURATION_MS);
    return;
  }
  startRace(room);
}

function forceStart(ws) {
  const room = rooms.get(ws.roomPin);
  if (!room || ws.role !== 'host' || room.phase !== 'lobby' || room.players.size === 0) {
    return;
  }
  startRace(room);
}

function joinRoom(ws, payload) {
  const room = rooms.get(payload?.pin);
  if (!room || room.phase !== 'lobby') {
    sendJson(ws, 'join_ack', { success: false, error: 'invalid_pin_or_started' });
    return;
  }
  if (room.players.size >= MAX_PLAYERS_PER_ROOM) {
    sendJson(ws, 'join_ack', { success: false, error: 'room_full' });
    return;
  }
  const nickname = String(payload?.nickname || '').trim().slice(0, 20);
  if (!nickname) {
    sendJson(ws, 'join_ack', { success: false, error: 'nickname_required' });
    return;
  }
  const playerId = crypto.randomUUID();
  const color = PLAYER_COLORS[room.players.size % PLAYER_COLORS.length];
  room.players.set(playerId, {
    playerId,
    ws,
    nickname,
    taps: 0,
    position: 0,
    color,
  });
  ws.roomPin = room.pin;
  ws.role = 'player';
  ws.playerId = playerId;
  sendJson(ws, 'join_ack', { success: true, playerId, targetTaps: RACE_TARGET_TAPS, color });
  notifyHost(room, 'player_joined', { playerId, nickname, color });
  notifyRoomStats(room);
}

function startRace(room) {
  clearLobbyTimer(room);
  room.phase = 'racing';
  room.raceStartTime = Date.now();

  for (const player of room.players.values()) {
    player.taps = 0;
    player.position = 0;
  }

  const payload = { durationMs: RACE_DURATION_MS, targetTaps: RACE_TARGET_TAPS };
  // Host cần thêm danh sách người chơi (tên + màu) để hiện màn "xếp hàng chờ đua" trước khi
  // vào từng làn riêng — player không cần vì họ chỉ thấy nút chạm của chính mình.
  notifyHost(room, 'race_start', { ...payload, players: raceSnapshot(room) });
  broadcastToPlayers(room, 'race_start', payload);
  notifyRoomStats(room);

  clearRaceTimers(room);
  room.raceTimer = setTimeout(() => concludeRace(room, 'timeout'), RACE_DURATION_MS);
  room.raceTickInterval = setInterval(() => {
    const progressPayload = {
      positions: raceSnapshot(room),
      remainingMs: Math.max(0, RACE_DURATION_MS - (Date.now() - room.raceStartTime)),
    };
    notifyHost(room, 'race_progress', progressPayload);
    broadcastToPlayers(room, 'race_progress', progressPayload);
  }, RACE_TICK_MS);
}

/**
 * Input dạng "rate" (chạm liên tục): điện thoại gửi TỔNG số lần chạm lũy kế theo nhịp cố định,
 * không gửi từng lần chạm — mất 1 gói tin không mất 1 lần chạm, gói tiếp theo tự bù đủ.
 *
 * Chống cheat: giới hạn theo TỔNG thời gian đã trôi qua từ lúc bắt đầu đua (không phải theo
 * khoảng cách giữa 2 lần gửi) — nếu tính theo khoảng cách giữa 2 lần gửi, một lần gửi bị trễ
 * (mạng chậm, hoặc client im lặng một lúc) sẽ vô tình "tích lũy" hạn mức và cho qua cả cú burst.
 */
function reportTapCount(ws, payload) {
  const room = rooms.get(ws.roomPin);
  if (!room || ws.role !== 'player' || room.phase !== 'racing') return;

  const player = room.players.get(ws.playerId);
  if (!player) return;

  const reportedTotal = Number(payload?.cumulativeTaps);
  if (!Number.isFinite(reportedTotal) || reportedTotal <= player.taps) return;

  const secondsSinceRaceStart = Math.max(0, (Date.now() - room.raceStartTime) / 1000);
  const maxTapsAllowedByNow = Math.ceil(MAX_TAPS_PER_SECOND * secondsSinceRaceStart) + TAP_RATE_GRACE;

  player.taps = Math.min(reportedTotal, maxTapsAllowedByNow);
  player.position = Math.min(100, Math.floor((player.taps / RACE_TARGET_TAPS) * 100));

  if (player.taps >= RACE_TARGET_TAPS) {
    concludeRace(room, 'finished');
  }
}

function concludeRace(room, reason) {
  if (room.phase !== 'racing') return;
  clearRaceTimers(room);
  room.phase = 'finished';

  const ranking = raceSnapshot(room).sort((a, b) => b.taps - a.taps);

  notifyHost(room, 'race_over', { ranking, reason });

  const top10 = ranking.slice(0, 10).map((r, idx) => ({
    rank: idx + 1,
    nickname: r.nickname,
    taps: r.taps,
    color: r.color,
  }));

  ranking.forEach((entry, index) => {
    const player = room.players.get(entry.playerId);
    if (!player) return;
    sendJson(player.ws, 'race_over', {
      rank: index + 1,
      totalPlayers: ranking.length,
      taps: entry.taps,
      top10,
      reason,
    });
  });

  notifyRoomStats(room);
  scheduleKioskRestart(room);
}

function scheduleKioskRestart(room) {
  const hostWs = room.hostWs;
  setTimeout(() => {
    destroyRoom(room.pin);
    if (hostWs.readyState === hostWs.OPEN) {
      createRoom(hostWs).catch((error) => {
        console.error('Failed to auto-restart kiosk room:', error);
      });
    }
  }, KIOSK_RESTART_DELAY_MS);
}

function handleDisconnect(ws) {
  const room = rooms.get(ws.roomPin);
  if (!room) return;

  if (ws.role === 'host') {
    broadcastToPlayers(room, 'room_closed', {});
    destroyRoom(room.pin);
    return;
  }

  if (ws.role === 'player' && ws.playerId) {
    room.players.delete(ws.playerId);
    notifyHost(room, 'player_left', { playerId: ws.playerId });
    notifyRoomStats(room);

    if (room.players.size === 0) {
      if (room.phase === 'racing') {
        concludeRace(room, 'all_players_left');
      } else if (room.phase !== 'lobby') {
        destroyRoom(room.pin);
      }
    }
  }
}

module.exports = {
  createRoom,
  joinRoom,
  reportTapCount,
  forceStart,
  handleDisconnect,
};
