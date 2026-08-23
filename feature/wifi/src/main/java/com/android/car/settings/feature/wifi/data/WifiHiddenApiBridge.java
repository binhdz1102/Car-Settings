package com.android.car.settings.feature.wifi.data;

import android.annotation.SuppressLint;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Typed boundary for platform-only Wi-Fi APIs.
 *
 * <p>Java is used here because its bootstrap classpath can be replaced with the matching AOSP
 * framework JARs. Kotlin callers only see this stable, testable boundary.
 */
@SuppressLint({"MissingPermission", "InlinedApi", "NewApi"})
public final class WifiHiddenApiBridge {
    private WifiHiddenApiBridge() {}

    public interface HotspotListener {
        void onStateChanged(int state, int failureReason);

        void onClientsChanged(int count);
    }

    public interface TetheringCallback {
        void onStarted();

        void onFailed(int error);
    }

    public static final class HotspotConfig {
        public final String ssid;
        public final String passphrase;
        public final int securityType;
        public final int band;
        public final int[] bands;
        public final boolean autoShutdownEnabled;
        public final int maxClients;

        HotspotConfig(SoftApConfiguration configuration) {
            ssid = configuration.getSsid();
            passphrase = configuration.getPassphrase();
            securityType = configuration.getSecurityType();
            band = configuration.getBand();
            bands = configuration.getBands();
            autoShutdownEnabled = configuration.isAutoShutdownEnabled();
            maxClients = configuration.getMaxNumberOfClients();
        }
    }

    public static void registerHotspotCallback(
            WifiManager manager,
            Executor executor,
            HotspotListener listener) {
        manager.registerSoftApCallback(
                executor,
                new WifiManager.SoftApCallback() {
                    @Override
                    public void onStateChanged(int state, int failureReason) {
                        listener.onStateChanged(state, failureReason);
                    }

                    @Override
                    public void onConnectedClientsChanged(List<WifiClient> clients) {
                        listener.onClientsChanged(clients.size());
                    }
                });
    }

    public static HotspotConfig getHotspotConfiguration(WifiManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        SoftApConfiguration configuration = manager.getSoftApConfiguration();
        return configuration == null ? null : new HotspotConfig(configuration);
    }

    public static boolean setHotspotConfiguration(
            WifiManager manager,
            String ssid,
            String password,
            int securityType,
            int band,
            boolean dualBand,
            boolean autoShutdownEnabled,
            int maxClients) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        SoftApConfiguration.Builder builder =
                new SoftApConfiguration.Builder()
                        .setSsid(ssid)
                        .setAutoShutdownEnabled(autoShutdownEnabled);
        if (securityType == SoftApConfiguration.SECURITY_TYPE_OPEN) {
            builder.setPassphrase(null, securityType);
        } else {
            builder.setPassphrase(password, securityType);
        }
        if (dualBand) {
            builder.setBands(
                    new int[] {
                        SoftApConfiguration.BAND_2GHZ,
                        SoftApConfiguration.BAND_5GHZ,
                    });
        } else if (band != 0) {
            builder.setBand(band);
        }
        if (maxClients > 0) {
            builder.setMaxNumberOfClients(maxClients);
        }
        return manager.setSoftApConfiguration(builder.build());
    }

    public static void startTethering(
            TetheringManager manager,
            Executor executor,
            TetheringCallback callback) {
        if (Build.VERSION.SDK_INT < 36) {
            callback.onFailed(-1);
            return;
        }
        manager.startTethering(
                ConnectivityManager.TETHERING_WIFI,
                executor,
                new TetheringManager.StartTetheringCallback() {
                    @Override
                    public void onTetheringStarted() {
                        callback.onStarted();
                    }

                    @Override
                    public void onTetheringFailed(int error) {
                        callback.onFailed(error);
                    }
                });
    }

    public static void stopTethering(TetheringManager manager) {
        if (Build.VERSION.SDK_INT < 36) {
            throw new UnsupportedOperationException("Tethering is not supported on this platform");
        }
        manager.stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    public static boolean getAutoJoin(WifiConfiguration configuration) {
        return configuration.allowAutojoin;
    }

    public static int getMeteredOverride(WifiConfiguration configuration) {
        return configuration.meteredOverride;
    }

    public static boolean setMeteredOverride(
            WifiManager manager,
            int networkId,
            int meteredOverride) {
        for (WifiConfiguration configuration : manager.getConfiguredNetworks()) {
            if (configuration.networkId == networkId) {
                configuration.meteredOverride = meteredOverride;
                return manager.updateNetwork(configuration) >= 0;
            }
        }
        return false;
    }
}
