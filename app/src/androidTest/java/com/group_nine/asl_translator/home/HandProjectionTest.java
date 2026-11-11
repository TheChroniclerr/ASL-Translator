package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertEquals;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Size;

import androidx.camera.view.transform.OutputTransform;
import androidx.camera.view.transform.CoordinateTransform;
import androidx.annotation.OptIn;
import androidx.camera.view.TransformExperimental;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
@OptIn(markerClass = TransformExperimental.class)
public class HandProjectionTest {
    @Test
    public void cropRotationMirroringAndPreviewSizesMapToExpectedPixels() {
        int[] rotations = {0, 90, 180, 270};
        float[][] bufferPoints = {{160, 360}, {480, 360}, {480, 120}, {160, 120}};
        RectF crop = new RectF(80, 60, 560, 420);
        Size viewport = new Size(480, 360);
        Matrix sourceMatrix = new Matrix();
        sourceMatrix.setRectToRect(new RectF(-1, -1, 1, 1), crop, Matrix.ScaleToFit.FILL);
        OutputTransform source = new OutputTransform(sourceMatrix, viewport);
        HandTrackingResult.Hand hand = new HandTrackingResult.Hand(Collections.nCopies(21,
                new HandTrackingResult.Landmark(.25f, .75f, 0)), "Right", 1);
        for (int index = 0; index < rotations.length; index++) {
            for (int[] size : new int[][]{{320, 240}, {800, 200}, {300, 600}}) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    for (boolean fitted : new boolean[]{false, true}) {
                        float scale = fitted ? Math.min(size[0] / crop.width(), size[1] / crop.height())
                                : Math.max(size[0] / crop.width(), size[1] / crop.height());
                        float dx = (size[0] - crop.width() * scale) / 2 - crop.left * scale;
                        float dy = (size[1] - crop.height() * scale) / 2 - crop.top * scale;
                        Matrix bufferToView = new Matrix();
                        bufferToView.setValues(new float[]{scale, 0, dx, 0, scale, dy, 0, 0, 1});
                        if (mirrored) { bufferToView.postScale(-1, 1, size[0] / 2f, size[1] / 2f); }
                        Matrix targetMatrix = new Matrix();
                        targetMatrix.setConcat(bufferToView, sourceMatrix);
                        HandTrackingResult result = new HandTrackingResult(1, 640, 480, rotations[index],
                                source, Collections.singletonList(hand));
                        float[] actual = HandLandmarkOverlay.project(result, hand, new OutputTransform(targetMatrix, viewport));
                        float expectedX = bufferPoints[index][0] * scale + dx;
                        if (mirrored) { expectedX = size[0] - expectedX; }
                        assertEquals(expectedX, actual[0], .01f);
                        assertEquals(bufferPoints[index][1] * scale + dy, actual[1], .01f);
                        RectF bounds = new RectF(crop);
                        new CoordinateTransform(source, new OutputTransform(targetMatrix, viewport)).mapRect(bounds);
                        assertEquals((size[0] - crop.width() * scale) / 2f, bounds.left, .01f);
                        assertEquals((size[1] - crop.height() * scale) / 2f, bounds.top, .01f);
                    }
                }
            }
        }
    }
}
