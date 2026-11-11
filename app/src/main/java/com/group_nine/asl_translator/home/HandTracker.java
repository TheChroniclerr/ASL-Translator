package com.group_nine.asl_translator.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.view.TransformExperimental;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.transform.ImageProxyTransformFactory;
import androidx.camera.view.transform.OutputTransform;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.group_nine.asl_translator.recognition.ForestModel;
import com.group_nine.asl_translator.recognition.VowelClassifier;
import com.group_nine.asl_translator.recognition.VowelRecognitionResult;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** A camera session owns one worker. Model creation, inference, and closing stay on that worker. */
@OptIn(markerClass = TransformExperimental.class)
final class HandTracker implements ImageAnalysis.Analyzer, AutoCloseable {
    enum State { IDLE, INITIALIZING, NO_HANDS, ONE_HAND, TWO_HANDS, UNAVAILABLE }

    interface Listener {
        void onTrackingChanged(State state, HandTrackingResult result);
    }

    interface Engine extends AutoCloseable {
        /** May consume the bitmap; the caller must not reuse its pixels after this call. */
        List<HandTrackingResult.Hand> detect(Bitmap uprightImage, long timestampMs);
        @Override void close();
    }

    interface EngineFactory {
        Engine create(Context context) throws Exception;
    }

    // Package-private injection seam for deterministic instrumentation tests; no user-facing mode.
    static volatile EngineFactory engineFactory = MediaPipeEngine::new;

    interface ClassifierFactory { VowelClassifier create(Context context) throws Exception; }
    static volatile ClassifierFactory classifierFactory = HandTracker::loadClassifier;

    static VowelClassifier loadClassifier(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("vowel_forest.bin")) {
            return new VowelClassifier(ForestModel.read(input));
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Executor main;
    private final Listener listener;
    private final java.util.function.Consumer<HandTrackingResult> frameObserver;
    private final ImageProxyTransformFactory transformFactory = new ImageProxyTransformFactory();
    private final AtomicReference<HandTrackingResult> pendingResult = new AtomicReference<>();
    private final AtomicBoolean deliveryPosted = new AtomicBoolean();
    private volatile boolean closed;
    private volatile boolean failed;
    private Engine engine;
    private VowelClassifier classifier;
    private boolean recognitionFailed;
    private long lastTimestamp = -1;

    HandTracker(Context context, Listener listener) {
        this(context, listener, null);
    }

    HandTracker(Context context, Listener listener, java.util.function.Consumer<HandTrackingResult> frameObserver) {
        this.listener = listener;
        this.frameObserver = frameObserver;
        main = ContextCompat.getMainExecutor(context);
        Context appContext = context.getApplicationContext();
        EngineFactory factory = engineFactory;
        ClassifierFactory vowelFactory = classifierFactory;
        worker.execute(() -> {
            if (closed) {
                return;
            }
            try {
                engine = factory.create(appContext);
                if (!closed) {
                    try { classifier = vowelFactory.create(appContext); }
                    catch (Exception | LinkageError exception) { failRecognition(exception); }
                }
            } catch (Exception | LinkageError exception) {
                fail(exception);
            }
        });
    }

    Executor executor() {
        return worker;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        Bitmap buffer = null;
        Bitmap upright = null;
        try {
            if (closed || failed || engine == null) {
                return;
            }
            int rotation = image.getImageInfo().getRotationDegrees();
            int width = image.getWidth();
            int height = image.getHeight();
            OutputTransform source = transformFactory.getOutputTransform(image);
            buffer = image.toBitmap();
            if (rotation == 0) {
                upright = buffer;
            } else {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                upright = Bitmap.createBitmap(buffer, 0, 0, width, height, matrix, false);
            }
            long timestamp = Math.max(lastTimestamp + 1, image.getImageInfo().getTimestamp() / 1000000);
            lastTimestamp = timestamp;
            List<HandTrackingResult.Hand> hands = engine.detect(upright, timestamp);
            if (!closed) {
                VowelRecognitionResult recognition = recognize(hands, rotation % 180 == 0 ? width : height,
                        rotation % 180 == 0 ? height : width, timestamp);
                HandTrackingResult result = new HandTrackingResult(timestamp, width, height, rotation, source, hands, recognition,
                        classifier == null ? null : classifier.lastPrediction());
                if (frameObserver != null) frameObserver.accept(result);
                pendingResult.set(result);
                postDelivery();
            }
        } catch (Exception | LinkageError exception) {
            fail(exception);
        } finally {
            if (upright != null && upright != buffer && !upright.isRecycled()) {
                upright.recycle();
            }
            if (buffer != null && !buffer.isRecycled()) {
                buffer.recycle();
            }
            image.close();
        }
    }

    private VowelRecognitionResult recognize(List<HandTrackingResult.Hand> hands, int width, int height, long timestamp) {
        if (!recognitionFailed && classifier != null) {
            try {
                List<float[]> points = new ArrayList<>();
                for (HandTrackingResult.Hand hand : hands) {
                    float[] xyz = new float[63];
                    for (int i = 0; i < 21; i++) {
                        HandTrackingResult.Landmark p = hand.landmarks.get(i);
                        xyz[i * 3] = p.x; xyz[i * 3 + 1] = p.y; xyz[i * 3 + 2] = p.z;
                    }
                    points.add(xyz);
                }
                return classifier.classify(points, width, height, timestamp);
            } catch (RuntimeException | LinkageError exception) { failRecognition(exception); }
        }
        return VowelRecognitionResult.status(timestamp, VowelRecognitionResult.State.UNAVAILABLE);
    }

    private void failRecognition(Throwable exception) {
        recognitionFailed = true;
        classifier = null;
        Log.w("VowelRecognition", "Vowel recognition unavailable", exception);
    }

    private void postDelivery() {
        if (!deliveryPosted.compareAndSet(false, true)) {
            return;
        }
        main.execute(() -> {
            HandTrackingResult result = pendingResult.getAndSet(null);
            deliveryPosted.set(false);
            if (!closed && !failed && result != null) {
                int count = result.hands.size();
                listener.onTrackingChanged(count == 0 ? State.NO_HANDS
                        : count == 1 ? State.ONE_HAND : State.TWO_HANDS, result);
            }
            if (pendingResult.get() != null && !closed) {
                postDelivery();
            }
        });
    }

    private void fail(Throwable exception) {
        failed = true;
        pendingResult.set(null);
        Log.w("HandTracker", "Hand tracking unavailable", exception);
        closeEngine();
        main.execute(() -> {
            if (!closed) {
                listener.onTrackingChanged(State.UNAVAILABLE, null);
            }
        });
    }

    private void closeEngine() {
        if (classifier != null) { classifier.reset(); classifier = null; }
        if (engine != null) {
            try {
                engine.close();
            } catch (RuntimeException exception) {
                Log.w("HandTracker", "Could not close hand tracker", exception);
            }
            engine = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pendingResult.set(null);
        worker.execute(this::closeEngine);
        worker.shutdown();
    }

    /** Adapter around the real bundled model, also exercised directly by device fixture tests. */
    static final class MediaPipeEngine implements Engine {
        private final HandLandmarker landmarker;

        MediaPipeEngine(Context context) {
            BaseOptions base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.CPU).build();
            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.VIDEO).setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f).setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f).build();
            landmarker = HandLandmarker.createFromOptions(context, options);
        }

        @Override
        public List<HandTrackingResult.Hand> detect(Bitmap uprightImage, long timestampMs) {
            if (uprightImage.isRecycled()) {
                throw new IllegalArgumentException("Input bitmap was already released");
            }
            MPImage input = new BitmapImageBuilder(uprightImage).build();
            try {
                HandLandmarkerResult result = landmarker.detectForVideo(input, timestampMs);
                List<HandTrackingResult.Hand> hands = new ArrayList<>();
                for (int i = 0; i < result.landmarks().size(); i++) {
                    List<HandTrackingResult.Landmark> landmarks = new ArrayList<>();
                    for (NormalizedLandmark point : result.landmarks().get(i)) {
                        landmarks.add(new HandTrackingResult.Landmark(point.x(), point.y(), point.z()));
                    }
                    Category category = result.handedness().get(i).get(0);
                    hands.add(new HandTrackingResult.Hand(landmarks, category.categoryName(), category.score()));
                }
                return hands;
            } finally {
                input.close();
            }
        }

        @Override
        public void close() {
            landmarker.close();
        }
    }
}
