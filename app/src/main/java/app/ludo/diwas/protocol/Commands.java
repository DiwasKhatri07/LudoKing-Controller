package app.ludo.diwas.protocol;

/**
 * Controller Protocol Commands sent from Controller to Game APK.
 */
public final class Commands {
    private Commands() {}

    // Pairing & Session
    public static final String PAIR_REQUEST = "PAIR_REQUEST";
    public static final String DISCONNECT = "DISCONNECT";
    public static final String GET_STATE = "GET_STATE";
    /** Keep-alive ping from the controller — answered with a STATE_UPDATE. */
    public static final String PING = "PING";

    // Live Game Controls
    public static final String ROLL_DICE = "ROLL_DICE";
    public static final String MOVE_PIECE = "MOVE_PIECE";
    public static final String SELECT_PLAYER = "SELECT_PLAYER";
    public static final String START_GAME = "START_GAME";
    public static final String PAUSE_GAME = "PAUSE_GAME";
    public static final String RESUME_GAME = "RESUME_GAME";
    public static final String RESTART_GAME = "RESTART_GAME";
    public static final String RESET_BOARD = "RESET_BOARD";
    public static final String SKIP_TURN = "SKIP_TURN";
    public static final String END_GAME = "END_GAME";

    // Controller convenience features
    /** value=1..6 (sticky until changed/cleared) or 0/absent = clear the fixed dice. */
    public static final String SET_FIXED_DICE = "SET_FIXED_DICE";
    /** value=true/false (or enabled=true/false): auto-move the best piece after each roll. */
    public static final String SET_AUTO_MOVE = "SET_AUTO_MOVE";
    /** visible=true/false: show or hide the CONNECTED banner on the game screen. */
    public static final String SET_BANNER_VISIBLE = "SET_BANNER_VISIBLE";

    // Fields
    public static final String FIELD_COMMAND = "command";
    public static final String FIELD_PIN = "pin";
    public static final String FIELD_TOKEN = "token";
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_PIECE_INDEX = "pieceIndex";
    public static final String FIELD_PLAYER = "player";
}
