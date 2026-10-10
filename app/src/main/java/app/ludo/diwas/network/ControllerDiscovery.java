package app.ludo.diwas.network;

import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP discovery beacon for Controller Mode.
 *
 * The game broadcasts a small JSON beacon on UDP port {@link #DISCOVERY_PORT}
 * every couple of seconds, so the Controller app can auto-detect the game IP
 * on the same Wi-Fi network without the user typing anything.
 *
 * The game also answers unicast "probe" packets immediately (and repeats the
 * reply a few times) which makes auto-connect fast and works on Wi-Fi networks
 * that filter broadcast traffic.
 */
public class ControllerDiscovery {

    private static final String TAG = "ControllerDiscovery";

    /** UDP port used only for discovery (the game itself listens on TCP 8080). */
    public static final int DISCOVERY_PORT = 8888;

    /** Service identifier so we never react to unrelated UDP traffic. */
    public static final String SERVICE_NAME = "ludo-diwas";

    private static final long BROADCAST_INTERVAL_MS = 2000L;
    private static final int BEACON_REPEAT = 3;

    private volatile boolean running = false;
    private Thread thread;
    private DatagramSocket socket;

    private int gamePort = LocalConnection.DEFAULT_PORT;
    private String pin = "";
    private String deviceName = "Ludo King By Diwas";
    private boolean autoPair = true;

    /** Starts the beacon. Safe to call multiple times. */
    public synchronized void start(int gamePort, String pin, String deviceName) {
        this.gamePort = gamePort;
        this.pin = pin == null ? "" : pin;
        this.deviceName = deviceName == null ? "Ludo King By Diwas" : deviceName;
        if (running) return;
        running = true;

        thread = new Thread(this::loop, "ludo-discovery-beacon");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the beacon and releases the UDP port. */
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
        Log.d(TAG, "[GAME] Discovery beacon stopped");
    }

    public boolean isRunning() {
        return running;
    }

    /** Updates the advertised PIN without restarting the beacon. */
    public synchronized void setPin(String pin) {
        this.pin = pin == null ? "" : pin;
    }

    private void loop() {
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.setSoTimeout(500);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            Log.d(TAG, "[GAME] Discovery beacon listening on UDP " + DISCOVERY_PORT);
        } catch (Exception e) {
            Log.e(TAG, "[GAME] Could not start discovery beacon: " + e.getMessage());
            running = false;
            return;
        }

        long lastBroadcast = 0L;
        byte[] buffer = new byte[1024];

        while (running && !socket.isClosed()) {
            long now = System.currentTimeMillis();
            if (now - lastBroadcast >= BROADCAST_INTERVAL_MS) {
                lastBroadcast = now;
                broadcastBeacon();
            }
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            } catch (java.net.SocketTimeoutException timeout) {
                // normal, just loop again and broadcast when due
            } catch (Exception e) {
                if (running) {
                    Log.d(TAG, "[GAME] Discovery receive error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles an incoming discovery packet: unicast reply to probes, ignore beacons.
     *
     * The reply goes back to the probe's actual source port (unless the probe
     * explicitly asks for a {@code replyTo} port). The controller listens on an
     * ephemeral port, so answering to port 8888 would only reach the game
     * itself when both apps run on the same phone — that clash was one of the
     * reasons auto-connect felt broken.
     */
    private void handlePacket(DatagramPacket packet) {
        try {
            String text = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                    StandardCharsets.UTF_8).trim();
            JSONObject json = new JSONObject(text);

            boolean isProbe = json.optBoolean("probe", false);
            boolean isOurService = SERVICE_NAME.equals(json.optString("service", ""));
            if (!isProbe || !isOurService) return;

            int senderPort = packet.getPort();
            int replyPort = json.optInt("replyTo", senderPort);
            String sender = packet.getAddress().getHostAddress();
            Log.d(TAG, "[GAME] Discovery probe received from " + sender + ":" + senderPort);

            byte[] reply = buildBeacon().getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < BEACON_REPEAT && running; i++) {
                try {
                    socket.send(new DatagramPacket(reply, reply.length,
                            packet.getAddress(), replyPort));
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ignored) {
                }
            }
        } catch (Exception ignored) {
            // Not a Ludo discovery packet — ignore it.
        }
    }

    private void broadcastBeacon() {
        try {
            byte[] data = buildBeacon().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
            socket.send(packet);
        } catch (Exception e) {
            Log.d(TAG, "[GAME] Beacon broadcast failed: " + e.getMessage());
        }
    }

    private String buildBeacon() {
        try {
            JSONObject json = new JSONObject();
            json.put("service", SERVICE_NAME);
            json.put("game", deviceName);
            json.put("ip", NetworkUtils.getLocalIpAddress());
            json.put("port", gamePort);
            json.put("pin", pin);
            json.put("autoPair", autoPair);
            return json.toString();
        } catch (Exception e) {
            return "{\"service\":\"" + SERVICE_NAME + "\"}";
        }
    }
}
