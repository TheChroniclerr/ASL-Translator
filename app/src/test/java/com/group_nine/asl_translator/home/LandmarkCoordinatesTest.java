package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LandmarkCoordinatesTest {
    @Test
    public void uprightCoordinatesReturnToEachBufferRotation() {
        int[] rotations = {0, 90, 180, 270};
        float[][] expected = {{128, 144}, {192, 384}, {512, 336}, {448, 96}};
        for (int i = 0; i < rotations.length; i++) {
            assertArrayEquals(expected[i], LandmarkCoordinates.toBuffer(.2f, .3f, rotations[i], 640, 480), .001f);
        }
    }

    @Test
    public void centerStaysCenteredForEveryRotation() {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            assertArrayEquals(new float[]{320, 240},
                    LandmarkCoordinates.toBuffer(.5f, .5f, rotation, 640, 480), .001f);
        }
    }

    @Test
    public void rotationMustBeOrthogonal() {
        assertThrows(IllegalArgumentException.class, () -> LandmarkCoordinates.toBuffer(.5f, .5f, 45, 640, 480));
    }

    @Test
    public void resultsDefensivelyCopyTheirLists() {
        List<HandTrackingResult.Landmark> points = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            points.add(new HandTrackingResult.Landmark(.5f, .5f, 0));
        }
        HandTrackingResult.Hand hand = new HandTrackingResult.Hand(points, "Left", .9f);
        List<HandTrackingResult.Hand> hands = new ArrayList<>(Collections.singletonList(hand));
        HandTrackingResult result = new HandTrackingResult(1, 640, 480, 0, null, hands);
        points.clear();
        hands.clear();
        assertEquals(1, result.hands.size());
        assertEquals(21, result.hands.get(0).landmarks.size());
        assertThrows(UnsupportedOperationException.class, () -> result.hands.clear());
        assertThrows(UnsupportedOperationException.class, () -> hand.landmarks.clear());
    }

    @Test
    public void incompleteHandResultsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HandTrackingResult.Hand(Collections.emptyList(), "Left", 1));
    }
}
