package app.ludo.diwas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.ludo.diwas.network.PairingManager;

import org.junit.Test;

/**
 * Unit tests for the Controller pairing handshake, including the PIN-less
 * auto-pair flow used when a Controller finds the game automatically.
 */
public class PairingManagerTest {

    @Test
    public void autoPair_acceptsAutoPin_whenControllerModeOn() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);

        String token = pm.pair(PairingManager.AUTO_PIN);

        assertNotNull("Auto pin must pair without the user reading a PIN", token);
        assertTrue(pm.isPaired());
        assertTrue(pm.isAuthorized(token));
    }

    @Test
    public void autoPair_acceptsEmptyPin_whenControllerModeOn() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);

        assertNotNull(pm.pair(""));
        assertNotNull(pm.pair(null));
    }

    @Test
    public void numericPin_stillPairs_asFallback() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);

        String token = pm.pair(pm.getPairingPin());

        assertNotNull("Typed numeric PIN must keep working", token);
    }

    @Test
    public void wrongPin_isRejected() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);

        assertNull(pm.pair("999999"));
        assertFalse(pm.isPaired());
    }

    @Test
    public void controllerModeOff_rejectsEveryPin() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(false);

        assertNull(pm.pair(PairingManager.AUTO_PIN));
        assertNull(pm.pair(pm.getPairingPin()));
    }

    @Test
    public void autoPairDisabled_requiresNumericPin() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);
        pm.setAutoPairEnabled(false);

        assertNull(pm.pair(PairingManager.AUTO_PIN));
        assertEquals(4, pm.getPairingPin().length());
        assertNotNull(pm.pair(pm.getPairingPin()));
    }

    @Test
    public void disconnectSession_clearsAuthorization() {
        PairingManager pm = new PairingManager();
        pm.setControllerModeEnabled(true);
        String token = pm.pair(PairingManager.AUTO_PIN);

        pm.disconnectSession();

        assertFalse(pm.isAuthorized(token));
        assertFalse(pm.isPaired());
    }
}
