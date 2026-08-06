const QRCode = require('qrcode');

function generateQrDataUrl(text) {
  return QRCode.toDataURL(text, { margin: 1, width: 320 });
}

module.exports = { generateQrDataUrl };
