package app.ludo.diwas.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages player rankings and game victory states.
 */
public class WinManager {
    private final List<String> winners = new ArrayList<>();
    private final int totalPlayers;

    public WinManager(int totalPlayers) {
        this.totalPlayers = Math.max(2, totalPlayers);
    }

    /**
     * Records a player as finished/won if not already recorded.
     * Returns their rank (1 for 1st place, 2 for 2nd place, etc.)
     */
    public int registerWinner(String player) {
        String p = player.toUpperCase();
        if (!winners.contains(p)) {
            winners.add(p);
        }
        return winners.indexOf(p) + 1;
    }

    public boolean hasWon(String player) {
        return winners.contains(player.toUpperCase());
    }

    public List<String> getWinners() {
        return new ArrayList<>(winners);
    }

    public int getWinnerCount() {
        return winners.size();
    }

    /**
     * Game is complete when all but one player has finished.
     */
    public boolean isGameFinished() {
        return winners.size() >= (totalPlayers - 1);
    }

    public void reset() {
        winners.clear();
    }
}
