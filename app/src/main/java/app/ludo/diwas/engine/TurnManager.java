package app.ludo.diwas.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages player turns, turn rotations, and extra turn rules.
 */
public class TurnManager {
    private final List<String> activePlayers;
    private int currentIndex = 0;
    private boolean extraTurnGranted = false;

    public TurnManager() {
        this(new String[]{"RED", "GREEN", "YELLOW", "BLUE"});
    }

    public TurnManager(String[] players) {
        this.activePlayers = new ArrayList<>();
        if (players != null) {
            Collections.addAll(this.activePlayers, players);
        }
    }

    public List<String> getActivePlayers() {
        return activePlayers;
    }

    public String getCurrentPlayer() {
        if (activePlayers.isEmpty()) {
            return "RED";
        }
        return activePlayers.get(currentIndex % activePlayers.size());
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void grantExtraTurn() {
        this.extraTurnGranted = true;
    }

    public boolean isExtraTurnGranted() {
        return extraTurnGranted;
    }

    /**
     * Advances turn to the next active player unless an extra turn was granted.
     * Returns the new current player.
     */
    public String nextTurn() {
        if (activePlayers.isEmpty()) {
            return "RED";
        }
        if (extraTurnGranted) {
            extraTurnGranted = false;
            return getCurrentPlayer();
        }
        currentIndex = (currentIndex + 1) % activePlayers.size();
        return getCurrentPlayer();
    }

    /**
     * Forces advancement of turn, clearing any extra turns.
     */
    public String forceNextTurn() {
        extraTurnGranted = false;
        if (activePlayers.isEmpty()) {
            return "RED";
        }
        currentIndex = (currentIndex + 1) % activePlayers.size();
        return getCurrentPlayer();
    }

    public void removePlayer(String player) {
        int idx = activePlayers.indexOf(player);
        if (idx != -1) {
            activePlayers.remove(idx);
            if (activePlayers.isEmpty()) {
                currentIndex = 0;
            } else if (currentIndex >= activePlayers.size()) {
                currentIndex = 0;
            }
        }
    }

    public void reset() {
        currentIndex = 0;
        extraTurnGranted = false;
    }
}
