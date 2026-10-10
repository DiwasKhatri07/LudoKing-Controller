package app.ludo.diwas.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages piece positions, board track calculations, safe zones, and capturing.
 *
 * Board layout model:
 * Track: 52 common cells (0 to 51)
 * Safe spots: 1 (Red start), 9 (Green star), 14 (Green start), 22 (Yellow star),
 *             27 (Yellow start), 35 (Blue star), 40 (Blue start), 48 (Red star).
 * Base position: -1 (piece in base)
 * Home stretch: 52, 53, 54, 55, 56
 * Reached Home: 57
 */
public class PieceManager {
    public static final int BASE_POS = -1;
    public static final int HOME_POS = 57;
    public static final int TRACK_LENGTH = 52;

    public static final Set<Integer> SAFE_SPOTS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(1, 9, 14, 22, 27, 35, 40, 48)
    ));

    // Player start positions on common track
    public static final Map<String, Integer> START_SPOTS;
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put("RED", 1);
        map.put("GREEN", 14);
        map.put("YELLOW", 27);
        map.put("BLUE", 40);
        START_SPOTS = Collections.unmodifiableMap(map);
    }

    // Each player has 4 pieces. Pos stores either -1 (base), 0..51 (track), or 52..57 (home stretch / home)
    // Note: for internal calculation we can track absolute distance travelled from start (0..57).
    private final Map<String, int[]> playerPieces = new HashMap<>();

    public PieceManager() {
        reset();
    }

    public void reset() {
        playerPieces.put("RED", new int[]{-1, -1, -1, -1});
        playerPieces.put("GREEN", new int[]{-1, -1, -1, -1});
        playerPieces.put("YELLOW", new int[]{-1, -1, -1, -1});
        playerPieces.put("BLUE", new int[]{-1, -1, -1, -1});
    }

    public int[] getPieces(String color) {
        int[] pieces = playerPieces.get(color.toUpperCase());
        if (pieces == null) {
            return new int[]{-1, -1, -1, -1};
        }
        return pieces.clone();
    }

    public void setPiece(String color, int pieceIndex, int position) {
        int[] pieces = playerPieces.get(color.toUpperCase());
        if (pieces != null && pieceIndex >= 0 && pieceIndex < 4) {
            pieces[pieceIndex] = position;
        }
    }

    public boolean isSafeSpot(int trackPos) {
        return SAFE_SPOTS.contains(trackPos);
    }

    /**
     * Checks if a piece can move with the rolled dice value.
     */
    public boolean canMovePiece(String color, int pieceIndex, int diceValue) {
        int[] pieces = playerPieces.get(color.toUpperCase());
        if (pieces == null || pieceIndex < 0 || pieceIndex >= 4) return false;

        int currentPos = pieces[pieceIndex];
        if (currentPos == HOME_POS) {
            return false; // Already completed
        }

        if (currentPos == BASE_POS) {
            return diceValue == 6; // Requires a 6 to leave base
        }

        // Must not overshoot HOME_POS (57)
        return (currentPos + diceValue) <= HOME_POS;
    }

    /**
     * Checks if player has any legal moves for this dice roll.
     */
    public boolean hasAnyLegalMove(String color, int diceValue) {
        for (int i = 0; i < 4; i++) {
            if (canMovePiece(color, i, diceValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves a piece by diceValue steps.
     * Returns true if move succeeded.
     */
    public boolean movePiece(String color, int pieceIndex, int diceValue) {
        if (!canMovePiece(color, pieceIndex, diceValue)) {
            return false;
        }

        int[] pieces = playerPieces.get(color.toUpperCase());
        int currentPos = pieces[pieceIndex];

        if (currentPos == BASE_POS) {
            pieces[pieceIndex] = 0; // Starts at track entry (step 0)
        } else {
            pieces[pieceIndex] = currentPos + diceValue;
        }
        return true;
    }

    /**
     * Checks if landing on a cell captures an opponent piece.
     * If captured, sets opponent piece back to BASE_POS (-1) and returns true.
     */
    public boolean checkAndCapture(String movingPlayer, int movedPieceIndex) {
        int[] movingPieces = playerPieces.get(movingPlayer.toUpperCase());
        if (movingPieces == null) return false;
        int movedStep = movingPieces[movedPieceIndex];

        // Pieces in base or in home stretch cannot capture / be captured
        if (movedStep < 0 || movedStep >= 51) {
            return false;
        }

        Integer startOffset = START_SPOTS.get(movingPlayer.toUpperCase());
        if (startOffset == null) return false;
        int commonCell = (startOffset + movedStep) % TRACK_LENGTH;

        if (isSafeSpot(commonCell)) {
            return false; // Cannot capture on safe spots
        }

        boolean captured = false;
        for (Map.Entry<String, int[]> entry : playerPieces.entrySet()) {
            String opponentColor = entry.getKey();
            if (opponentColor.equalsIgnoreCase(movingPlayer)) continue;

            Integer oppStart = START_SPOTS.get(opponentColor);
            if (oppStart == null) continue;

            int[] oppPieces = entry.getValue();
            for (int i = 0; i < oppPieces.length; i++) {
                int oppStep = oppPieces[i];
                if (oppStep >= 0 && oppStep < 51) {
                    int oppCommonCell = (oppStart + oppStep) % TRACK_LENGTH;
                    if (oppCommonCell == commonCell) {
                        // Captured!
                        oppPieces[i] = BASE_POS;
                        captured = true;
                    }
                }
            }
        }
        return captured;
    }

    /**
     * Returns true if all 4 pieces have reached HOME_POS.
     */
    public boolean hasPlayerWon(String color) {
        int[] pieces = playerPieces.get(color.toUpperCase());
        if (pieces == null) return false;
        for (int p : pieces) {
            if (p != HOME_POS) return false;
        }
        return true;
    }
}
