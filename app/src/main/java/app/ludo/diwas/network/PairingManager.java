package app.ludo.diwas.network;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Manages pairing authorization, PIN verification, and session tokens.
 *
 * Auto-pair is enabled by default: a Controller that found this game over
 * Wi-Fi (or over localhost on the same phone) sends the PIN {@link #AUTO_PIN}
 * and is paired instantly. The numeric PIN still works as a fallback so an
 * older Controller build keeps working.
 */
public class PairingManager {

    /** Magic PIN sent by the Controller when it auto-detects a game. */
    public static final String AUTO_PIN = "auto";

    private boolean controllerModeEnabled = false;
    private boolean autoPairEnabled = true;
    private String pairingPin = "1234";
    private String activeSessionToken = null;
    private final SecureRandom random = new SecureRandom();

    public PairingManager() {
        generateNewPin();
    }

    public synchronized void generateNewPin() {
        int pinNum = 1000 + random.nextInt(9000);
        this.pairingPin = String.valueOf(pinNum);
        this.activeSessionToken = null;
    }

    public synchronized boolean isControllerModeEnabled() {
        return controllerModeEnabled;
    }

    public synchronized void setControllerModeEnabled(boolean enabled) {
        this.controllerModeEnabled = enabled;
        if (!enabled) {
            this.activeSessionToken = null;
        } else if (this.activeSessionToken == null) {
            generateNewPin();
        }
    }

    public synchronized String getPairingPin() {
        return pairingPin;
    }

    public synchronized boolean isAutoPairEnabled() {
        return autoPairEnabled;
    }

    public synchronized void setAutoPairEnabled(boolean autoPairEnabled) {
        this.autoPairEnabled = autoPairEnabled;
    }

    /**
     * Attempts to pair with the given PIN.
     * Returns a new session token if successful, null if failed.
     *
     * An empty PIN (or {@link #AUTO_PIN}) is accepted while auto-pair is on,
     * which is what the Controller sends after it auto-detects the game.
     */
    public synchronized String pair(String pin) {
        if (!controllerModeEnabled) {
            return null; // Controller mode is OFF
        }
        String given = pin == null ? "" : pin.trim();
        boolean isAutoPair = autoPairEnabled
                && (given.isEmpty() || AUTO_PIN.equalsIgnoreCase(given));
        if (isAutoPair || given.equals(pairingPin)) {
            activeSessionToken = UUID.randomUUID().toString();
            return activeSessionToken;
        }
        return null;
    }

    public synchronized boolean isAuthorized(String token) {
        if (!controllerModeEnabled || activeSessionToken == null) {
            return false;
        }
        return activeSessionToken.equals(token);
    }

    public synchronized boolean isPaired() {
        return controllerModeEnabled && activeSessionToken != null;
    }

    public synchronized void disconnectSession() {
        this.activeSessionToken = null;
    }
}
