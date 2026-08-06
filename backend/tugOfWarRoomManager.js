const crypto = require('crypto');
const { buildTugOfWarJoinUrl } = require('./serverInfo');
const { generateQrDataUrl } = require('./qr');

const LOBBY_DURATION_MS = 30_000;
const KIOSK_RESTART_DELAY_MS = 10_000;
const MAX_PLAYERS_PER_ROOM = 100;

// Tug of War: Mỗi bên kéo về phía mình. Đích thắng ngay lập tức = TARGET_NET_TAPS.
const TARGET_NET_TAPS = 50; 
const RACE_DURATION_MS = 30_000;
const RACE_TICK_MS = 200;

// Chống cheat
const MAX_TAPS_PER_SECOND = 15;
const TAP_RATE_GRACE = 5;

// Shades of Red/Orange for Team Red
const RED_COLORS = [
  '#ef4444', '#dc2626', '#b91c1c', '#ea580c', '#f87171',
];

// Shades of Blue/Cyan for Team Blue
const BLUE_COLORS = [
  '#3b82f6', '#2563eb', '#1d4ed8', '#0284c7', '#60a5fa',
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

function broadcastLobbyUpdate(room) {
  let redCount = 0;
  let blueCount = 0;
  for (const p of room.players.values()) {
    if (p.team === 'red') redCount++;
    else blueCount++;
  }
  
  broadcastToPlayers(room, 'lobby_update', {
    redCount,
    blueCount,
  });
  
  notifyHost(room, 'lobby_players_update', {
    players: Array.from(room.players.values()).map(p => ({
      playerId: p.playerId,
      nickname: p.nickname,
      color: p.color,
      team: p.team
    }))
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
    color: player.color,
    team: player.team,
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

async function createRoom(hostWs) {
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
    redTaps: 0,
    blueTaps: 0,
    delta: 0,
  };
  rooms.set(pin, room);
  hostWs.roomPin = pin;
  hostWs.role = 'host';

  const joinUrl = buildTugOfWarJoinUrl(pin);
  const qrDataUrl = await generateQrDataUrl(joinUrl);

  sendJson(hostWs, 'room_created', {
    pin,
    joinUrl,
    qrDataUrl,
    maxCapacity: MAX_PLAYERS_PER_ROOM,
    lobbyDurationMs: null,
    targetNetTaps: TARGET_NET_TAPS,
  });
  notifyRoomStats(room);
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

  // Calculate current teams counts for balancing
  let redCount = 0;
  let blueCount = 0;
  for (const p of room.players.values()) {
    if (p.team === 'red') redCount++;
    else blueCount++;
  }

  const team = redCount <= blueCount ? 'red' : 'blue';
  const playerId = crypto.randomUUID();
  
  let color;
  if (team === 'red') {
    color = RED_COLORS[redCount % RED_COLORS.length];
  } else {
    color = BLUE_COLORS[blueCount % BLUE_COLORS.length];
  }

  room.players.set(playerId, {
    playerId,
    ws,
    nickname,
    taps: 0,
    color,
    team,
  });

  ws.roomPin = room.pin;
  ws.role = 'player';
  ws.playerId = playerId;

  sendJson(ws, 'join_ack', { success: true, playerId, targetNetTaps: TARGET_NET_TAPS, color, team });
  broadcastLobbyUpdate(room);
  notifyRoomStats(room);
}

function startRace(room) {
  clearLobbyTimer(room);
  room.phase = 'racing';
  room.raceStartTime = Date.now();
  room.redTaps = 0;
  room.blueTaps = 0;
  room.delta = 0;

  for (const player of room.players.values()) {
    player.taps = 0;
  }

  const payload = { durationMs: RACE_DURATION_MS, targetNetTaps: TARGET_NET_TAPS };
  
  // Host needs player list with team properties
  notifyHost(room, 'race_start', { ...payload, players: raceSnapshot(room) });
  
  // Send customized start message to players with their team
  for (const player of room.players.values()) {
    sendJson(player.ws, 'race_start', { ...payload, team: player.team });
  }

  notifyRoomStats(room);

  clearRaceTimers(room);
  room.raceTimer = setTimeout(() => concludeRace(room, 'timeout'), RACE_DURATION_MS);
  room.raceTickInterval = setInterval(() => {
    const progressPayload = {
      delta: room.delta,
      redTaps: room.redTaps,
      blueTaps: room.blueTaps,
      remainingMs: Math.max(0, RACE_DURATION_MS - (Date.now() - room.raceStartTime)),
      players: raceSnapshot(room),
    };
    notifyHost(room, 'race_progress', progressPayload);
    
    // Broadcast progress to players, adding their individual taps
    for (const player of room.players.values()) {
      sendJson(player.ws, 'race_progress', {
        delta: room.delta,
        redTaps: room.redTaps,
        blueTaps: room.blueTaps,
        remainingMs: progressPayload.remainingMs,
        myTaps: player.taps,
      });
    }
  }, RACE_TICK_MS);
}

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

  // Recalculate team total taps
  let redTotal = 0;
  let blueTotal = 0;
  for (const p of room.players.values()) {
    if (p.team === 'red') {
      redTotal += p.taps;
    } else {
      blueTotal += p.taps;
    }
  }

  room.redTaps = redTotal;
  room.blueTaps = blueTotal;
  room.delta = blueTotal - redTotal; // Negative is Red team leading, Positive is Blue team leading

  // Win condition: check if either team reached the target lead
  if (room.delta <= -TARGET_NET_TAPS) {
    concludeRace(room, 'finished');
  } else if (room.delta >= TARGET_NET_TAPS) {
    concludeRace(room, 'finished');
  }
}

function concludeRace(room, reason) {
  if (room.phase !== 'racing') return;
  clearRaceTimers(room);
  room.phase = 'finished';

  let winnerTeam = 'tie';
  if (room.delta < 0) {
    winnerTeam = 'red';
  } else if (room.delta > 0) {
    winnerTeam = 'blue';
  }

  // Sort players by taps to find overall MVP and individual stats
  const ranking = raceSnapshot(room).sort((a, b) => b.taps - a.taps);

  notifyHost(room, 'race_over', { 
    winnerTeam, 
    delta: room.delta, 
    redTaps: room.redTaps, 
    blueTaps: room.blueTaps, 
    ranking, 
    reason 
  });

  const top10 = ranking.slice(0, 10).map((r, idx) => ({
    rank: idx + 1,
    nickname: r.nickname,
    taps: r.taps,
    color: r.color,
    team: r.team,
  }));

  ranking.forEach((entry, index) => {
    const player = room.players.get(entry.playerId);
    if (!player) return;
    sendJson(player.ws, 'race_over', {
      winnerTeam,
      myTeam: player.team,
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

function switchTeam(ws, payload) {
  const room = rooms.get(ws.roomPin);
  if (!room || room.phase !== 'lobby') return;

  const player = room.players.get(ws.playerId);
  if (!player) return;

  const newTeam = payload?.team;
  if (newTeam !== 'red' && newTeam !== 'blue') return;
  if (player.team === newTeam) return;

  player.team = newTeam;

  // Re-assign color shade based on target team
  let sameTeamCount = 0;
  for (const p of room.players.values()) {
    if (p.team === newTeam && p.playerId !== player.playerId) {
      sameTeamCount++;
    }
  }

  if (newTeam === 'red') {
    player.color = RED_COLORS[sameTeamCount % RED_COLORS.length];
  } else {
    player.color = BLUE_COLORS[sameTeamCount % BLUE_COLORS.length];
  }

  sendJson(ws, 'join_ack', { 
    success: true, 
    playerId: player.playerId, 
    targetNetTaps: TARGET_NET_TAPS, 
    color: player.color, 
    team: player.team 
  });

  broadcastLobbyUpdate(room);
  notifyRoomStats(room);
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
    broadcastLobbyUpdate(room);
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
  switchTeam,
  reportTapCount,
  forceStart,
  handleDisconnect,
};
