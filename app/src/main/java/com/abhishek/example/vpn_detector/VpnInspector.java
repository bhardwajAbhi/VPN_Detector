package com.abhishek.example.vpn_detector;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class VpnInspector {

    private VpnInspector() {}

    public static final class Report {
        public boolean vpnActive;
        public final List<String> techniques = new ArrayList<>();
        public final List<String> details = new ArrayList<>();
    }

    public static Report inspect(Context context) {
        Report r = new Report();

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            r.details.add("ConnectivityManager: null");
            return r;
        }

        // --- Default / active network
        Network active = cm.getActiveNetwork();
        if (active == null) {
            r.details.add("Active network: null (no connectivity?)");
        } else {
            r.details.add("Active network: " + active);

            NetworkCapabilities activeCaps = cm.getNetworkCapabilities(active);
            if (activeCaps != null) {
                r.details.add("Active transports: " + transportsString(activeCaps));
                r.details.add("Validated: " + hasCapabilitySafe(activeCaps, NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                r.details.add("Captive portal: " + hasCapabilitySafe(activeCaps, NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));
                r.details.add("Not metered: " + hasCapabilitySafe(activeCaps, NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
            } else {
                r.details.add("Active NetworkCapabilities: null");
            }

            LinkProperties lp = cm.getLinkProperties(active);
            if (lp != null) {
                r.details.add("Interface: " + nullToDash(lp.getInterfaceName()));
                r.details.add("DNS servers: " + lp.getDnsServers());
                r.details.add("Routes count: " + lp.getRoutes().size());
                r.details.add("Domains: " + nullToDash(lp.getDomains()));
            } else {
                r.details.add("Active LinkProperties: null");
            }
        }

        // --- Technique #1: look for TRANSPORT_VPN (API 23+)
        boolean capsVpn = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network[] all = cm.getAllNetworks();
            for (Network n : all) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    capsVpn = true;
                    r.details.add("VPN network found: " + n);
                    LinkProperties vpnLp = cm.getLinkProperties(n);
                    if (vpnLp != null) {
                        r.details.add("VPN interface: " + nullToDash(vpnLp.getInterfaceName()));
                        r.details.add("VPN DNS servers: " + vpnLp.getDnsServers());
                    }
                    break;
                }
            }
            if (capsVpn) r.techniques.add("Transport: TRANSPORT_VPN");
        } else {
            r.details.add("TRANSPORT_VPN check: skipped (API < 23)");
        }

        // --- Technique #2: heuristic tun* interface
        boolean tunUp = isTunInterfaceUp(r.details);
        if (tunUp) r.techniques.add("Heuristic: tun* interface UP");

        // --- Technique #3 (optional legacy): best-effort only
        boolean legacyVpn = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            legacyVpn = isLegacyVpnConnected(cm);
            if (legacyVpn) r.techniques.add("Legacy: TYPE_VPN connected");
        }

        // --- Extra trace info
        addPrivateDnsInfo(context, r.details);

        // Final decision (OR of signals)
        r.vpnActive = capsVpn || tunUp || legacyVpn;

        if (!r.vpnActive) {
            r.techniques.add("No VPN indicators found");
        }

        return r;
    }

    private static String transportsString(NetworkCapabilities caps) {
        List<String> t = new ArrayList<>();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) t.add("WIFI");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) t.add("CELLULAR");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) t.add("ETHERNET");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) t.add("VPN");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) t.add("BT");
        return t.toString();
    }

    private static boolean isTunInterfaceUp(List<String> detailsOut) {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                String name = intf.getName();
                if (name != null && name.startsWith("tun")) {
                    boolean up = intf.isUp();
                    detailsOut.add("Interface check: " + name + " up=" + up);
                    if (up) return true;
                }
            }
        } catch (Exception e) {
            detailsOut.add("Tun check error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacyVpnConnected(ConnectivityManager cm) {
        try {
            android.net.NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN);
            return ni != null && ni.isConnectedOrConnecting();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasCapabilitySafe(NetworkCapabilities caps, int cap) {
        try { return caps.hasCapability(cap); } catch (Exception ignored) { return false; }
    }

    private static void addPrivateDnsInfo(Context context, List<String> out) {
        // Clean, allowed info via Settings.Global (no special permission needed for these on most builds),
        // but some OEMs may restrict. We handle errors gracefully.
        try {
            String mode = android.provider.Settings.Global.getString(
                    context.getContentResolver(), "private_dns_mode");
            String host = android.provider.Settings.Global.getString(
                    context.getContentResolver(), "private_dns_specifier");
            out.add("Private DNS mode: " + nullToDash(mode));
            out.add("Private DNS host: " + nullToDash(host));
        } catch (Exception e) {
            out.add("Private DNS info: unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String nullToDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s;
    }
}
