package com.group_nine.asl_translator.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.CoordinateTransform;
import androidx.camera.view.transform.OutputTransform;

/** Draws landmarks using the current preview transform, including its display mirroring. */
@OptIn(markerClass = TransformExperimental.class)
public final class HandLandmarkOverlay extends View {
    private static final int[][] CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {5, 9}, {9, 10}, {10, 11}, {11, 12}, {9, 13}, {13, 14}, {14, 15}, {15, 16},
            {0, 17}, {13, 17}, {17, 18}, {18, 19}, {19, 20}
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private HandTrackingResult result;
    private PreviewView preview;

    public HandLandmarkOverlay(Context context, @Nullable AttributeSet attributes) {
        super(context, attributes);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void showResult(@Nullable HandTrackingResult result, PreviewView preview) {
        this.result = result;
        this.preview = preview;
        invalidate();
    }

    void clear() {
        result = null;
        invalidate();
    }

    int getHandCount() {
        return result == null ? 0 : result.hands.size();
    }

    static float[] project(HandTrackingResult result, HandTrackingResult.Hand hand, OutputTransform target) {
        float[] points = new float[hand.landmarks.size() * 2];
        for (int i = 0; i < hand.landmarks.size(); i++) {
            HandTrackingResult.Landmark point = hand.landmarks.get(i);
            float[] buffer = LandmarkCoordinates.toBuffer(point.x, point.y, result.rotationDegrees,
                    result.bufferWidth, result.bufferHeight);
            points[2 * i] = buffer[0];
            points[2 * i + 1] = buffer[1];
        }
        new CoordinateTransform(result.sourceTransform(), target).mapPoints(points);
        return points;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null || preview == null || result.hands.isEmpty()) {
            return;
        }
        OutputTransform target = preview.getOutputTransform();
        if (target == null) {
            return;
        }
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        for (HandTrackingResult.Hand hand : result.hands) {
            float[] points = project(result, hand, target);
            paint.setColor("Left".equals(hand.handedness) ? Color.rgb(52, 235, 170) : Color.rgb(255, 202, 70));
            paint.setStrokeWidth(2.5f * density);
            for (int[] connection : CONNECTIONS) {
                canvas.drawLine(points[connection[0] * 2], points[connection[0] * 2 + 1],
                        points[connection[1] * 2], points[connection[1] * 2 + 1], paint);
            }
            for (int i = 0; i < points.length; i += 2) {
                paint.setColor(Color.BLACK);
                canvas.drawCircle(points[i], points[i + 1], 4.5f * density, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(points[i], points[i + 1], 3 * density, paint);
            }
        }
        canvas.restore();
    }
}
