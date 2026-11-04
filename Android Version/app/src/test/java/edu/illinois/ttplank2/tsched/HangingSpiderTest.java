package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HangingSpiderTest {
    private static final float REACH = 700;
    private static final float SPEED = 35;

    @Test
    public void theSpiderHangsOverTheTopThirdOfATallScreenAndTheTopHalfOfAWideOne() {
        assertEquals(1f / 3f, HangingSpider.zoneShare(1080, 2340), 0.0001f);
        assertEquals(0.5f, HangingSpider.zoneShare(2340, 1080), 0.0001f);
    }

    @Test
    public void theSpiderStaysWithinItsZoneAndReachesTheTopAndTheBottomOfIt() {
        float lowest = 1;
        float highest = 0;
        for (int tenths = 0; tenths < 12000; tenths++) {
            float depth = HangingSpider.depthShare(tenths / 10f, REACH, SPEED);
            assertTrue(depth >= -0.0001f && depth <= 1.0001f);
            lowest = Math.min(lowest, depth);
            highest = Math.max(highest, depth);
        }
        assertTrue(lowest < 0.01f);
        assertTrue(highest > 0.99f);
    }

    @Test
    public void itAlwaysMovesAtTheSameSpeedWhenItMovesAndSometimesStops() {
        float step = 0.05f;
        int stopped = 0;
        int moving = 0;
        float last = HangingSpider.depthShare(0, REACH, SPEED);
        for (int i = 1; i < 6000; i++) {
            float now = HangingSpider.depthShare(i * step, REACH, SPEED);
            float pixelsPerSecond = Math.abs(now - last) * REACH / step;
            if (pixelsPerSecond < 0.001f) {
                stopped++;
            } else {
                moving++;
                // Moving: exactly the one speed, apart from the step where it starts or stops partway.
                assertTrue("speed " + pixelsPerSecond, pixelsPerSecond <= SPEED * 1.03f);
            }
            last = now;
        }
        assertTrue("stopped " + stopped, stopped > 100);
        assertTrue("moving " + moving, moving > 100);
    }

    @Test
    public void whileMovingItNeverSlowsDownOrSpeedsUpInTheMiddleOfAMove() {
        // Between two nearby moments well inside the first move down, the distance covered is speed times time.
        float a = HangingSpider.depthShare(2f, REACH, SPEED);
        float b = HangingSpider.depthShare(3f, REACH, SPEED);
        float c = HangingSpider.depthShare(4f, REACH, SPEED);
        assertEquals(SPEED, (b - a) * REACH, 0.3f);
        assertEquals(SPEED, (c - b) * REACH, 0.3f);
    }
}
