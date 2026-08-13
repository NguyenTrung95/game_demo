# Kế hoạch: Chơi offline (không cần Internet) qua QR trên Android TV

**Trạng thái:** Tài liệu phân tích/kế hoạch — chưa triển khai code.
**Phạm vi:** `AndroidTVGame` (app TV) + tham khảo logic từ `game-duckrace` (server Node.js hiện tại).

---

## 1. Mục tiêu

Cho phép chơi các game kiểu Kahoot (Duck Race, Tug of War, ...) chỉ với:
- 1 TV chạy Android TV app, và
- Nhiều điện thoại quét QR hiển thị trên TV để tham gia,

**không cần Internet (WAN)**, và hỗ trợ **2 hình thức kết nối khác nhau**:

| Hình thức | Khi nào dùng | Yêu cầu hạ tầng |
|---|---|---|
| **A. LAN có sẵn** | Nhà/lớp học đã có Wi-Fi (có thể không có Internet) | TV và điện thoại cùng nối vào 1 Wi-Fi/router có sẵn |
| **B. Wi-Fi do TV tự tạo** | Không có router nào cả (ngoài công viên, phòng trống...) | Chỉ cần TV — TV tự phát Wi-Fi, điện thoại quét QR để nối vào |

---

## 2. Hiện trạng (đã khảo sát)

### 2.1 `game-duckrace` (Node.js)
- Server Express + WebSocket (`backend/index.js`), phục vụ 2 trang tĩnh: `host/index.html` (màn TV) và `player/index.html` (màn điện thoại).
- Luồng tham gia: host tạo `PIN`, sinh QR encode URL dạng `http://<IP-LAN-của-server>:8787/tug-of-war/?pin=XXXX`. Điện thoại quét → mở trình duyệt → nhập tên → WebSocket `join_room`.
- **Điểm quan trọng:** cơ chế này **đã hoạt động không cần Internet** — QR chỉ chứa IP nội bộ (LAN), không phải domain public. Vấn đề duy nhất là server Node.js phải chạy trên *một máy riêng* (laptop/PC) cùng mạng — TV không tự host được.

### 2.2 `AndroidTVGame` (Kotlin/Compose)
- Là app Compose thông thường (không phải app Leanback/TV thật), có màn `HomeScreen` liệt kê các game, và `GameWebViewScreen` mở **WebView trỏ tới URL Railway** (`https://game-demo-production-4101.up.railway.app/...`) — nghĩa là **đang phụ thuộc Internet thật (cloud)**.
- Có sẵn logic forward phím D-pad → sự kiện keyboard JS trong WebView (`MainActivity.kt`), khá hữu ích để giữ lại.
- **Chưa có** bất kỳ logic QR, server nhúng, WifiP2p, hay Hotspot nào.

### 2.3 Kết luận
Muốn "TV tự chơi được, không cần máy chủ riêng, không cần Internet", phải:
1. Bỏ phụ thuộc Railway — server phải chạy **ngay trong app Android TV**.
2. Thêm khả năng TV tự tạo mạng Wi-Fi riêng cho hình thức B.

---

## 3. Kiến trúc đề xuất

### 3.1 Server nhúng: Kotlin/Ktor (đã chốt)

Viết lại server bằng **Ktor** (embedded server, engine Netty hoặc CIO) chạy trong 1 `Service` (foreground service để không bị Android kill khi app xuống nền) của app Android TV:
- Ktor `routing` phục vụ file tĩnh (`host` HTML/CSS/JS và `player` HTML/CSS/JS) từ `assets/` — gần như copy nguyên trang hiện tại của `game-duckrace`, chỉ đổi lại việc phục vụ file.
- Ktor `webSocket {}` route thay cho `ws` (`backend/index.js`), port nguyên logic phòng chơi (room, PIN, teams, tap counting, tug-of-war progress, timer) từ JS sang Kotlin — đây là phần **tốn công nhất** vì phải đọc lại toàn bộ `backend/index.js` (state machine của room/race) và viết lại tương đương bằng Kotlin.
- **Lý do chọn Ktor thay vì nhúng Node.js runtime:** nhúng Node.js vào APK (qua các thư viện như Node-on-Android) nặng, khó build, dễ vỡ trên các bản Android TV OEM khác nhau. Ktor native nhẹ, ổn định, tích hợp tốt với lifecycle Android.
- **Việc KHÔNG cần viết lại:** toàn bộ HTML/CSS/JS phía client (host + player) — chỉ cần đổi endpoint constants nếu có, phần còn lại (canvas/DOM logic, sprite avatar, tug-of-war animation vừa làm) giữ nguyên 100%.

### 3.2 Hình thức A — dùng LAN có sẵn

- App lấy IP LAN hiện tại của TV (`WifiManager` / `ConnectivityManager`, lấy IP của interface Wi-Fi đang kết nối).
- Sinh QR: `http://<IP-TV>:<PORT>/tug-of-war/?pin=XXXX` (giữ nguyên định dạng URL hiện tại của game-duckrace).
- Điện thoại: đã join sẵn cùng Wi-Fi (người chơi tự bấm chọn Wi-Fi như bình thường), quét QR → mở trình duyệt → vào thẳng trang player.
- **Không cần Internet thật**, chỉ cần Wi-Fi/router tồn tại (có thể là router nhà không có dây WAN cắm vào).

### 3.3 Hình thức B — TV tự tạo Wi-Fi (không cần router nào cả)

**Đã loại Wi-Fi Direct**, vì: Wi-Fi Direct yêu cầu bên nhận (điện thoại) chủ động vào `Settings → Wi-Fi → Wi-Fi Direct` để bắt cặp thủ công, hoặc phải có app riêng gọi `WifiP2pManager` — không thể "quét 1 mã QR là tự nối" với điện thoại bất kỳ (đặc biệt iPhone không hỗ trợ Wi-Fi Direct chuẩn ở tầng OS cho bên thứ 3).

**Chọn: `WifiManager.LocalOnlyHotspot`** (API có từ Android 8):
- App gọi `startLocalOnlyHotspot()` → Android tự phát ra 1 Wi-Fi (SSID + password ngẫu nhiên hoặc do app đặt) **không cần Internet, không cần SIM/router**.
- Sinh QR chuẩn Wi-Fi (`WIFI:T:WPA;S:<ssid>;P:<password>;;`) — **camera mặc định của cả Android và iOS đều tự nhận diện** QR này và hiện nút "Kết nối Wi-Fi" ngay, không cần cài app.
- Sau khi điện thoại nối vào hotspot của TV, cần **bước 2**: mở trình duyệt vào trang player. Có 2 cách:
  - (i) Hiện **QR thứ hai** (URL player) ngay sau khi hotspot bật, người chơi quét tiếp — đơn giản, chắc chắn hoạt động.
  - (ii) Dùng cơ chế **captive portal** (khi điện thoại nối Wi-Fi không Internet, hệ điều hành tự mở trang "đăng nhập Wi-Fi") để tự động chuyển tới trang player — mượt hơn nhưng phức tạp, không đảm bảo hoạt động đồng nhất trên mọi hãng điện thoại.
  - **Khuyến nghị: làm (i) trước** (2 QR, đơn giản/chắc chắn), có thể nâng cấp (ii) sau nếu cần trải nghiệm mượt hơn.
- **Hạn chế cần lưu ý:** `LocalOnlyHotspot` giới hạn số client kết nối cùng lúc theo thiết bị/hãng (thường 5–10, có máy ít hơn), và trên một số Android TV OEM, API này có thể bị tắt/hạn chế (khác với điện thoại) — **cần kiểm tra thực tế trên thiết bị Android TV mục tiêu trước khi chốt hướng này**, đây là rủi ro kỹ thuật lớn nhất của toàn kế hoạch.

### 3.4 Sơ đồ luồng tổng quát

```
[Chọn hình thức A hoặc B trên TV]
        |
   A: TV lấy IP LAN hiện có       B: TV gọi startLocalOnlyHotspot()
        |                                |
   Sinh QR "URL player"          Sinh QR "WIFI:...;;" (nối Wi-Fi)
        |                                |
   Điện thoại quét → mở trình     Điện thoại quét → tự nối Wi-Fi
   duyệt → vào trang player              |
                                   Sinh tiếp QR "URL player"
                                          |
                                   Điện thoại quét → mở trình
                                   duyệt → vào trang player
        |________________________________|
                     |
        Ktor server (chạy trong TV app) nhận WebSocket,
        chạy logic game (port từ backend/index.js)
```

---

## 4. Kế hoạch di trú (những gì cần port từ Node.js sang Kotlin)

| Thành phần | Nguồn (game-duckrace) | Việc cần làm |
|---|---|---|
| Static file serving | `express.static(...)` cho `host/`, `player/` | Copy HTML/CSS/JS vào `app/src/main/assets/`, Ktor route serve từ assets |
| WebSocket protocol | `backend/index.js` (`ws` library, message `{type, payload}`) | Viết lại bằng Ktor `webSocket{}`, giữ nguyên format message để **không phải sửa client JS** |
| Room/PIN logic | Tạo phòng, generate PIN 4 số, track player list | Port sang 1 class Kotlin (`RoomManager`) |
| Race/Tug-of-war state machine | Tính taps, delta, timer, kết thúc trận, ranking | Port sang Kotlin, giữ nguyên các hằng số (target taps, thời gian 30s...) |
| QR generation | `qrcode` npm package | Dùng lib Kotlin/Java tương đương (ví dụ ZXing) — sinh cả QR "URL" và QR "WIFI:" |
| Lấy IP LAN | `serverInfo.js` (Node `os.networkInterfaces()`) | `WifiManager`/`NetworkInterface` phía Android |

**Ước lượng:** đây là phần việc lớn nhất — về khối lượng gần như viết lại toàn bộ `backend/` bằng Kotlin. Toàn bộ `frontend-web/` (HTML/CSS/JS) tái sử dụng được ~100%.

---

## 5. Đề xuất các giai đoạn triển khai

1. **Giai đoạn 0 (đã xong):** Xác nhận kiến trúc hiện tại, chốt hướng Ktor + LocalOnlyHotspot.
2. **Giai đoạn 1 — Server nhúng + Hình thức A (LAN):** Port `backend/index.js` sang Ktor, đóng gói `frontend-web/` vào assets, sửa `AndroidTVGame` để tự host thay vì trỏ Railway. Kiểm thử với LAN có sẵn (Wi-Fi nhà/văn phòng). Đây là bản MVP dùng được ngay, không phụ thuộc Internet.
3. **Giai đoạn 2 — Hình thức B (Hotspot):** Thêm `LocalOnlyHotspot`, sinh QR Wi-Fi, luồng 2 QR. Kiểm thử thực tế trên thiết bị Android TV mục tiêu (rủi ro OEM ở mục 3.3).
4. **Giai đoạn 3 (tuỳ chọn):** Nâng cấp captive-portal để chỉ cần 1 QR cho hình thức B; UI cho người host chọn A/B trên TV.

---

## 6. Câu hỏi còn mở (cần chốt trước khi code)

- Thiết bị Android TV thực tế sẽ dùng là gì/version Android nào? (ảnh hưởng tính khả dụng của `LocalOnlyHotspot`).
- Số lượng người chơi tối đa cần hỗ trợ đồng thời qua hotspot? (ảnh hưởng chọn hotspot vs các phương án khác nếu vượt giới hạn client).
- Có cần hỗ trợ iPhone không? (Wi-Fi Direct đã loại vì lý do này; hotspot QR thì cả 2 nền tảng đều ổn).
- Ưu tiên làm giai đoạn 1 (LAN) trước rồi mới quyết có cần giai đoạn 2 (Hotspot) hay không?
