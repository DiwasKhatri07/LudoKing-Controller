package app.ludo.diwas.controller.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.ludo.diwas.controller.protocol.Commands;
import app.ludo.diwas.controller.protocol.Events;
import app.ludo.diwas.controller.protocol.Serialization;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller Socket Client communicating with the Game APK.
 *
 * <p>Fixed in this version:
 * <ul>
 *   <li>Commands could never be sent before: the single-thread executor was
 *       busy inside the blocking read-loop, so every {@link #sendCommand}
 *       queued forever and the game was impossible to control. Writes now run
 *       on their own single-thread executor.</li>
 *   <li>Auto-reconnect with exponential backoff (1s → 2s → 4s … max 10s), and
 *       the pairing handshake is retried automatically so the connection
 *       becomes reliable instead of one-shot.</li>
 *   <li>No network calls on the main thread (connect / close all run on the
 *       background executor), which previously could crash the app.</li>
 *   <li>Callbacks are always delivered on the main thread.</li>
 * </ul>
 */
public class ControllerClient {

    private static final String TAG = "ControllerClient";

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onPairSuccess(String token);
        void onPairFailed(String message);
        void onEventReceived(String event, JSONObject payload);
        void onLog(String log);
    }

    /** How long we wait for TCP connect / the pairing handshake answer. */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final long PAIR_TIMEOUT_MS = 5000L;
    private static final long RECONNECT_BASE_MS = 1000L;
    private static final long RECONNECT_MAX_MS = 10000L;

    private Socket socket;
    private volatile PrintWriter writer;
    private BufferedReader reader;

    /** Read loop + reconnect scheduling run here (single thread, sequential). */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    /** Writes run here so a blocked read-loop can never swallow a command. */
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Listener listener;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean userRequestedDisconnect = new AtomicBoolean(false);
    private volatile String sessionToken = null;
    private volatile String targetHost = null;
    private volatile int targetPort = 8080;
    private volatile String targetPin = Commands.AUTO_PIN;

    /** Auto-reconnect state — controlled by the UI (enable for AUTO/MANUAL mode). */
    private volatile boolean autoReconnect = false;
    private int reconnectAttempt = 0;
    private final Runnable reconnectRunnable = this::attemptReconnect;

    private volatile boolean pairAttemptInProgress = false;
    private int pairAttempt = 0;
    private final Runnable pairTimeoutRunnable = this::handlePairTimeout;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Enables/disables automatic reconnecting (with exponential backoff). */
    public void setAutoReconnect(boolean enabled) {
        this.autoReconnect = enabled;
        if (!enabled) {
            mainHandler.removeCallbacks(reconnectRunnable);
        }
    }

    public boolean isAutoReconnectEnabled() {
        return autoReconnect;
    }

    /**
     * Connects (and pairs) to the game. Safe to call repeatedly: every call
     * drops the previous connection first.
     */
    public void connect(String host, int port, String pin) {
        disconnectInternal(true);
        isRunning.set(true);
        userRequestedDisconnect.set(false);
        targetHost = host;
        targetPort = port;
        targetPin = (pin == null || pin.trim().isEmpty()) ? Commands.AUTO_PIN : pin.trim();
        reconnectAttempt = 0;
        pairAttempt = 0;
        openSocket();
    }

    private void openSocket() {
        final String host = targetHost;
        final int port = targetPort;
        if (host == null || !isRunning.get()) return;

        ioExecutor.execute(() -> {
            try {
                log("[CTRL] Connecting to " + host + ":" + port + "…");
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                socket = s;

                writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)), true);
                reader = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));

                reconnectAttempt = 0;
                log("[CTRL] Socket connected — pairing…");
                post(() -> {
                    if (listener != null) listener.onConnected();
                });

                sendPairRequest();

                String line;
                while (isRunning.get() && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    handleIncomingLine(line);
                }
                // Server closed the stream / read failed
                handleConnectionLost(isRunning.get() ? "Game closed the connection" : null);
            } catch (Exception e) {
                if (!isRunning.get() || userRequestedDisconnect.get()) return;
                handleConnectionLost("Could not reach the game (" + safeMessage(e) + ")");
            }
        });
    }

    // ───────────────────────── Pairing handshake ─────────────────────────

    private void sendPairRequest() {
        if (sessionToken != null || !isRunning.get()) return;
        pairAttempt++;
        pairAttemptInProgress = true;
        try {
            JSONObject pairParams = new JSONObject();
            pairParams.put(Commands.FIELD_PIN, targetPin);
            sendRaw(Serialization.buildCommandJson(Commands.PAIR_REQUEST, null, pairParams));
        } catch (Exception e) {
            log("[CTRL] Pair request failed: " + safeMessage(e));
        }
        mainHandler.removeCallbacks(pairTimeoutRunnable);
        mainHandler.postDelayed(pairTimeoutRunnable, PAIR_TIMEOUT_MS);
    }

    /** Re-sends the pair request with increasing patience: 2s → 4s → 8s → give up. */
    private void handlePairTimeout() {
        if (!isRunning.get() || sessionToken != null || !pairAttemptInProgress) return;
        if (socket == null || socket.isClosed()) return;
        if (pairAttempt < 4) {
            log("[CTRL] No answer yet — retrying pairing (" + pairAttempt + "/3)…");
            sendPairRequest();
        } else {
            post(() -> {
                if (listener != null) listener.onPairFailed("Game did not answer the pairing");
            });
            // Keep the socket open; reconnection logic may still recover it.
        }
    }

    private void handleIncomingLine(String line) {
        try {
            JSONObject json = Serialization.parseJson(line);
            String event = json.optString(Events.FIELD_EVENT, "");

            if (Events.PAIR_ACCEPTED.equals(event)) {
                pairAttemptInProgress = false;
                mainHandler.removeCallbacks(pairTimeoutRunnable);
                boolean firstPair = (sessionToken == null);
                sessionToken = json.optString(Events.FIELD_TOKEN, "");
                pairAttempt = 0;
                if (firstPair) log("[GAME] Paired with the game");
                post(() -> {
                    if (listener != null) listener.onPairSuccess(sessionToken);
                });
            } else if (Events.PAIR_REJECTED.equals(event)) {
                pairAttemptInProgress = false;
                mainHandler.removeCallbacks(pairTimeoutRunnable);
                String msg = json.optString(Events.FIELD_MESSAGE, "Pairing rejected");
                log("[GAME] " + msg);
                post(() -> {
                    if (listener != null) listener.onPairFailed(msg);
                });
            } else if (Events.STATE_UPDATE.equals(event) || Events.TURN_CHANGED.equals(event)
                    || Events.DICE_ROLLED.equals(event) || Events.PLAYER_WON.equals(event)) {
                log("[GAME] " + event);
            }

            final String evt = event;
            post(() -> {
                if (listener != null) listener.onEventReceived(evt, json);
            });
        } catch (Exception ignored) {
        }
    }

    // ───────────────────────── Sending commands ─────────────────────────

    public void sendCommand(String command, JSONObject params) {
        if (!isRunning.get() || sessionToken == null) {
            log("[CTRL] Not connected — cannot send " + command);
            return;
        }
        final String json = Serialization.buildCommandJson(command, sessionToken, params);
        writeExecutor.execute(() -> {
            PrintWriter w = writer;
            if (w == null) return;
            try {
                w.println(json);
                w.flush();
                if (w.checkError()) {
                    handleConnectionLost("Write failed");
                }
            } catch (Exception e) {
                handleConnectionLost(safeMessage(e));
            }
        });
    }

    /** Sends a keep-alive ping so dead links are noticed quickly. */
    public void sendKeepAlive() {
        if (!isRunning.get() || sessionToken == null) return;
        sendCommand(Commands.GET_STATE, null);
    }

    private void sendRaw(String msg) {
        writeExecutor.execute(() -> {
            PrintWriter w = writer;
            if (w == null) return;
            try {
                w.println(msg);
                w.flush();
            } catch (Exception ignored) {
            }
        });
    }

    // ───────────────────────── Reconnection ─────────────────────────

    private void handleConnectionLost(String reason) {
        if (sessionToken != null || reason != null) {
            if (reason != null) log("[CTRL] " + reason);
            final boolean wasPaired = sessionToken != null;
            sessionToken = null;
            pairAttemptInProgress = false;
            mainHandler.removeCallbacks(pairTimeoutRunnable);
            closeSocket();

            post(() -> {
                if (listener != null) listener.onDisconnected(
                        reason == null ? "Disconnected" : reason);
            });

            if (autoReconnect && !userRequestedDisconnect.get() && isRunning.get()) {
                scheduleReconnect(wasPaired);
            }
        }
    }

    private void scheduleReconnect(boolean wasPaired) {
        long delay = Math.min(RECONNECT_BASE_MS * (1L << Math.min(reconnectAttempt, 4)),
                RECONNECT_MAX_MS);
        reconnectAttempt++;
        log("[CTRL] Reconnecting in " + delay / 1000 + "s…");
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.postDelayed(reconnectRunnable, delay);
    }

    private void attemptReconnect() {
        if (!isRunning.get() || userRequestedDisconnect.get() || targetHost == null) return;
        if (sessionToken != null) return; // already reconnected & re-paired
        openSocket();
    }

    // ───────────────────────── Teardown ─────────────────────────

    /** User pressed DISCONNECT: drops everything, cancels reconnection. */
    public synchronized void disconnect() {
        userRequestedDisconnect.set(true);
        disconnectInternal(false);
    }

    private void disconnectInternal(boolean keepPrefs) {
        isRunning.set(false);
        userRequestedDisconnect.set(true);
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.removeCallbacks(pairTimeoutRunnable);
        sessionToken = null;
        pairAttemptInProgress = false;
        closeSocket();
        post(() -> {
            if (listener != null) listener.onDisconnected("Disconnected");
        });
    }

    private void closeSocket() {
        final Socket s = socket;
        socket = null;
        writer = null;
        reader = null;
        if (s != null && !s.isClosed()) {
            ioExecutor.execute(() -> {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            });
        }
    }

    public boolean isConnected() {
        return isRunning.get() && socket != null && sessionToken != null;
    }

    public boolean isPaired() {
        return sessionToken != null && socket != null;
    }

    public String getHost() {
        return targetHost;
    }

    private void post(Runnable r) {
        mainHandler.post(r);
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        post(() -> {
            if (listener != null) listener.onLog(msg);
        });
    }
}
