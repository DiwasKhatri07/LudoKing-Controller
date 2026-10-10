package app.ludo.diwas.engine;

import app.ludo.diwas.protocol.GameStateMessage;

/**
 * Top-level GameState orchestrating the engine components.
 */
public class GameState {
    public enum Status {
        IDLE,
        PLAYING,
        PAUSED,
        FINISHED
    }

    private final String gameId;
    private final DiceEngine diceEngine;
    private final TurnManager turnManager;
    private final PieceManager pieceManager;
    private final WinManager winManager;
    private Status status;
    private boolean controllerMode;

    public GameState(int totalPlayers) {
        this.gameId = "game-" + System.currentTimeMillis();
        this.diceEngine = new DiceEngine();
        this.turnManager = new TurnManager();
        this.pieceManager = new PieceManager();
        this.winManager = new WinManager(totalPlayers);
        this.status = Status.PLAYING;
        this.controllerMode = false;
    }

    public String getGameId() {
        return gameId;
    }

    public DiceEngine getDiceEngine() {
        return diceEngine;
    }

    public TurnManager getTurnManager() {
        return turnManager;
    }

    public PieceManager getPieceManager() {
        return pieceManager;
    }

    public WinManager getWinManager() {
        return winManager;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isControllerMode() {
        return controllerMode;
    }

    public void setControllerMode(boolean controllerMode) {
        this.controllerMode = controllerMode;
    }

    public GameStateMessage toMessage() {
        GameStateMessage msg = new GameStateMessage(gameId, turnManager.getCurrentPlayer(),
                diceEngine.getLastRoll(), status.name(), controllerMode);
        for (String player : turnManager.getActivePlayers()) {
            msg.setPlayerPieces(player, pieceManager.getPieces(player));
        }
        return msg;
    }

    public void reset() {
        diceEngine.resetConsecutiveSixes();
        turnManager.reset();
        pieceManager.reset();
        winManager.reset();
        status = Status.PLAYING;
    }
}
