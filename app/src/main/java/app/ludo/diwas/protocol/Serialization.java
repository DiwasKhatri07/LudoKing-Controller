package app.ludo.diwas.protocol;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message serialization and deserialization utilities.
 */
public final class Serialization {
    private Serialization() {}

    public static String buildEventJson(String event, JSONObject payload) {
        try {
            JSONObject obj = (payload != null) ? new JSONObject(payload.toString()) : new JSONObject();
            obj.put(Events.FIELD_EVENT, event);
            return obj.toString();
        } catch (JSONException e) {
            return "{\"" + Events.FIELD_EVENT + "\":\"" + event + "\"}";
        }
    }

    public static String buildEventJson(String event, String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(Events.FIELD_EVENT, event);
            if (message != null) {
                obj.put(Events.FIELD_MESSAGE, message);
            }
            return obj.toString();
        } catch (JSONException e) {
            return "{\"" + Events.FIELD_EVENT + "\":\"" + event + "\"}";
        }
    }

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
