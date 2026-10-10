package app.ludo.diwas.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the game state message synchronized between Game and Controller.
 */
public class GameStateMessage {
    private String gameId;
    private String turn;
    private int dice;
    private String status; // IDLE, PLAYING, PAUSED, FINISHED
    private boolean controllerMode;
    private Map<String, int[]> playerPieces; // Color -> array of 4 block indices

    public GameStateMessage() {
        this.gameId = "local-" + System.currentTimeMillis();
        this.turn = "RED";
        this.dice = 0;
        this.status = "PLAYING";
        this.controllerMode = true;
        this.playerPieces = new HashMap<>();
    }

    public GameStateMessage(String gameId, String turn, int dice, String status, boolean controllerMode) {
        this.gameId = gameId;
        this.turn = turn;
        this.dice = dice;
        this.status = status;
        this.controllerMode = controllerMode;
        this.playerPieces = new HashMap<>();
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getTurn() {
        return turn;
    }

    public void setTurn(String turn) {
        this.turn = turn;
    }

    public int getDice() {
        return dice;
    }

    public void setDice(int dice) {
        this.dice = dice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isControllerMode() {
        return controllerMode;
    }

    public void setControllerMode(boolean controllerMode) {
        this.controllerMode = controllerMode;
    }

    public Map<String, int[]> getPlayerPieces() {
        return playerPieces;
    }

    public void setPlayerPieces(String color, int[] pieces) {
        this.playerPieces.put(color, pieces);
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("gameId", gameId);
        json.put("turn", turn);
        json.put("dice", dice);
        json.put("status", status);
        json.put("controllerMode", controllerMode);

        JSONObject playersJson = new JSONObject();
        for (Map.Entry<String, int[]> entry : playerPieces.entrySet()) {
            JSONObject playerObj = new JSONObject();
            JSONArray piecesArray = new JSONArray();
            if (entry.getValue() != null) {
                for (int pos : entry.getValue()) {
                    piecesArray.put(pos);
                }
            }
            playerObj.put("pieces", piecesArray);
            playersJson.put(entry.getKey(), playerObj);
        }
        json.put("players", playersJson);
        return json;
    }

    public static GameStateMessage fromJsonObject(JSONObject json) throws JSONException {
        GameStateMessage state = new GameStateMessage();
        state.setGameId(json.optString("gameId", "local-1"));
        state.setTurn(json.optString("turn", "RED"));
        state.setDice(json.optInt("dice", 0));
        state.setStatus(json.optString("status", "PLAYING"));
        state.setControllerMode(json.optBoolean("controllerMode", true));

        JSONObject playersJson = json.optJSONObject("players");
        if (playersJson != null) {
            JSONArray names = playersJson.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String color = names.getString(i);
                    JSONObject pObj = playersJson.optJSONObject(color);
                    if (pObj != null) {
                        JSONArray pieces = pObj.optJSONArray("pieces");
                        if (pieces != null) {
                            int[] arr = new int[pieces.length()];
                            for (int j = 0; j < pieces.length(); j++) {
                                arr[j] = pieces.getInt(j);
                            }
                            state.setPlayerPieces(color, arr);
                        }
                    }
                }
            }
        }
        return state;
    }
}
