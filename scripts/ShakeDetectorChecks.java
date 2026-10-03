package com.senecapp.sensors;

/** Dependency-free behavioral checks against the actual compiled detector. */
public final class ShakeDetectorChecks {
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean pulse(ShakeDetector detector, long time) {
        detector.sample(0, 0, 9.80665f, time - 50);
        return detector.sample(30, 0, 9.80665f, time);
    }

    public static void main(String[] args) {
        ShakeDetector normal = new ShakeDetector();
        for (int i = 0; i < 100; i++) {
            double angle = i * Math.PI / 50;
            expect(!normal.sample((float) (9.80665 * Math.sin(angle)), 0,
                    (float) (9.80665 * Math.cos(angle)), i * 100L), "Tilting must not refresh");
        }

        ShakeDetector shake = new ShakeDetector();
        expect(!pulse(shake, 100), "One bump must not refresh");
        expect(!shake.sample(30, 0, 9.80665f, 300), "A sustained peak is not two shakes");
        expect(pulse(shake, 500), "Two distinct peaks should refresh");
        expect(!pulse(shake, 800) && !pulse(shake, 1100), "Cooldown must suppress repeated requests");
        expect(!pulse(shake, 10600) && pulse(shake, 10900), "Refreshing should work after cooldown");

        ShakeDetector slow = new ShakeDetector();
        expect(!pulse(slow, 100) && !pulse(slow, 1500), "Separated bumps must not count as one shake");

        ShakeDetector paused = new ShakeDetector();
        pulse(paused, 100);
        paused.resetMotion();
        expect(!pulse(paused, 500), "Partial gestures must not survive a lifecycle reset");
        expect(pulse(paused, 800), "Fresh gesture should still work after reset");
        paused.resetMotion();
        expect(!pulse(paused, 1000) && !pulse(paused, 1300), "Reset must not bypass cooldown");

        ShakeDetector invalid = new ShakeDetector();
        pulse(invalid, 100);
        expect(!invalid.sample(Float.NaN, 0, 0, 200), "NaN must not trigger a refresh");
        expect(!invalid.sample(Float.POSITIVE_INFINITY, 0, 0, 250), "Infinity must not trigger a refresh");
        expect(!pulse(invalid, 500), "Invalid readings must discard the partial gesture");
        System.out.println("PASS: tilt, single bump, plateau, deliberate shake, cooldown, lifecycle reset and invalid readings");
    }
}
