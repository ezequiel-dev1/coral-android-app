# Spec: Integrate the web app with the Coral Print Agent (Android)

## 1. Context

The Coral Print Agent is an Android app installed on the same tablet where this web app runs in Chrome. It owns the connection to the Nextep NE-511 receipt printer (Ethernet, raw TCP/ESC-POS) and the NE-514 cash drawer. The web app cannot talk to the printer directly, so it calls a small HTTP server that the Android app exposes on the tablet's loopback interface.

This spec covers only the web app side: a client module, UI hooks and error handling.

## 2. Goal

From the web app (HTTPS site, Chrome, on the tablet), let staff:

- Open the cash drawer.
- Print a test receipt.
- Check whether the print agent is reachable.

Real order receipts and kitchen tickets are out of scope for this spec. The agent has no endpoint for them yet.

## 3. Agent API (already implemented)

Base URL: `http://127.0.0.1:8765`

Use the literal `127.0.0.1`, not `localhost`. The server binds IPv4 loopback only.

There is no token or authentication header. Access is restricted by origin: the agent only answers browser requests whose `Origin` is on its allowed list (production `https://admin.coral.restaurant`, UAT `https://admin-uat.coral.restaurant`, and LAN dev `https://192.168.1.4:3000`). Any other origin gets 403.

| Method | Path | Purpose |
|---|---|---|
| GET | `/status` | Health check. Confirms the agent bridge is running. |
| POST | `/print/test` | Print the sample test receipt. |
| POST | `/drawer/open` | Pulse the cash drawer. |

No request body or custom headers are needed.

### Responses

All responses are JSON: `{ "ok": boolean, "message": string }`.

| Status | Meaning | Notes |
|---|---|---|
| 200 | Success | `ok: true`. For print and drawer this means the bytes were sent to the printer, not that paper printed or the drawer physically opened. |
| 403 | Origin not allowed | No CORS headers are returned, so the browser reports a generic network/CORS error. |
| 404 | Unknown route | |
| 502 | Printer unreachable or write failed | `message` is human-readable, for example "Connection timed out after 3s...", "Connection refused...", "Write timed out after 5s". Safe to show to staff. |

Preflight: `OPTIONS` returns 204 with CORS headers. It allows the methods GET, POST and OPTIONS, and sets `Access-Control-Allow-Private-Network: true`. Requests are only accepted from the single origin configured in the Android app.

Timing: the agent uses a 3 s printer connect timeout and a 5 s write timeout. Requests are serialized, so calls queue. Use a client-side timeout of about 10 s.

## 4. Requirements

| ID | Requirement |
|---|---|
| W-01 | Add a `PrintAgentClient` module wrapping the three endpoints, with typed results `{ ok, message, status }`. |
| W-03 | Provide a Settings screen with a "Test agent connection" button that calls `GET /status`. |
| W-04 | Provide an "Open cash drawer" action that calls `POST /drawer/open` and shows success or the error `message`. |
| W-05 | Provide a "Print test receipt" action that calls `POST /print/test`. |
| W-06 | Disable the action button while a request is in flight, to avoid double pulses. |
| W-07 | Use an `AbortController` timeout of about 10 s. On timeout show "Print agent not responding". |
| W-08 | Map failures to clear staff-facing messages (see section 5). |
| W-09 | Never block other web app functionality if the agent is down. Failures must be non-fatal. |
| W-10 | Make the agent base URL a single config constant (`http://127.0.0.1:8765`). |

## 5. Error mapping

| Condition | Message to show |
|---|---|
| `fetch` throws `TypeError` (network error) | "Can't reach the Print Agent. Open the Coral Print Agent app on this tablet and check that the web bridge is on. If Chrome asked for local network access, allow it." |
| Abort/timeout | "Print agent not responding." |
| 403 | Same as a network error. The browser may not surface it distinctly, so also mention "check the allowed origin in the Print Agent app." |
| 502 | Show the response `message`, for example "Connection timed out after 3s. Check the IP address and that the printer is on." |
| 200 | Success toast, for example "Command sent". Do not claim "printed" or "drawer opened". |

## 6. Reference implementation

```ts
const AGENT_URL = "http://127.0.0.1:8765";

export interface AgentResult {
  ok: boolean;
  message: string;
  status: number | null; // null = network error or timeout
}

async function call(
  method: "GET" | "POST",
  path: string,
  timeoutMs = 10_000,
): Promise<AgentResult> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(`${AGENT_URL}${path}`, {
      method,
            signal: ctrl.signal,
    });
    const body = await res.json().catch(() => ({ message: res.statusText }));
    return { ok: res.ok && body.ok === true, message: body.message ?? "", status: res.status };
  } catch (e) {
    const aborted = (e as Error).name === "AbortError";
    return {
      ok: false,
      status: null,
      message: aborted
        ? "Print agent not responding."
        : "Can't reach the Print Agent. Open the app on this tablet and check that the web bridge is on.",
    };
  } finally {
    clearTimeout(timer);
  }
}

export const printAgent = {
  status: () => call("GET", "/status"),
  testPrint: () => call("POST", "/print/test"),
  openDrawer: () => call("POST", "/drawer/open"),
};
```

## 7. Browser considerations

- **Mixed content:** An HTTPS page may call `http://127.0.0.1` because Chrome treats loopback as potentially trustworthy. This does not work with `http://<LAN-IP>`.
- **Local network access prompt:** Recent Chrome versions ask the user for permission when a public website calls a loopback or local address. Expect a one-time prompt on the tablet and tap Allow. If it was denied, it can be reset under Chrome site settings for the domain. Verify the exact behavior on the tablet's Chrome version. If the site loads the calling code inside an iframe, the iframe may need a Permissions Policy allowing local network access.
- **Origin must match exactly:** The Android app only accepts the exact origin (scheme, host and port, no trailing slash), for example `https://app.example.com`. If the site is served from several hostnames (for example `www` and a bare domain), only the one configured will work. Use one canonical hostname.
- **The agent only works on the tablet itself.** It is not reachable from other devices, so the print buttons only work in Chrome on that tablet. Hide or disable them on other devices, or show the error from section 5.

## 8. Security notes

- Open-drawer actions should only be shown to roles that are allowed to use them, as appropriate for the app's permission model. The agent itself does not know who the user is.

## 9. Acceptance criteria

1. With the agent running, "Test agent connection" returns success.
2. "Open cash drawer" opens the NE-514, and "Print test receipt" prints a receipt.
3. With the page served from an origin that is not on the agent's allowed list, the call fails and the user sees the "Can't reach the Print Agent" message.
4. With the agent app closed or the bridge off, the user sees the "Can't reach the Print Agent" message, and the rest of the web app keeps working.
5. With the printer powered off, the user sees the 502 message from the agent, such as a connection timeout.
6. Rapid repeated taps on a button send only one request at a time.

## 10. Out of scope (future)

- Printing real order receipts or kitchen tickets. This needs a new agent endpoint, for example `POST /print/receipt` with a structured payload.
- Printing from devices other than this tablet.
- Cloud print queue.

## 11. Information needed from the Android side

- Any new site origin must be added to the allowed origins in the Android app.
