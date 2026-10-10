package app.ludo.diwas.controller.network;

import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Finds the Ludo game automatically on the same Wi-Fi network.
 *
 * <p>Fixed in this version — the previous build bound UDP port 8888 on the
 * phone, which collided with the game's own beacon socket (the game steals
 * its own discovery replies when both apps run on one device). Now:
 * <ul>
 *   <li>The controller listens on an <b>ephemeral port</b> and sends unicast
 *       probes to the game's fixed UDP 8888 beacon port; the game answers
 *       directly back to the probe's source port.</li>
 *   <li>While probing, it also <b>listens passively</b> for the game's periodic
 *       broadcast beacons, so discovery works even when probes are filtered.</li>
 *   <li>New fallback: a quick <b>TCP scan</b> of the subnet on the game port —
 *       this finds the game even on Wi-Fi routers that block all broadcast /
 *       multicast traffic.</li>
 * </ul>
 */
public class GameDiscovery {

    private static final String TAG = "GameDiscovery";

    /** The game's beacon listens here. Probes are sent to this port. */
    public static final int DISCOVERY_PORT = 8888;
    /** TCP port of the game itself — used by the subnet scan fallback. */
    public static final int GAME_TCP_PORT = 8080;

    private static final String SERVICE_NAME = "ludo-diwas";
    private static final long PROBE_INTERVAL_MS = 700L;
    private static final long TCP_SCAN_START_DELAY_MS = 4000L;

    public interface Listener {
        void onGameFound(String host, int port, String pin, boolean autoPair);

        void onLog(String message);
    }

    private volatile boolean running = false;
    private Thread thread;
    private Thread scanThread;
    private DatagramSocket socket;
    private Listener listener;

    /** Starts searching for a game. The listener is called from a background thread. */
    public synchronized void start(Listener listener) {
        this.listener = listener;
        if (running) return;
        running = true;

        thread = new Thread(this::loop, "ludo-discovery-client");
        thread.setDaemon(true);
        thread.start();

        // Slow fallback: if UDP gives nothing, scan the subnet over TCP.
        scanThread = new Thread(this::tcpScanLoop, "ludo-tcp-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            socket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (scanThread != null) {
            scanThread.interrupt();
            scanThread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    // ───────────────────────── UDP probe + passive listen ─────────────────────────

    private void loop() {
        try {
            // EPHEMERAL port — fixes the clash with the game's own UDP 8888 beacon.
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.setSoTimeout(400);
            socket.bind(new InetSocketAddress(0));
        } catch (Exception e) {
            log("[CTRL] UDP discovery unavailable (" + e.getMessage() + ") — TCP scan will find the game");
            return;
        }

        log("[CTRL] Searching for the game on this Wi-Fi (" + getLocalIpAddress() + ")…");
        byte[] probe = buildProbe().getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[2048];
        long lastProbe = 0L;

        while (running && socket != null && !socket.isClosed()) {
            long now = System.currentTimeMillis();
            if (now - lastProbe >= PROBE_INTERVAL_MS) {
                lastProbe = now;
                sendProbe(probe);
            }

            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                if (handleBeaconText(new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8).trim(),
                        packet.getAddress().getHostAddress())) {
                    return; // found — handleBeaconText stops us
                }
            } catch (java.net.SocketTimeoutException timeout) {
                // keep probing
            } catch (Exception e) {
                if (running) {
                    Log.d(TAG, "Discovery error: " + e.getMessage());
                }
            }
        }
    }

    /** Broadcasts the probe to the global + subnet broadcast addresses. */
    private void sendProbe(byte[] probe) {
        try {
            socket.send(new DatagramPacket(probe, probe.length,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
        } catch (Exception ignored) {
        }
        String subnetBroadcast = getSubnetBroadcastAddress();
        if (subnetBroadcast != null) {
            try {
                socket.send(new DatagramPacket(probe, probe.length,
                        InetAddress.getByName(subnetBroadcast), DISCOVERY_PORT));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Parses a beacon/probe-reply. Returns true and stops the discovery when
     * the game was found. Both the unicast reply and the periodic broadcast
     * beacon carry the same JSON.
     */
    private boolean handleBeaconText(String text, String packetHost) {
        try {
            JSONObject json = new JSONObject(text);
            if (!SERVICE_NAME.equals(json.optString("service", ""))) return false;
            if (json.optBoolean("probe", false)) return false; // our own probe bounced back

            String host = json.optString("ip", packetHost);
            if (host == null || host.isEmpty() || "127.0.0.1".equals(host)) {
                host = packetHost;
            }
            int port = json.optInt("port", GAME_TCP_PORT);
            String pin = json.optString("pin", "");
            boolean autoPair = json.optBoolean("autoPair", true);

            log("[CTRL] Game found at " + host + ":" + port);
            notifyFound(host, port, pin, autoPair);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void notifyFound(String host, int port, String pin, boolean autoPair) {
        stop();
        if (listener != null) {
            listener.onGameFound(host, port, pin, autoPair);
        }
    }

    private String buildProbe() {
        try {
            JSONObject json = new JSONObject();
            json.put("service", SERVICE_NAME);
            json.put("probe", true);
            json.put("from", "Ludo Controller By Diwas");
            return json.toString();
        } catch (Exception e) {
            return "{\"service\":\"" + SERVICE_NAME + "\",\"probe\":true}";
        }
    }

    // ───────────────────────── TCP subnet scan fallback ─────────────────────────

    /**
     * Waits a few seconds for UDP discovery, then sweeps x.x.x.1-254 on the
     * game TCP port with short timeouts. Finds the game even on routers that
     * drop broadcast packets (client isolation APs still forward direct TCP).
     */
    private void tcpScanLoop() {
        try {
            Thread.sleep(TCP_SCAN_START_DELAY_MS);
        } catch (InterruptedException e) {
            return;
        }
        while (running) {
            String ownIp = getLocalIpAddress();
            if (ownIp != null && !ownIp.isEmpty() && !ownIp.startsWith("127.")) {
                String prefix = ownIp.substring(0, ownIp.lastIndexOf('.') + 1);
                log("[CTRL] Scanning network " + prefix + "x for the game…");
                for (int i = 1; i <= 254 && running; i++) {
                    String candidate = prefix + i;
                    if (candidate.equals(ownIp)) continue;
                    if (isGamePortOpen(candidate, GAME_TCP_PORT, 250)) {
                        log("[CTRL] Game server found at " + candidate + ":" + GAME_TCP_PORT);
                        notifyFound(candidate, GAME_TCP_PORT, "", true);
                        return;
                    }
                }
            }
            // Wait before the next sweep (and give UDP another chance).
            for (int waited = 0; waited < 5000 && running; waited += 500) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /** True when a TCP connection to host:port succeeds within timeoutMs. */
    private static boolean isGamePortOpen(String host, int port, int timeoutMs) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(InetAddress.getByName(host), port), timeoutMs);
            try {
                s.close();
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** e.g. 192.168.1.23 -> 192.168.1.255 (standard /24 home Wi-Fi). */
    private String getSubnetBroadcastAddress() {
        String ip = getLocalIpAddress();
        if (ip == null || ip.isEmpty()) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot <= 0) return null;
        return ip.substring(0, lastDot) + ".255";
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (listener != null && running) {
            listener.onLog(message);
        }
    }
}
