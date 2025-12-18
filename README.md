```markdown
# VPN Detector (Android / Java) — README

This project is a small Android app (Java) that detects whether a **VPN is active** on the device and shows **what evidence/technique** was used to detect it, along with useful network trace details (interface name, DNS servers, routes, etc.).

---

## What the App Does

On launch, the app displays a simple dashboard:

- **VPN Status:** `ACTIVE ✅` or `NOT ACTIVE ❌`
- **Technique Chips:** a quick summary of which detection technique(s) triggered (e.g., `TRANSPORT_VPN`, `tun* interface UP`, etc.)
- **Trace Details:** a scrollable text area showing active network info (interface, DNS, routes, private DNS settings, and other capability flags)
- **Refresh Button:** re-runs the detection
- **Live Mode:** (toggle) automatically refreshes when network state/capabilities change

---

## Project Files Explained

### 1) `MainActivity.java` — UI + Live Updates Controller

`MainActivity` is responsible for:
- Rendering the latest VPN detection report into the UI
- Handling user actions (Refresh / Live)
- Registering and unregistering a network callback to auto-refresh the report

#### Key Components
- **TextViews**
  - `tvVpnStatus` → shows VPN status (ACTIVE / NOT ACTIVE)
  - `tvDetails` → shows multi-line trace output (monospace-style log)
- **ChipGroup**
  - `chipGroupTechniques` → chips summarizing detection techniques (evidence)
- **Buttons**
  - `btnRefresh` → runs a fresh inspection and updates UI
  - `btnLive` → toggles live monitoring (network callback)

#### Core Flow
1. **Startup (`onCreate`)**
   - Sets layout
   - Wires button clicks
   - Calls `renderReport()` once initially

2. **Generating the UI Report (`renderReport`)**
   - Calls: `VpnInspector.inspect(context)`
   - Uses returned `Report` object to:
     - Set `VPN: ACTIVE ✅` or `VPN: NOT ACTIVE ❌`
     - Create chips for each entry in `report.techniques`
     - Build the "Trace Info" multi-line block from `report.details`

3. **Live Mode (`enableLive` / `disableLive`)**
   - For API level **Android N (24)+**, it uses:
     - `ConnectivityManager.registerDefaultNetworkCallback(...)`
   - Callback triggers refresh on:
     - `onAvailable()`
     - `onLost()`
     - `onCapabilitiesChanged()`
   - On stop of activity (`onStop`), it unregisters callback to avoid leaks.

✅ Result: the UI stays updated when VPN toggles or network changes.

---

### 2) `VpnInspector.java` — VPN Detection + Trace Collection Engine

`VpnInspector` is a utility class that performs the actual inspection and returns a structured `Report`.

#### `Report` Object (Output Model)
The report contains:
- `boolean vpnActive`  
  Final decision: is VPN active or not?
- `List<String> techniques`  
  Human-readable list of which technique(s) detected VPN
- `List<String> details`  
  A detailed trace log suitable for UI display

#### Inspection Steps (`inspect(Context context)`)

##### Step A: Collect Active Network Context
The inspector queries the system networking stack:

- `ConnectivityManager.getActiveNetwork()`
- `ConnectivityManager.getNetworkCapabilities(activeNetwork)`
- `ConnectivityManager.getLinkProperties(activeNetwork)`

From these it logs:
- Active network object reference
- Active transport types (WIFI / CELLULAR / ETHERNET / VPN etc.)
- Capability flags (validated, captive portal, metered-ness)
- Interface name (e.g., `wlan0`, `rmnet_data0`, etc.)
- DNS servers list
- Route count
- Domains (if available)

This is the core “connection trace” shown in the UI.

---

## How VPN Detection Works (Techniques Used)

The app uses multiple signals (evidence) and ORs them together for the final decision.

### Technique 1 (Primary): `TRANSPORT_VPN` via NetworkCapabilities
**How it works:**
- The inspector loops over `ConnectivityManager.getAllNetworks()`
- For each network, it checks if:
  - `NetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)`

**Why it’s strong:**
- This is Android’s framework-level declaration of a VPN network transport.

If found, it:
- sets VPN detected
- logs the VPN network details
- adds a technique label to the report

---

### Technique 2 (Secondary / Heuristic): Detect `tun*` Interface
**How it works:**
- Uses `NetworkInterface.getNetworkInterfaces()`
- Checks for any interface whose name starts with `tun` (e.g., `tun0`)
- Also checks whether it is `UP`

**Why it helps:**
- Many VPNs create a TUN interface in the Linux network stack.

**Why it’s heuristic:**
- Not all VPNs expose a `tun*` interface the same way on all OEM builds.

---

### Technique 3 (Legacy / Best-effort): TYPE_VPN NetworkInfo (older API behavior)
On older Android versions, the code may use a legacy check:
- `getNetworkInfo(ConnectivityManager.TYPE_VPN)`
- `isConnectedOrConnecting()`

This is mainly for backward compatibility and should be treated as best-effort.

---

### Final Decision
The report sets:
- `vpnActive = transportVpnFound || tunInterfaceUp || legacyVpnConnected`

The techniques list shows exactly which evidence triggered.

---

## Private DNS Trace Info (Best-effort)
The inspector tries to read:
- `private_dns_mode`
- `private_dns_specifier`

via `Settings.Global.getString(...)`

This may be available on most builds, but some OEM policies can restrict it; the code handles failures gracefully and logs when it’s unavailable.

---

## Permissions Used
Minimal permission required:

- `android.permission.ACCESS_NETWORK_STATE`

No dangerous runtime permissions are required for the base detection logic.

---

## How to Run
1. Open the project in Android Studio
2. Ensure Material dependency is present (if your UI uses Material components)
3. Build + Run on a device/emulator
4. Toggle VPN ON/OFF and observe:
   - status changes
   - technique chips
   - trace info updates (especially in Live mode)

---

## Notes / Limitations
- “VPN Active” here means: Android’s network stack reports a VPN transport or VPN-like indicators.
- This does **not** guarantee that *all* traffic is tunneled (split tunneling may exist).
- Different OEM implementations can show slightly different interfaces/capabilities.

---
