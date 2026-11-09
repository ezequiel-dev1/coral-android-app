# Coral Printer Bridge

Android app for Coral Reef & Beef that prints receipts and opens the cash drawer on a **Nextep NE-511** Ethernet thermal printer with a **Nextep NE-514** cash drawer.

It also runs a small local HTTP bridge, so the Coral admin web app, running in Chrome on the same tablet, can trigger the printer and the drawer.

No internet connection is required. Everything happens on the local Wi-Fi/LAN.

## Features

- Configure the kitchen printer and the register printer (IP and TCP port, default `9100`), plus the cash drawer pin (0 or 1). The drawer is connected to the register printer only. Settings persist across restarts.
- **Test Connection**: opens and closes a TCP connection to the printer.
- **Test Print**: prints a sample ESC/POS receipt and cuts the paper.
- **Open Cash Drawer**: sends the ESC/POS drawer pulse.
- **Web bridge**: a foreground service that lets allowed web apps call the same actions over HTTP on the tablet.
- Readable errors for timeouts, refused connections, unreachable hosts and socket failures.

## Hardware and network

| Component | Details |
|---|---|
| Printer | Nextep NE-511, wired to the router over Ethernet |
| Cash drawer | Nextep NE-514, connected to the printer's drawer port (RJ11/RJ12) |
| Tablet | Android 8.0+ (API 26), on the same LAN as the printer |
| Protocol | ESC/POS over a raw TCP socket, port `9100` by default |

Give the printer a reserved (static) IP in the router, so its address doesn't change after a restart. The tablet must be on the same network as the printer, not a guest network or a separate subnet.

## How it works

```
Chrome (Coral admin web app)
        │  fetch http://127.0.0.1:8765
        ▼
BridgeService (this app, loopback only)
        │  raw TCP, ESC/POS, port 9100
        ▼
Nextep NE-511 ──► NE-514 cash drawer
```

### ESC/POS commands

| Action | Bytes |
|---|---|
| Initialize | `1B 40` |
| Full cut | `1D 56 00` |
| Drawer pulse | `1B 70 <pin> 32 32` (pin 0 by default; 100 ms on, 100 ms off) |

A successful result means the bytes were sent. It does not guarantee that the printer printed or the drawer opened.

Timeouts: 3 s to connect, 5 s to write. Sockets are closed after every command.

## Web bridge

When enabled, the app listens on `http://127.0.0.1:8765`, on the tablet only. Other devices on the network can't reach it.

| Method | Path | Action |
|---|---|---|
| GET | `/status` | Health check |
| POST | `/print/test` | Print the test receipt. Optional body `{"printer":"kitchen"\|"register"}`, default kitchen |
| POST | `/print` | Print a job. Add `"printer": "kitchen"` or `"register"` (default kitchen, or register when `openDrawer` is true). `openDrawer` on the kitchen printer returns 400. Add `"logo": true` to print the Coral logo centered above the lines (kitchen printer only, otherwise 400) |
| POST | `/drawer/open` | Open the cash drawer (always the register printer) |

Responses are JSON: `{ "ok": boolean, "message": string }`.

| Status | Meaning |
|---|---|
| 200 | Command sent to the printer |
| 403 | Origin not allowed |
| 404 | Unknown route |
| 502 | Printer unreachable or write failed (`message` explains why) |

### Security model

- The server binds to loopback only.
- Browser requests are accepted only from the **allowed origins** listed in the app. Any other origin gets 403.
- There is no token. Requests that carry no `Origin` header, such as those from another app on the same tablet, are not blocked. Use this on a dedicated shop tablet and don't install untrusted apps on it.

### Allowed origins

Configured in the app as a comma-separated list of exact origins (scheme, host and port, no trailing slash, `https://` only). The defaults are:

```
https://admin.coral.restaurant
https://admin-uat.coral.restaurant
https://192.168.1.4:3000
```

The last one is a LAN development server and can be removed in production. Settings saved on a device override the defaults.

### Calling the bridge from the web app

```ts
const res = await fetch("http://127.0.0.1:8765/drawer/open", { method: "POST" });
const { ok, message } = await res.json();
```

Use `127.0.0.1`, not `localhost`. Recent versions of Chrome may ask for permission the first time a public site calls a local address. It must be allowed.

The full integration spec for the web app is in [`docs/web-app-integration-spec.md`](docs/web-app-integration-spec.md).

## Setup on the tablet

1. Install the app and open it.
2. Allow notifications when prompted. The bridge runs as a foreground service and shows a notification.
3. Enter the IP and port for each printer, then tap **Save**, **Test Connection**, **Test Print** and **Open Cash Drawer**.
4. Check the allowed origins, then turn on the bridge switch.
5. In the Android battery settings for the app, choose **Unrestricted**, so the system doesn't stop the bridge.

If the drawer doesn't open, try pin 1 in the app. Check the NE-511's pin mapping before relying on it.

## Tech stack

- Kotlin, Jetpack Compose (Material 3)
- MVVM, with a separate printer client and repository
- Kotlin Coroutines (`Dispatchers.IO`) for all network work
- Jetpack DataStore for settings
- `java.net.Socket` for the printer connection
- Min SDK 26, target and compile SDK 36

## Project structure

```
app/src/main/java/com/coral/printagent/
├── MainActivity.kt
├── bridge/BridgeService.kt           # Local HTTP server (foreground service)
├── model/PrinterResult.kt            # Results and settings model
├── printer/
│   ├── EscPosCommands.kt             # Receipt and drawer bytes
│   ├── PrinterClient.kt              # TCP socket client
│   └── PrinterRepository.kt
├── settings/PrinterSettingsRepository.kt
└── ui/
    ├── PrinterScreen.kt
    ├── PrinterViewModel.kt
    └── theme/CoralTheme.kt           # Brand colors (blue #3551A3, salmon #F3805C)
```

## Building

Requirements: JDK 17 or newer, Android SDK (API 36). Android Studio sets these up for you.

```bash
# Debug build
./gradlew :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Create a `local.properties` file with `sdk.dir=<path to your Android SDK>` if Android Studio hasn't already.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| "Connection timed out" | Wrong IP, printer off, or the tablet is on a different network or subnet |
| "Connection refused" | Wrong port, or the printer doesn't accept raw TCP on that port |
| Prints but doesn't cut | The printer may not support `GS V 0` |
| Drawer doesn't open | Try the other pin (0 or 1), and check the drawer cable |
| Web app can't reach the bridge | Bridge is off, the origin isn't in the allowed list, or Chrome blocked local network access |
| Bridge stops after a while | Set the app's battery usage to Unrestricted |

## Out of scope

Order receipts, kitchen ticket routing, a cloud print queue, and printing from other devices are not implemented.
