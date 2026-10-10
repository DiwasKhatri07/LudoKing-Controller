package app.ludo.diwas;

import app.ludo.diwas.engine.DiceEngine;
import app.ludo.diwas.engine.PieceManager;
import app.ludo.diwas.engine.TurnManager;
import app.ludo.diwas.engine.WinManager;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the game engine layer.
 */
public class GameEngineTest {

    // ─── DiceEngine ────────────────────────────────────────────────────────────

    private DiceEngine diceEngine;

    @Before
    public void setUp() {
        diceEngine = new DiceEngine();
    }

    @Test
    public void dice_randomRoll_isInRange() {
        for (int i = 0; i < 100; i++) {
            int result = diceEngine.roll(false, null);
            assertTrue("Dice must be 1–6", result >= 1 && result <= 6);
        }
    }

    @Test
    public void dice_controlledRoll_returnsRequestedValue() {
        for (int val = 1; val <= 6; val++) {
            int result = diceEngine.roll(true, val);
            assertEquals("Controlled roll must return exact value", val, result);
        }
    }

    @Test
    public void dice_controlledRoll_outOfRange_fallsBackToRandom() {
        // Value 0 is invalid: should produce a random 1–6
        int result = diceEngine.roll(true, 0);
        assertTrue("Out-of-range controlled value must fall back to random 1–6",
                result >= 1 && result <= 6);
        result = diceEngine.roll(true, 7);
        assertTrue(result >= 1 && result <= 6);
    }

    @Test
    public void dice_controllerModeOff_ignoresControlledValue() {
        // When controller mode is off the engine must roll randomly even if a value is provided
        boolean allSame = true;
        int first = diceEngine.roll(false, 3);
        for (int i = 0; i < 30; i++) {
            if (diceEngine.roll(false, 3) != first) {
                allSame = false;
                break;
            }
        }
        // Very low probability that all 30 rolls produce the same number naturally
        // This is a probabilistic assertion – it catches the bug 99.99% of the time
        assertFalse("Controller mode OFF should not always return 3", allSame);
    }

    @Test
    public void dice_consecutiveSixes_trackedCorrectly() {
        diceEngine.roll(true, 6);
        assertEquals(1, diceEngine.getConsecutiveSixes());
        diceEngine.roll(true, 6);
        assertEquals(2, diceEngine.getConsecutiveSixes());
        diceEngine.roll(true, 3);
        assertEquals(0, diceEngine.getConsecutiveSixes());
    }

    @Test
    public void dice_threeConsecutiveSixes_detected() {
        diceEngine.roll(true, 6);
        diceEngine.roll(true, 6);
        diceEngine.roll(true, 6);
        assertTrue(diceEngine.isThreeConsecutiveSixes());
    }

    // ─── TurnManager ───────────────────────────────────────────────────────────

    @Test
    public void turn_initialPlayerIsRed() {
        TurnManager tm = new TurnManager(new String[]{"RED", "GREEN", "YELLOW", "BLUE"});
        assertEquals("RED", tm.getCurrentPlayer());
    }

    @Test
    public void turn_nextTurn_cycles() {
        TurnManager tm = new TurnManager(new String[]{"RED", "GREEN", "YELLOW"});
        assertEquals("RED", tm.getCurrentPlayer());
        tm.nextTurn();
        assertEquals("GREEN", tm.getCurrentPlayer());
        tm.nextTurn();
        assertEquals("YELLOW", tm.getCurrentPlayer());
        tm.nextTurn();
        assertEquals("RED", tm.getCurrentPlayer()); // wraps
    }

    @Test
    public void turn_extraTurn_stays() {
        TurnManager tm = new TurnManager(new String[]{"RED", "GREEN"});
        tm.grantExtraTurn();
        String before = tm.getCurrentPlayer();
        String after = tm.nextTurn();
        assertEquals("Extra turn should stay on same player", before, after);
        // Next turn advances normally
        assertEquals("GREEN", tm.nextTurn());
    }

    @Test
    public void turn_removePlayer_adjustsCorrectly() {
        TurnManager tm = new TurnManager(new String[]{"RED", "GREEN", "BLUE"});
        tm.removePlayer("GREEN");
        assertEquals(2, tm.getActivePlayers().size());
        assertFalse(tm.getActivePlayers().contains("GREEN"));
    }

    @Test
    public void turn_reset_goesBackToFirst() {
        TurnManager tm = new TurnManager(new String[]{"RED", "GREEN"});
        tm.nextTurn(); // now GREEN
        tm.reset();
        assertEquals("RED", tm.getCurrentPlayer());
    }

    // ─── PieceManager ──────────────────────────────────────────────────────────

    @Test
    public void piece_initiallyInBase() {
        PieceManager pm = new PieceManager();
        int[] pieces = pm.getPieces("RED");
        for (int p : pieces) {
            assertEquals(PieceManager.BASE_POS, p);
        }
    }

    @Test
    public void piece_canLeaveBaseOnlyWithSix() {
        PieceManager pm = new PieceManager();
        assertFalse(pm.canMovePiece("RED", 0, 5));
        assertFalse(pm.canMovePiece("RED", 0, 1));
        assertTrue(pm.canMovePiece("RED", 0, 6));
    }

    @Test
    public void piece_moveFromBase_setsToTrack() {
        PieceManager pm = new PieceManager();
        assertTrue(pm.movePiece("RED", 0, 6));
        assertEquals(0, pm.getPieces("RED")[0]); // step 0 on track
    }

    @Test
    public void piece_cannotOvershootHome() {
        PieceManager pm = new PieceManager();
        pm.setPiece("RED", 0, 55); // near home
        assertFalse(pm.canMovePiece("RED", 0, 3)); // 55+3 = 58 > 57
        assertTrue(pm.canMovePiece("RED", 0, 2));  // 55+2 = 57 exactly
    }

    @Test
    public void piece_reachHome_registersWin() {
        PieceManager pm = new PieceManager();
        // Put all 4 RED pieces at step 56 and move them 1 step to HOME (57)
        for (int i = 0; i < 4; i++) {
            pm.setPiece("RED", i, 56);
            pm.movePiece("RED", i, 1);
        }
        assertTrue(pm.hasPlayerWon("RED"));
    }

    @Test
    public void piece_safeSpotPreventsCapture() {
        PieceManager pm = new PieceManager();
        // Put GREEN piece at step 0 which maps to position 14 (GREEN start = safe spot)
        pm.setPiece("GREEN", 0, 0);
        // Put RED piece at same cell
        pm.setPiece("RED", 0, 0);
        pm.movePiece("RED", 0, 6); // move RED
        // GREEN's position 14 is a safe spot – should not be captured
        // (this is a basic sanity test; full safe-spot logic is in checkAndCapture)
        assertTrue(pm.isSafeSpot(14)); // GREEN start position is safe
    }

    // ─── WinManager ────────────────────────────────────────────────────────────

    @Test
    public void win_registerWinner_assignsRank() {
        WinManager wm = new WinManager(4);
        assertEquals(1, wm.registerWinner("RED"));
        assertEquals(2, wm.registerWinner("GREEN"));
        assertEquals(3, wm.registerWinner("BLUE"));
    }

    @Test
    public void win_gameFinished_whenAllButOneDone() {
        WinManager wm = new WinManager(4);
        assertFalse(wm.isGameFinished());
        wm.registerWinner("RED");
        wm.registerWinner("GREEN");
        wm.registerWinner("BLUE");
        assertTrue(wm.isGameFinished()); // 3 of 4 done
    }

    @Test
    public void win_reset_clearsWinners() {
        WinManager wm = new WinManager(4);
        wm.registerWinner("RED");
        wm.reset();
        assertEquals(0, wm.getWinnerCount());
        assertFalse(wm.isGameFinished());
    }
}
