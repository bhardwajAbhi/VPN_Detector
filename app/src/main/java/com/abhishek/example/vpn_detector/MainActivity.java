package com.abhishek.example.vpn_detector;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class MainActivity extends AppCompatActivity {

    private TextView tvVpnStatus, tvDetails;
    private ChipGroup chipGroup;
    private MaterialButton btnRefresh, btnLive;

    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private boolean liveEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvVpnStatus = findViewById(R.id.tvVpnStatus);
        tvDetails = findViewById(R.id.tvDetails);
        chipGroup = findViewById(R.id.chipGroupTechniques);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnLive = findViewById(R.id.btnLive);

        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        btnRefresh.setOnClickListener(v -> renderReport());
        btnLive.setOnClickListener(v -> toggleLive());

        renderReport();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // avoid leaks
        disableLive();
    }

    private void renderReport() {
        VpnInspector.Report r = VpnInspector.inspect(this);

        tvVpnStatus.setText(r.vpnActive ? "VPN: ACTIVE ✅" : "VPN: NOT ACTIVE ❌");

        // Chips: techniques
        chipGroup.removeAllViews();
        for (String t : r.techniques) {
            Chip c = new Chip(this);
            c.setText(t);
            c.setCheckable(false);
            chipGroup.addView(c);
        }

        // Details block
        StringBuilder sb = new StringBuilder();
        sb.append("=== Trace Info ===\n");
        for (String d : r.details) sb.append("• ").append(d).append("\n");
        tvDetails.setText(sb.toString());
    }

    private void toggleLive() {
        if (!liveEnabled) enableLive();
        else disableLive();
    }

    private void enableLive() {
        liveEnabled = true;
        btnLive.setText("Live: ON");

        if (cm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) { runOnUiThread(() -> renderReport()); }
                @Override public void onLost(@NonNull Network network) { runOnUiThread(() -> renderReport()); }
                @Override public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities nc) {
                    runOnUiThread(() -> renderReport());
                }
            };
            cm.registerDefaultNetworkCallback(callback);
        } else {
            // For older devices, you could add a BroadcastReceiver for CONNECTIVITY_ACTION (deprecated on newer).
            renderReport();
        }
    }

    private void disableLive() {
        liveEnabled = false;
        btnLive.setText("Live");

        if (cm != null && callback != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (Exception ignored) {}
        }
        callback = null;
    }
}
