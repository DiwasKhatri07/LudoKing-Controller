package app.ludo.diwas.engine;

import java.security.SecureRandom;

/**
 * Independent Dice Engine with fair random rolls and authorized controlled dice injection.
 */
public class DiceEngine {
    private final SecureRandom random = new SecureRandom();
    private int lastRoll = 1;
    private int consecutiveSixes = 0;

    /**
     * Rolls the dice.
     * If controllerMode is true and controlledValue is non-null and between 1 and 6,
     * the controlled value is used.
     * Otherwise, a fair random number between 1 and 6 is generated.
     */
    public int roll(boolean controllerMode, Integer controlledValue) {
        int result;
        if (controllerMode && controlledValue != null && controlledValue >= 1 && controlledValue <= 6) {
            result = controlledValue;
        } else {
            result = random.nextInt(6) + 1;
        }

        lastRoll = result;
        if (result == 6) {
            consecutiveSixes++;
        } else {
            consecutiveSixes = 0;
        }
        return result;
    }

    public int getLastRoll() {
        return lastRoll;
    }

    public int getConsecutiveSixes() {
        return consecutiveSixes;
    }

    public void resetConsecutiveSixes() {
        consecutiveSixes = 0;
    }

    public boolean isThreeConsecutiveSixes() {
        return consecutiveSixes >= 3;
    }
}
