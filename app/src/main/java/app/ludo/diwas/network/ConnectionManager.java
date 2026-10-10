package app.ludo.diwas.network;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the state, preferences, and lifecycle of Controller Mode and networking.
 */
public class ConnectionManager {
    private static final String PREF_NAME = "ludo_controller_prefs";
    private static final String KEY_CONTROLLER_MODE = "controller_mode_enabled";
    private static final String KEY_PORT = "controller_port";

    private final PairingManager pairingManager;
    private final LocalConnection localConnection;
    private final ControllerDiscovery discovery;
    private final SharedPreferences prefs;

    private static ConnectionManager instance;

    public static synchronized ConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    private ConnectionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.pairingManager = new PairingManager();
        this.localConnection = new LocalConnection();
        this.discovery = new ControllerDiscovery();

        boolean savedMode = prefs.getBoolean(KEY_CONTROLLER_MODE, false);
        pairingManager.setControllerModeEnabled(savedMode);
    }

    public PairingManager getPairingManager() {
        return pairingManager;
    }

    public LocalConnection getLocalConnection() {
        return localConnection;
    }

    public ControllerDiscovery getDiscovery() {
        return discovery;
    }

    /**
     * Convenience entry point used by the game screen: turns Controller Mode on
     * (it is on by default so the Controller works out of the box), starts the
     * TCP server and the Wi-Fi discovery beacon.
     */
    public void autoStart() {
        if (!pairingManager.isControllerModeEnabled()) {
            setControllerModeEnabled(true);
        } else {
            localConnection.start(getPort());
        }
        startDiscovery();
    }

    /**
     * Starts (or restarts if it died) the UDP beacon advertising this game on
     * the Wi-Fi. Called every second from the game screen watchdog, so the
     * beacon recovers automatically even after an error or a port clash.
     */
    public void startDiscovery() {
        if (!pairingManager.isControllerModeEnabled()) return;
        if (discovery.isRunning()) return;
        discovery.start(getPort(), pairingManager.getPairingPin(), "Ludo King By Diwas");
    }

    /** True when the TCP server is actually listening. */
    public boolean isServerRunning() {
        return localConnection.isServerRunning();
    }

    /**
     * Recovers the TCP server if its accept-loop died (e.g. the port was
     * occupied right after a reinstall). Tries the configured port first,
     * then falls back to 8081/8082/8083 so the Controller Mode always comes
     * back up.
    */
    public synchronized void ensureServerRunning() {
        if (!pairingManager.isControllerModeEnabled()) return;
        if (localConnection.isServerRunning()) return;
        final Thread recovery = new Thread(() -> {
            localConnection.stop(); // release a half-dead state
            int[] candidates = {getPort(), LocalConnection.DEFAULT_PORT, 8081, 8082, 8083};
            for (int port : candidates) {
                localConnection.start(port);
                try {
                    Thread.sleep(150); // give the accept-loop thread a moment to bind
                } catch (InterruptedException ignored) {
                    return;
                }
                if (localConnection.isServerRunning()) {
                    if (port != getPort()) setPort(port);
                    startDiscovery(); // re-advertise the (possibly new) port
                    return;
                }
                localConnection.stop();
            }
        }, "ludo-server-recovery");
        recovery.setDaemon(true);
        recovery.start();
    }

    public void stopDiscovery() {
        discovery.stop();
    }

    public boolean isControllerModeEnabled() {
        return pairingManager.isControllerModeEnabled();
    }

    public void setControllerModeEnabled(boolean enabled) {
        pairingManager.setControllerModeEnabled(enabled);
        prefs.edit().putBoolean(KEY_CONTROLLER_MODE, enabled).apply();
        if (enabled) {
            localConnection.start(getPort());
            startDiscovery();
        } else {
            localConnection.stop();
            discovery.stop();
        }
    }

    public int getPort() {
        return prefs.getInt(KEY_PORT, LocalConnection.DEFAULT_PORT);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public void startIfEnabled() {
        if (isControllerModeEnabled()) {
            localConnection.start(getPort());
            startDiscovery();
        }
    }
}
