const os = require('os');

const PORT = process.env.PORT || 8787;

// APP_URL is set by cloud platforms (e.g. Railway, Render).
// For local dev, falls back to LAN IP.
function getBaseUrl() {
  if (process.env.APP_URL) {
    return process.env.APP_URL.replace(/\/$/, ''); // trim trailing slash
  }
  return `http://${getLanIpAddress()}:${PORT}`;
}

function getLanIpAddress() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

function buildJoinUrl(pin) {
  return `${getBaseUrl()}/?pin=${pin}`;
}

function buildTugOfWarJoinUrl(pin) {
  return `${getBaseUrl()}/tug-of-war/?pin=${pin}`;
}

module.exports = { PORT, getLanIpAddress, getBaseUrl, buildJoinUrl, buildTugOfWarJoinUrl };

