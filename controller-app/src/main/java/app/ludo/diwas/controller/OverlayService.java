package app.ludo.diwas.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import app.ludo.diwas.controller.network.ControllerClient;
import app.ludo.diwas.controller.network.GameDiscovery;
import app.ludo.diwas.controller.protocol.Commands;
import app.ludo.diwas.controller.protocol.Events;

import org.json.JSONObject;

/**
 * Floating controller panel ("overlay display") for playing on a single phone:
 * the Ludo game stays full screen and this small panel floats on top of it.
 *
 * <p>Upgraded in this version:
 * <ul>
 *   <li>Connects automatically (localhost first, then Wi-Fi discovery) and
 *       <b>stays connected</b>: auto-reconnect with backoff + keep-alive pings.</li>
 *   <li>Explicit <b>FIXED DICE</b> — pick a number, the roll button shows
 *       "ROLL 4" and the game is guaranteed to show that 4.</li>
 *   <li>Live turn / dice / fixed badge in the header, status pill always
 *       visible (LIVE / SEARCHING / OFFLINE).</li>
 *   <li>Draggable from anywhere on the header, and can be minimized to a tiny
 *       pill so it never blocks the board.</li>
 * </ul>
 */
public class OverlayService extends Service {

    private static final String TAG = "LudoOverlay";
    private static final String CHANNEL_ID = "ludo_overlay_channel";
    private static final int NOTIFICATION_ID = 4242;
    private static final long KEEP_ALIVE_MS = 4000L;

    /** Lets the activity show the right button state. */
    public static volatile boolean isRunning = false;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private final ControllerClient client = new ControllerClient();
    private final GameDiscovery discovery = new GameDiscovery();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvStatus, tvInfo, tvFixedBadge;
    private LinearLayout collapsibleContent;
    private Button ovRoll, ovAutoMove, ovBanner;
    private Integer selectedDice = null;
    private int selectedPiece = 0;
    private boolean minimized = false;

    /** AUTO-MOVE: game moves the best piece by itself after every roll. */
    private boolean autoMoveEnabled = false;
    /** Whether the CONNECTED banner is visible on the game screen. */
    private boolean bannerVisible = true;

    private final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (client.isPaired()) {
                client.sendKeepAlive();
            }
            mainHandler.postDelayed(this, KEEP_ALIVE_MS);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        setupClient();
        addOverlayView();
        autoConnect();
        mainHandler.post(keepAliveRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        mainHandler.removeCallbacks(keepAliveRunnable);
        discovery.stop();
        client.disconnect();
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
        super.onDestroy();
    }

    // ───────────────────────── Overlay window ─────────────────────────

    private void addOverlayView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_controller, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 160;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Log.e(TAG, "Overlay not allowed: " + e.getMessage());
            Toast.makeText(this, "Allow \"Display over other apps\" to use the overlay",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        bindOverlayViews();
    }

    private void bindOverlayViews() {
        tvStatus = overlayView.findViewById(R.id.ovStatus);
        tvInfo = overlayView.findViewById(R.id.ovInfo);
        tvFixedBadge = overlayView.findViewById(R.id.ovFixedBadge);
        collapsibleContent = overlayView.findViewById(R.id.ovCollapsible);
        ovRoll = overlayView.findViewById(R.id.ovRoll);

        overlayView.findViewById(R.id.ovClose).setOnClickListener(v -> stopSelf());
        overlayView.findViewById(R.id.ovMinimize).setOnClickListener(v -> toggleMinimize());

        final Button[] diceButtons = new Button[]{
                overlayView.findViewById(R.id.ovDice1),
                overlayView.findViewById(R.id.ovDice2),
                overlayView.findViewById(R.id.ovDice3),
                overlayView.findViewById(R.id.ovDice4),
                overlayView.findViewById(R.id.ovDice5),
                overlayView.findViewById(R.id.ovDice6)
        };
        for (int i = 0; i < diceButtons.length; i++) {
            final int value = i + 1;
            diceButtons[i].setOnClickListener(v -> {
                selectedDice = (selectedDice != null && selectedDice == value) ? null : value;
                updateDiceUi(diceButtons);
            });
        }

        // FIXED DICE roll: the queued number WILL come on the game dice.
        ovRoll.setOnClickListener(v -> {
            JSONObject params = new JSONObject();
            try {
                if (selectedDice != null) {
                    params.put(Commands.FIELD_VALUE, selectedDice);
                    params.put(Commands.FIELD_FIXED, true);
                }
            } catch (Exception ignored) {
            }
            client.sendCommand(Commands.ROLL_DICE, params);
        });

        overlayView.findViewById(R.id.ovMove).setOnClickListener(v -> {
            JSONObject params = new JSONObject();
            try {
                params.put(Commands.FIELD_PIECE_INDEX, selectedPiece);
            } catch (Exception ignored) {
            }
            client.sendCommand(Commands.MOVE_PIECE, params);
        });

        final Button[] pieceButtons = new Button[]{
                overlayView.findViewById(R.id.ovPiece1),
                overlayView.findViewById(R.id.ovPiece2),
                overlayView.findViewById(R.id.ovPiece3),
                overlayView.findViewById(R.id.ovPiece4)
        };
        for (int i = 0; i < pieceButtons.length; i++) {
            final int pieceIndex = i;
            pieceButtons[i].setOnClickListener(v -> {
                selectedPiece = pieceIndex;
                updatePieceUi(pieceButtons);
            });
        }

        overlayView.findViewById(R.id.ovPause)
                .setOnClickListener(v -> client.sendCommand(Commands.PAUSE_GAME, null));
        overlayView.findViewById(R.id.ovResume)
                .setOnClickListener(v -> client.sendCommand(Commands.RESUME_GAME, null));
        overlayView.findViewById(R.id.ovSkip)
                .setOnClickListener(v -> client.sendCommand(Commands.SKIP_TURN, null));

        // ── AUTO-MOVE toggle ──
        ovAutoMove = overlayView.findViewById(R.id.ovAutoMove);
        ovAutoMove.setOnClickListener(v -> {
            autoMoveEnabled = !autoMoveEnabled;
            updateAutoMoveUi();
            try {
                JSONObject p = new JSONObject();
                p.put("enabled", autoMoveEnabled);
                client.sendCommand(Commands.SET_AUTO_MOVE, p);
            } catch (Exception ignored) {
            }
        });

        // ── Game banner hide/show toggle ──
        ovBanner = overlayView.findViewById(R.id.ovBanner);
        ovBanner.setOnClickListener(v -> {
            bannerVisible = !bannerVisible;
            updateBannerUi();
            try {
                JSONObject p = new JSONObject();
                p.put("visible", bannerVisible);
                client.sendCommand(Commands.SET_BANNER_VISIBLE, p);
            } catch (Exception ignored) {
            }
        });

        updateAutoMoveUi();
        updateBannerUi();

        makeDraggable(overlayView.findViewById(R.id.ovDragHandle));
        updateDiceUi(diceButtons);
        updatePieceUi(pieceButtons);
    }

    /** Collapse to a tiny pill (or expand again) so the board stays visible. */
    private void toggleMinimize() {
        minimized = !minimized;
        collapsibleContent.setVisibility(minimized ? View.GONE : View.VISIBLE);
        TextView title = overlayView.findViewById(R.id.ovTitle);
        title.setText(minimized ? "LUDO  >" : "LUDO CONTROLLER");
        TextView minBtn = overlayView.findViewById(R.id.ovMinimize);
        minBtn.setText(minimized ? ">" : "v");
    }

    /** Lets the user drag the panel anywhere by its header. */
    private void makeDraggable(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchX;
                        float dy = event.getRawY() - touchY;
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true;
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        try {
                            windowManager.updateViewLayout(overlayView, params);
                        } catch (Exception ignored) {
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        // A tap (no drag) on the collapsed pill re-expands it.
                        if (!moved && minimized) toggleMinimize();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void updateDiceUi(Button[] diceButtons) {
        for (int i = 0; i < diceButtons.length; i++) {
            boolean selected = selectedDice != null && selectedDice == (i + 1);
            diceButtons[i].setBackgroundColor(Color.parseColor(selected ? "#10B981" : "#334155"));
            diceButtons[i].setTextColor(Color.parseColor(selected ? "#FFFFFF" : "#CBD5E1"));
        }
        if (selectedDice == null) {
            ovRoll.setText("ROLL");
            ovRoll.setBackgroundColor(Color.parseColor("#10B981"));
            tvFixedBadge.setVisibility(View.GONE);
        } else {
            ovRoll.setText("ROLL " + selectedDice + " (LOCKED)");
            ovRoll.setBackgroundColor(Color.parseColor("#059669"));
            tvFixedBadge.setVisibility(View.VISIBLE);
            tvFixedBadge.setText("FIXED: " + selectedDice);
            tvFixedBadge.setBackgroundColor(Color.parseColor("#10B981"));
        }
    }

    private void updateAutoMoveUi() {
        if (ovAutoMove == null) return;
        ovAutoMove.setText(autoMoveEnabled ? "AUTO-MOVE ON" : "AUTO-MOVE OFF");
        ovAutoMove.setBackgroundColor(Color.parseColor(autoMoveEnabled ? "#10B981" : "#334155"));
        ovAutoMove.setTextColor(Color.parseColor(autoMoveEnabled ? "#FFFFFF" : "#CBD5E1"));
    }

    private void updateBannerUi() {
        if (ovBanner == null) return;
        ovBanner.setText(bannerVisible ? "BANNER ON" : "BANNER HIDDEN");
        ovBanner.setBackgroundColor(Color.parseColor(bannerVisible ? "#334155" : "#7C3AED"));
        ovBanner.setTextColor(Color.parseColor("#FFFFFF"));
    }

    private void updatePieceUi(Button[] pieceButtons) {
        for (int i = 0; i < pieceButtons.length; i++) {
            boolean selected = selectedPiece == i;
            pieceButtons[i].setBackgroundColor(Color.parseColor(selected ? "#2563EB" : "#334155"));
            pieceButtons[i].setTextColor(Color.parseColor(selected ? "#FFFFFF" : "#CBD5E1"));
        }
    }

    // ───────────────────────── Auto connection ─────────────────────────

    private void autoConnect() {
        setStatus("CONNECTING", "#F59E0B");
        // Same phone → the game server is on localhost. If that fails, the
        // client auto-reconnects and we also start Wi-Fi discovery.
        client.setAutoReconnect(true);
        client.connect("127.0.0.1", 8080, Commands.AUTO_PIN);
    }

    private void startWifiDiscovery() {
        if (discovery.isRunning()) return;
        setStatus("SEARCHING", "#F59E0B");
        discovery.start(new GameDiscovery.Listener() {
            @Override
            public void onGameFound(String host, int port, String pin, boolean autoPair) {
                mainHandler.post(() -> {
                    setStatus("CONNECTING", "#F59E0B");
                    client.setAutoReconnect(true);
                    client.connect(host, port, autoPair ? Commands.AUTO_PIN : pin);
                });
            }

            @Override
            public void onLog(String message) {
                Log.d(TAG, message);
            }
        });
    }

    private void setupClient() {
        client.setListener(new ControllerClient.Listener() {
            @Override
            public void onConnected() {
                setStatus("PAIRING", "#F59E0B");
            }

            @Override
            public void onDisconnected(String reason) {
                setStatus("OFFLINE", "#EF4444");
                tvInfo.setText("Reconnecting... open the game first");
                // Same phone → hunt the Wi-Fi as well (game may run elsewhere).
                startWifiDiscovery();
            }

            @Override
            public void onPairSuccess(String token) {
                setStatus("LIVE", "#10B981");
                discovery.stop();
                // Apply current toggle states on the game right away.
                try {
                    JSONObject am = new JSONObject();
                    am.put("enabled", autoMoveEnabled);
                    client.sendCommand(Commands.SET_AUTO_MOVE, am);
                    JSONObject bv = new JSONObject();
                    bv.put("visible", bannerVisible);
                    client.sendCommand(Commands.SET_BANNER_VISIBLE, bv);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onPairFailed(String message) {
                setStatus("NO GAME", "#EF4444");
                // Connector mode may be off, or the game is on another phone.
                if (!discovery.isRunning()) {
                    startWifiDiscovery();
                }
            }

            @Override
            public void onEventReceived(String event, JSONObject payload) {
                updateInfo(event, payload);
            }

            @Override
            public void onLog(String log) {
                Log.d(TAG, log);
            }
        });
    }

    private void updateInfo(String event, JSONObject payload) {
        if (tvInfo == null) return;
        switch (event) {
            case Events.TURN_CHANGED:
                tvInfo.setText("Turn: " + payload.optString(Events.FIELD_TURN, "?").toUpperCase());
                break;
            case Events.DICE_ROLLED:
                tvInfo.setText("Turn: " + payload.optString(Events.FIELD_TURN, "?").toUpperCase()
                        + " • Dice: " + payload.optInt(Events.FIELD_DICE, 0));
                break;
            case Events.GAME_PAUSED:
                tvInfo.setText("Game paused");
                break;
            case Events.GAME_RESUMED:
                tvInfo.setText("Game resumed");
                break;
            case Events.PLAYER_WON: {
                String winner = payload.optString(Events.FIELD_WINNER, "");
                tvInfo.setText("🏆 " + (winner.isEmpty() ? "Game over!" : winner + " won!"));
                break;
            }
            case Events.STATE_UPDATE: {
                JSONObject state = payload.optJSONObject(Events.FIELD_STATE);
                if (state != null) {
                    String text = "Turn: " + state.optString("turn", "?").toUpperCase()
                            + " • Dice: " + state.optInt("dice", 0);
                    if (state.optBoolean("fixedDice", false)) {
                        text += " • FIXED " + state.optInt("fixedValue", 0);
                    }
                    tvInfo.setText(text);

                    // Keep toggles in sync with the game's actual state.
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
                            updateBannerUi();
                        }
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private void setStatus(String text, String color) {
        mainHandler.post(() -> {
            if (tvStatus != null) {
                tvStatus.setText(text);
                tvStatus.setBackgroundColor(Color.parseColor(color));
            }
        });
    }

    // ───────────────────────── Notification ─────────────────────────

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "Ludo Overlay Controller", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Keeps the floating controller running over the game");
                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Ludo Controller overlay is on")
                .setContentText("Floating controls are active over your game")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
