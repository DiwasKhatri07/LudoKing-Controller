package app.ludo.diwas.controller.protocol;

import org.json.JSONException;
import org.json.JSONObject;

public final class Serialization {
    private Serialization() {}

    public static String buildCommandJson(String command, String token, JSONObject params) {
        try {
            JSONObject obj = (params != null) ? new JSONObject(params.toString()) : new JSONObject();
            obj.put(Commands.FIELD_COMMAND, command);
            if (token != null) {
                obj.put(Commands.FIELD_TOKEN, token);
            }
            return obj.toString();
        } catch (JSONException e) {
            return "{\"" + Commands.FIELD_COMMAND + "\":\"" + command + "\"}";
        }
    }

    public static JSONObject parseJson(String line) throws JSONException {
        if (line == null || line.trim().isEmpty()) {
            throw new JSONException("Empty message payload");
        }
        return new JSONObject(line.trim());
    }
}
