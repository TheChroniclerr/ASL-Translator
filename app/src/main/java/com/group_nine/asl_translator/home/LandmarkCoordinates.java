package com.group_nine.asl_translator.home;

/** Converts upright detector coordinates back to the unmirrored camera buffer. */
final class LandmarkCoordinates {
    private LandmarkCoordinates() { }

    static float[] toBuffer(float x, float y, int rotation, int width, int height) {
        switch (rotation) {
            case 0: return new float[]{x * width, y * height};
            case 90: return new float[]{y * width, (1 - x) * height};
            case 180: return new float[]{(1 - x) * width, (1 - y) * height};
            case 270: return new float[]{(1 - y) * width, x * height};
            default: throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270");
        }
    }
}
