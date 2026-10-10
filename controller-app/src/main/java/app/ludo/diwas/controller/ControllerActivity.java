package app.ludo.diwas.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import app.ludo.diwas.controller.network.ControllerClient;
import app.ludo.diwas.controller.network.GameDiscovery;
import app.ludo.diwas.controller.protocol.Commands;
import app.ludo.diwas.controller.protocol.Events;

import org.json.JSONObject;

public class ControllerActivity extends AppCompatActivity {

    private static final String PREFS = "ludo_ctrl_prefs";
    private static final String KEY_LAST_HOST = "last_host";
    private static final String KEY_LAST_PORT = "last_port";
    private static final int REQ_OVERLAY_PERMISSION = 1001;

    private static final long KEEP_ALIVE_MS = 4000L;
    private static final int MAX_LOG_LINES = 200;

    private TextView tvStatusBadge, tvCurrentPlayer, tvDiceValue, tvFixedBadge, tvConsole;
    private EditText etGameIp, etPort, etPin;
    private Button btnAutoConnect, btnConnect, btnDisconnect, btnOverlay, btnRollDice, btnMoveSelectedPiece;
    private Button btnDice1, btnDice2, btnDice3, btnDice4, btnDice5, btnDice6;
    private Button btnPiece1, btnPiece2, btnPiece3, btnPiece4;
    private Button btnPause, btnResume, btnSkip, btnReset;
    private Button btnAutoMove, btnBannerToggle;

    private final ControllerClient client = new ControllerClient();
    private final GameDiscovery discovery = new GameDiscovery();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Integer selectedFixedDice = null;
    private int selectedPieceIndex = 0;
    private boolean selectedDiceIsFixed = false;

    /** AUTO-MOVE: game moves the best piece by itself after every roll. */
    private boolean autoMoveEnabled = false;
    /** Whether the CONNECTED banner is visible on the game screen. */
    private boolean bannerVisible = true;
    /** Guards against the game's unlock confirmation racing a local lock. */
    private boolean pendingLocalLock = false;

    /** True while we are connecting automatically (localhost → Wi-Fi discovery). */
    private boolean autoMode = false;
    private boolean triedLocalhost = false;
    /** PIN from the discovery beacon, used when auto pairing gets rejected. */
    private String beaconPin = null;

    private final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (client.isPaired()) {
                client.sendKeepAlive();
            }
            mainHandler.postDelayed(this, KEEP_ALIVE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        initViews();
        setupListeners();
        setupClient();
        autoConnect();
        mainHandler.post(keepAliveRunnable);
    }

    private void initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvCurrentPlayer = findViewById(R.id.tvCurrentPlayer);
        tvDiceValue = findViewById(R.id.tvDiceValue);
        tvFixedBadge = findViewById(R.id.tvFixedBadge);
        tvConsole = findViewById(R.id.tvConsole);
        tvConsole.setMovementMethod(new ScrollingMovementMethod());

        etGameIp = findViewById(R.id.etGameIp);
        etPort = findViewById(R.id.etPort);
        etPin = findViewById(R.id.etPin);

        btnAutoConnect = findViewById(R.id.btnAutoConnect);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnOverlay = findViewById(R.id.btnOverlay);
        btnRollDice = findViewById(R.id.btnRollDice);
        btnMoveSelectedPiece = findViewById(R.id.btnMoveSelectedPiece);

        btnDice1 = findViewById(R.id.btnDice1);
        btnDice2 = findViewById(R.id.btnDice2);
        btnDice3 = findViewById(R.id.btnDice3);
        btnDice4 = findViewById(R.id.btnDice4);
        btnDice5 = findViewById(R.id.btnDice5);
        btnDice6 = findViewById(R.id.btnDice6);

        btnPiece1 = findViewById(R.id.btnPiece1);
        btnPiece2 = findViewById(R.id.btnPiece2);
        btnPiece3 = findViewById(R.id.btnPiece3);
        btnPiece4 = findViewById(R.id.btnPiece4);

        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnSkip = findViewById(R.id.btnSkip);
        btnReset = findViewById(R.id.btnReset);

        btnAutoMove = findViewById(R.id.btnAutoMove);
        btnBannerToggle = findViewById(R.id.btnBannerToggle);

        updateAutoMoveUi();
        updateBannerToggleUi();

        appendLog("[CTRL] Controller ready. AUTO CONNECT finds the game by itself.");
    }

    private void setupListeners() {
        // Fully automatic: last game → localhost (same phone) → Wi-Fi discovery,
        // with automatic reconnection if the link drops.
        btnAutoConnect.setOnClickListener(v -> {
            autoMode = true;
            discovery.stop();
            startAutoConnect();
        });

        btnConnect.setOnClickListener(v -> {
            String ip = etGameIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            String pin = etPin.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(this, "Enter the game IP (or use AUTO CONNECT)", Toast.LENGTH_SHORT).show();
                return;
            }
            int port = 8080;
            try {
                port = Integer.parseInt(portStr);
            } catch (Exception ignored) {
            }

            autoMode = false;
            triedLocalhost = false;
            discovery.stop();
            client.setAutoReconnect(true);
            appendLog("[CTRL] Connecting to " + ip + ":" + port
                    + (pin.isEmpty() ? " (auto pairing)" : "…"));
            client.connect(ip, port, pin);
        });

        btnDisconnect.setOnClickListener(v -> {
            autoMode = false;
            discovery.stop();
            client.setAutoReconnect(false);
            client.disconnect();
        });

        btnOverlay.setOnClickListener(v -> toggleOverlay());

        // ── FIXED DICE selector: tap a number to LOCK it — every roll shows that
        // number until you change or clear it. Tapping a number also rolls right
        // away, just like tapping the dice in a normal ludo game.
        Button[] diceBtns = new Button[]{btnDice1, btnDice2, btnDice3, btnDice4, btnDice5, btnDice6};
        for (int i = 0; i < diceBtns.length; i++) {
            final int diceVal = i + 1;
            diceBtns[i].setOnClickListener(v -> {
                if (selectedFixedDice != null && selectedFixedDice == diceVal) {
                    // Same number tapped again → unlock (back to random rolls).
                    selectedFixedDice = null;
                    pendingLocalLock = false;
                    setFixedDiceOnGame(null);
                    updateDiceButtonsUi();
                    return;
                }
                selectedFixedDice = diceVal;
                pendingLocalLock = true;
                updateDiceButtonsUi();
                setFixedDiceOnGame(diceVal);
                rollNow(); // tap = roll, no extra button press needed
            });
        }

        btnRollDice.setOnClickListener(v -> rollNow());

        // Piece selection + move
        Button[] pieceBtns = new Button[]{btnPiece1, btnPiece2, btnPiece3, btnPiece4};
        for (int i = 0; i < pieceBtns.length; i++) {
            final int pIdx = i;
            pieceBtns[i].setOnClickListener(v -> {
                selectedPieceIndex = pIdx;
                updatePieceButtonsUi();
            });
        }

        btnMoveSelectedPiece.setOnClickListener(v -> {
            if (autoMoveEnabled) {
                Toast.makeText(this, "AUTO-MOVE is ON — pieces move by themselves", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject params = new JSONObject();
                params.put(Commands.FIELD_PIECE_INDEX, selectedPieceIndex);
                client.sendCommand(Commands.MOVE_PIECE, params);
            } catch (Exception ignored) {
            }
        });

        btnPause.setOnClickListener(v -> client.sendCommand(Commands.PAUSE_GAME, null));
        btnResume.setOnClickListener(v -> client.sendCommand(Commands.RESUME_GAME, null));
        btnSkip.setOnClickListener(v -> client.sendCommand(Commands.SKIP_TURN, null));
        btnReset.setOnClickListener(v -> client.sendCommand(Commands.RESET_BOARD, null));

        // ── AUTO-MOVE toggle: no piece selection needed anymore ──
        btnAutoMove.setOnClickListener(v -> {
            autoMoveEnabled = !autoMoveEnabled;
            updateAutoMoveUi();
            try {
                JSONObject params = new JSONObject();
                params.put("enabled", autoMoveEnabled);
                client.sendCommand(Commands.SET_AUTO_MOVE, params);
            } catch (Exception ignored) {
            }
            appendLog("[CTRL] AUTO-MOVE " + (autoMoveEnabled ? "ON — pieces move by themselves"
                    : "OFF — select pieces manually"));
        });

        // ── Banner toggle: show/hide the CONNECTED banner on the game screen ──
        btnBannerToggle.setOnClickListener(v -> {
            bannerVisible = !bannerVisible;
            updateBannerToggleUi();
            try {
                JSONObject params = new JSONObject();
                params.put("visible", bannerVisible);
                client.sendCommand(Commands.SET_BANNER_VISIBLE, params);
            } catch (Exception ignored) {
            }
            appendLog("[CTRL] Game banner " + (bannerVisible ? "SHOWN" : "HIDDEN"));
        });
    }

    /** Locks (value 1-6) or clears (null) the fixed dice on the game — sticky. */
    private void setFixedDiceOnGame(Integer value) {
        try {
            JSONObject params = new JSONObject();
            params.put(Commands.FIELD_VALUE, value == null ? 0 : value);
            client.sendCommand(Commands.SET_FIXED_DICE, params);
            pendingLocalLock = (value != null);
        } catch (Exception ignored) {
        }
    }

    /** Rolls the dice on the game: fixed number if locked, otherwise random. */
    private void rollNow() {
        try {
            JSONObject params = new JSONObject();
            if (selectedFixedDice != null) {
                params.put(Commands.FIELD_VALUE, selectedFixedDice);
            }
            client.sendCommand(Commands.ROLL_DICE, params);
        } catch (Exception ignored) {
        }
    }

    private void updateAutoMoveUi() {
        if (btnAutoMove == null) return;
        btnAutoMove.setText(autoMoveEnabled ? "AUTO-MOVE: ON"
                : "AUTO-MOVE: OFF");
        btnAutoMove.setBackgroundColor(Color.parseColor(autoMoveEnabled ? "#10B981" : "#334155"));
        btnAutoMove.setTextColor(Color.parseColor(autoMoveEnabled ? "#FFFFFF" : "#CBD5E1"));
    }

    private void updateBannerToggleUi() {
        if (btnBannerToggle == null) return;
        btnBannerToggle.setText(bannerVisible ? "GAME BANNER: SHOWN (TAP TO HIDE)"
                : "GAME BANNER: HIDDEN (TAP TO SHOW)");
        btnBannerToggle.setBackgroundColor(Color.parseColor(bannerVisible ? "#334155" : "#7C3AED"));
        btnBannerToggle.setTextColor(Color.parseColor("#FFFFFF"));
    }

    // ───────────────────────── Auto connection ─────────────────────────

    /** Tries the last known game first, then localhost (game on the same phone). */
    private void autoConnect() {
        autoMode = true;
        triedLocalhost = false;
        client.setAutoReconnect(true);
        startAutoConnect();
    }

    private void startAutoConnect() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lastHost = prefs.getString(KEY_LAST_HOST, null);
        int lastPort = prefs.getInt(KEY_LAST_PORT, 8080);

        setStatus("CONNECTING…", "#F59E0B");
        if (lastHost != null && !lastHost.isEmpty()) {
            appendLog("[CTRL] Trying last game " + lastHost + ":" + lastPort + "…");
            client.connect(lastHost, lastPort, Commands.AUTO_PIN);
        } else {
            tryLocalhost();
        }
    }

    private void tryLocalhost() {
        if (triedLocalhost) {
            startWifiDiscovery();
            return;
        }
        triedLocalhost = true;
        appendLog("[CTRL] Checking for the game on this phone (127.0.0.1)…");
        client.connect("127.0.0.1", 8080, Commands.AUTO_PIN);
    }

    /** Scans the Wi-Fi network for a running game and connects automatically. */
    private void startWifiDiscovery() {
        if (discovery.isRunning()) return;
        setStatus("SEARCHING…", "#F59E0B");
        appendLog("[CTRL] Searching the same Wi-Fi network (beacon + network scan)…");
        discovery.start(new GameDiscovery.Listener() {
            @Override
            public void onGameFound(String host, int port, String pin, boolean autoPair) {
                runOnUiThread(() -> {
                    beaconPin = (pin == null || pin.trim().isEmpty()) ? beaconPin : pin;
                    etGameIp.setText(host);
                    etPort.setText(String.valueOf(port));
                    appendLog("[CTRL] Game found at " + host + ":" + port + " — connecting…");
                    client.setAutoReconnect(true);
                    client.connect(host, port, autoPair ? Commands.AUTO_PIN
                            : (pin == null ? "" : pin));
                });
            }

            @Override
            public void onLog(String message) {
                runOnUiThread(() -> appendLog(message));
            }
        });
    }

    private void saveLastGame(String host, int port) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LAST_HOST, host)
                .putInt(KEY_LAST_PORT, port)
                .apply();
    }

    // ───────────────────────── Overlay (one phone) ─────────────────────────

    private void toggleOverlay() {
        if (OverlayService.isRunning) {
            stopService(new Intent(this, OverlayService.class));
            btnOverlay.setText("FLOATING OVERLAY (ONE PHONE)");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" for the overlay", Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_OVERLAY_PERMISSION);
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        btnOverlay.setText("🛑  STOP FLOATING OVERLAY");
        Toast.makeText(this, "Overlay on — open Ludo King By Diwas and play", Toast.LENGTH_LONG).show();
        appendLog("[CTRL] Floating overlay started (works on the same phone).");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ───────────────────────── UI helpers ─────────────────────────

    private void updateDiceButtonsUi() {
        Button[] diceBtns = new Button[]{btnDice1, btnDice2, btnDice3, btnDice4, btnDice5, btnDice6};
        for (int i = 0; i < diceBtns.length; i++) {
            boolean selected = selectedFixedDice != null && selectedFixedDice == (i + 1);
            diceBtns[i].setBackgroundColor(Color.parseColor(selected ? "#10B981" : "#334155"));
            diceBtns[i].setTextColor(Color.parseColor(selected ? "#FFFFFF" : "#CBD5E1"));
        }
        if (selectedFixedDice != null) {
            btnRollDice.setText("ROLL  " + selectedFixedDice + "  (LOCKED)");
            btnRollDice.setBackgroundColor(Color.parseColor("#059669"));
            tvFixedBadge.setVisibility(android.view.View.VISIBLE);
            tvFixedBadge.setText("FIXED: " + selectedFixedDice);
            tvFixedBadge.setBackgroundColor(Color.parseColor("#10B981"));
        } else {
            btnRollDice.setText("ROLL DICE");
            btnRollDice.setBackgroundColor(Color.parseColor("#10B981"));
            tvFixedBadge.setVisibility(android.view.View.GONE);
        }
    }

    private static final String[] PIECE_COLORS = {"RED", "GREEN", "YELLOW", "BLUE"};
    private static final String[] PIECE_COLORS_ON = {"#DC2626", "#059669", "#D97706", "#2563EB"};
    private static final String[] PIECE_COLORS_OFF = {"#7F1D1D", "#14532D", "#78350F", "#1E3A8A"};

    private void updatePieceButtonsUi() {
        Button[] pieceBtns = new Button[]{btnPiece1, btnPiece2, btnPiece3, btnPiece4};
        for (int i = 0; i < pieceBtns.length; i++) {
            boolean selected = selectedPieceIndex == i;
            pieceBtns[i].setText(PIECE_COLORS[i]);
            pieceBtns[i].setBackgroundColor(Color.parseColor(
                    selected ? PIECE_COLORS_ON[i] : PIECE_COLORS_OFF[i]));
            pieceBtns[i].setTextColor(Color.parseColor(selected ? "#FFFFFF" : "#E5E7EB"));
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnRollDice.setEnabled(enabled);
        btnMoveSelectedPiece.setEnabled(enabled);
        btnPause.setEnabled(enabled);
        btnResume.setEnabled(enabled);
        btnSkip.setEnabled(enabled);
        btnReset.setEnabled(enabled);
        btnRollDice.setAlpha(enabled ? 1f : 0.45f);
        btnMoveSelectedPiece.setAlpha(enabled ? 1f : 0.45f);
    }

    private void setStatus(String text, String color) {
        tvStatusBadge.setText(text);
        tvStatusBadge.setBackgroundColor(Color.parseColor(color));
    }

    private void setupClient() {
        client.setListener(new ControllerClient.Listener() {
            @Override
            public void onConnected() {
                setStatus("PAIRING…", "#F59E0B");
            }

            @Override
            public void onDisconnected(String reason) {
                if (client.isAutoReconnectEnabled() && autoModeOrManual()) {
                    setStatus("RECONNECTING…", "#F59E0B");
                    appendLog("[CTRL] " + reason);
                } else {
                    setStatus("DISCONNECTED", "#EF4444");
                    setControlsEnabled(false);
                    appendLog("[CTRL] " + reason);
                }
            }

            @Override
            public void onPairSuccess(String token) {
                autoMode = false;
                setStatus("CONNECTED", "#10B981");
                setControlsEnabled(true);
                Toast.makeText(ControllerActivity.this, "Paired with the game!", Toast.LENGTH_SHORT).show();
                appendLog("[CTRL] Connected to the game — controls are live.");

                // Apply the current toggle states on the game right away
                // (they persist per controller session).
                try {
                    JSONObject am = new JSONObject();
                    am.put("enabled", autoMoveEnabled);
                    client.sendCommand(Commands.SET_AUTO_MOVE, am);
                    JSONObject bv = new JSONObject();
                    bv.put("visible", bannerVisible);
                    client.sendCommand(Commands.SET_BANNER_VISIBLE, bv);
                } catch (Exception ignored) {
                }

                String host = client.getHost() != null ? client.getHost()
                        : etGameIp.getText().toString().trim();
                int port = 8080;
                try {
                    port = Integer.parseInt(etPort.getText().toString().trim());
                } catch (Exception ignored) {
                }
                if (!host.isEmpty()) {
                    etGameIp.setText(host);
                    saveLastGame(host, port);
                }
            }

            @Override
            public void onPairFailed(String message) {
                appendLog("[CTRL] " + message);
                if (autoMode) {
                    // Keep hunting: localhost → Wi-Fi discovery.
                    tryLocalhost();
                } else if (beaconPin != null) {
                    // Auto-pair rejected (mode may require the real PIN) — retry once.
                    appendLog("[CTRL] Retrying with the game's PIN…");
                    String host = client.getHost();
                    int port = 8080;
                    try {
                        port = Integer.parseInt(etPort.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    if (host != null) client.connect(host, port, beaconPin);
                    beaconPin = null;
                } else {
                    setStatus("NO GAME", "#F59E0B");
                }
            }

            @Override
            public void onEventReceived(String event, JSONObject payload) {
                handleIncomingEvent(event, payload);
            }

            @Override
            public void onLog(String log) {
                appendLog(log);
            }
        });
    }

    private boolean autoModeOrManual() {
        return autoMode || client.isAutoReconnectEnabled();
    }

    private void handleIncomingEvent(String event, JSONObject payload) {
        switch (event) {
            case Events.TURN_CHANGED: {
                String player = payload.optString(Events.FIELD_TURN, "RED").toUpperCase();
                tvCurrentPlayer.setText(player);
                setPlayerTextColor(player);
                break;
            }
            case Events.DICE_ROLLED: {
                int dice = payload.optInt(Events.FIELD_DICE, 0);
                if (dice > 0) tvDiceValue.setText(String.valueOf(dice));
                break;
            }
            case Events.STATE_UPDATE: {
                JSONObject state = payload.optJSONObject(Events.FIELD_STATE);
                if (state != null) {
                    String turn = state.optString("turn", "RED").toUpperCase();
                    tvCurrentPlayer.setText(turn);
                    setPlayerTextColor(turn);
                    int d = state.optInt("dice", 0);
                    if (d > 0) tvDiceValue.setText(String.valueOf(d));

                    // Sticky fixed dice: sync the lock state from the game.
                    boolean fixed = state.optBoolean("fixedDice", false);
                    int fixedValue = state.optInt("fixedValue", 0);
                    Integer gameFixed = (fixed && fixedValue >= 1 && fixedValue <= 6)
                            ? fixedValue : null;
                    if (gameFixed != null && !gameFixed.equals(selectedFixedDice)) {
                        selectedFixedDice = gameFixed;
                        updateDiceButtonsUi();
                    } else if (gameFixed == null && state.has("fixedDice")
                            && !state.optBoolean("fixedDice", false)
                            && !pendingLocalLock) {
                        // Game confirmed the unlock — clear local selection.
                        selectedFixedDice = null;
                        updateDiceButtonsUi();
                    }

                    boolean paused = state.optBoolean("paused", false);
                    if (paused) setStatus("PAUSED", "#F59E0B");
                    else if (client.isPaired()) setStatus("CONNECTED", "#10B981");

                    // Keep the toggles in sync with the game's actual state.
                    if (state.has("autoMove")) {
                        boolean am = state.optBoolean("autoMove", false);
                        if (am != autoMoveEnabled) {
                            autoMoveEnabled = am;
                            updateAutoMoveUi();
                        }
                    }
                    if (state.has("bannerVisible")) {
                        boolean bv = state.optBoolean("bannerVisible", true);
                        if (bv != bannerVisible) {
                            bannerVisible = bv;
                            updateBannerToggleUi();
                        }
                    }
                }
                break;
            }
            case Events.GAME_PAUSED:
                setStatus("PAUSED", "#F59E0B");
                break;
            case Events.GAME_RESUMED:
                if (client.isPaired()) setStatus("CONNECTED", "#10B981");
                break;
            case Events.PLAYER_WON: {
                String winner = payload.optString(Events.FIELD_WINNER, "");
                String name = winner.isEmpty() ? payload.optString(Events.FIELD_TURN, "") : winner;
                Toast.makeText(this, "🏆 " + (name.isEmpty() ? "A player" : name) + " Won!", Toast.LENGTH_LONG).show();
                appendLog("[GAME] 🏆 " + name + " won the game!");
                break;
            }
        }
    }

    private void setPlayerTextColor(String player) {
        switch (player) {
            case "RED":
                tvCurrentPlayer.setTextColor(Color.parseColor("#EF4444"));
                break;
            case "GREEN":
                tvCurrentPlayer.setTextColor(Color.parseColor("#10B981"));
                break;
            case "YELLOW":
                tvCurrentPlayer.setTextColor(Color.parseColor("#F59E0B"));
                break;
            case "BLUE":
                tvCurrentPlayer.setTextColor(Color.parseColor("#3B82F6"));
                break;
            default:
                tvCurrentPlayer.setTextColor(Color.WHITE);
                break;
        }
    }

    private void appendLog(String line) {
        tvConsole.append(line + "\n");
        int lines = tvConsole.getLineCount();
        if (lines > MAX_LOG_LINES) {
            String text = tvConsole.getText().toString();
            int cut = text.indexOf('\n', text.length() - MAX_LOG_LINES / 2);
            if (cut > 0) tvConsole.setText(text.substring(cut + 1));
        }
        // Scroll to bottom
        final int scrollAmount = tvConsole.getLayout() != null ?
                tvConsole.getLayout().getLineTop(tvConsole.getLineCount()) - tvConsole.getHeight() : 0;
        if (scrollAmount > 0) {
            tvConsole.scrollTo(0, scrollAmount);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(keepAliveRunnable);
        discovery.stop();
        client.disconnect();
    }
}
