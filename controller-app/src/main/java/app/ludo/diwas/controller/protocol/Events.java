package app.ludo.diwas.controller.protocol;

public final class Events {
    private Events() {}

    public static final String PAIR_ACCEPTED = "PAIR_ACCEPTED";
    public static final String PAIR_REJECTED = "PAIR_REJECTED";

    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String TURN_CHANGED = "TURN_CHANGED";
    public static final String DICE_ROLLED = "DICE_ROLLED";
    public static final String PIECE_MOVED = "PIECE_MOVED";
    public static final String PLAYER_WON = "PLAYER_WON";
    public static final String GAME_PAUSED = "GAME_PAUSED";
    public static final String GAME_RESUMED = "GAME_RESUMED";
    public static final String GAME_RESET = "GAME_RESET";
    public static final String GAME_FINISHED = "GAME_FINISHED";
    public static final String STATE_UPDATE = "STATE_UPDATE";
    public static final String ERROR = "ERROR";

    public static final String FIELD_EVENT = "event";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_TOKEN = "token";
    public static final String FIELD_TURN = "turn";
    public static final String FIELD_DICE = "dice";
    public static final String FIELD_PIECE_INDEX = "pieceIndex";
    public static final String FIELD_FROM_BLOCK = "fromBlock";
    public static final String FIELD_TO_BLOCK = "toBlock";
    public static final String FIELD_WINNER = "winner";
    public static final String FIELD_STATE = "state";
}
