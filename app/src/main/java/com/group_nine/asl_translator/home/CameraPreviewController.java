package com.group_nine.asl_translator.home;

import android.content.Context;
import android.view.Surface;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.StringRes;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.Preview;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;
import com.group_nine.asl_translator.R;

import java.util.concurrent.ExecutionException;

/** Owns the camera preview and its session-scoped hand-analysis pipeline. */
final class CameraPreviewController {
    enum State { IDLE, STARTING, ACTIVE, UNAVAILABLE, ERROR }

    interface Listener {
        void onStateChanged(State state, @StringRes int message, int lensFacing, boolean canSwitch);
    }

    private final Context context;
    private final LifecycleOwner owner;
    private final PreviewView surface;
    private final Listener listener;
    private final HandTracker.Listener trackingListener;
    private java.util.function.Consumer<HandTrackingResult> frameObserver;
    private ProcessCameraProvider provider;
    private Preview preview;
    private ImageAnalysis analysis;
    private HandTracker tracker;
    private CameraInfo cameraInfo;
    private Observer<CameraState> cameraObserver;
    private Observer<PreviewView.StreamState> streamObserver;
    private Runnable timeout;
    private int generation;
    private int lensFacing;
    private boolean bothCameras;
    private boolean running;
    private boolean disposed;
    private State state = State.IDLE;

    CameraPreviewController(Context context, LifecycleOwner owner, PreviewView surface,
                            int lensFacing, Listener listener, HandTracker.Listener trackingListener) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.surface = surface;
        this.lensFacing = lensFacing;
        this.listener = listener;
        this.trackingListener = trackingListener;
    }

    void start() {
        if (disposed || running || !owner.getLifecycle().getCurrentState()
                .isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        release();
        running = true;
        int request = generation;
        emit(State.STARTING, R.string.camera_starting);
        timeout = () -> {
            if (isCurrent(request)) {
                fail(State.ERROR, R.string.camera_start_failed);
            }
        };
        surface.postDelayed(timeout, 15000);
        try {
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
            future.addListener(() -> {
                if (!isCurrent(request)) {
                    return;
                }
                try {
                    provider = future.get();
                    bind(request);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(State.ERROR, R.string.camera_start_failed);
                } catch (ExecutionException | RuntimeException exception) {
                    fail(State.ERROR, exception instanceof SecurityException
                            ? R.string.camera_permission_denied : R.string.camera_start_failed);
                }
            }, ContextCompat.getMainExecutor(context));
        } catch (RuntimeException exception) {
            fail(State.ERROR, R.string.camera_start_failed);
        }
    }

    /** Optional per-analysis observer; it runs on the existing tracking worker. */
    void setFrameObserver(java.util.function.Consumer<HandTrackingResult> observer) {
        if (running) throw new IllegalStateException("Configure observer before starting the camera");
        frameObserver = observer;
    }

    private void bind(int request) {
        try {
            boolean front = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
            boolean rear = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
            bothCameras = front && rear;
            if (!front && !rear) {
                fail(State.UNAVAILABLE, R.string.camera_no_hardware);
                return;
            }
            if (lensFacing == CameraSelector.LENS_FACING_FRONT && !front) {
                lensFacing = CameraSelector.LENS_FACING_BACK;
            } else if (lensFacing == CameraSelector.LENS_FACING_BACK && !rear) {
                lensFacing = CameraSelector.LENS_FACING_FRONT;
            }
            emit(State.STARTING, R.string.camera_starting);
            if (!surface.isAttachedToWindow() || !surface.isShown() || surface.getWidth() == 0 || surface.getHeight() == 0) {
                surface.postOnAnimation(() -> {
                    if (isCurrent(request)) {
                        bind(request);
                    }
                });
                return;
            }
            int rotation = surface.getDisplay() == null ? Surface.ROTATION_0
                    : surface.getDisplay().getRotation();
            // FIT preserves the maximum shared sensor area. Never freeze a crop from the
            // panel's startup dimensions: the panel can change shape while streaming.
            ViewPort viewport = new ViewPort.Builder(new Rational(4, 3), rotation)
                    .setScaleType(ViewPort.FIT).build();
            preview = new Preview.Builder().setTargetRotation(rotation).build();
            preview.setSurfaceProvider(surface.getSurfaceProvider());
            CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            trackingListener.onTrackingChanged(HandTracker.State.INITIALIZING, null);
            tracker = new HandTracker(context, (trackingState, result) -> {
                if (isCurrent(request)) {
                    if (trackingState == HandTracker.State.UNAVAILABLE && analysis != null) {
                        analysis.clearAnalyzer();
                    }
                    trackingListener.onTrackingChanged(trackingState, result);
                }
            }, frameObserver);
            analysis = new ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(new ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .build();
            analysis.setAnalyzer(tracker.executor(), tracker);
            Camera camera;
            try {
                camera = provider.bindToLifecycle(owner, selector, new UseCaseGroup.Builder()
                        .setViewPort(viewport).addUseCase(preview).addUseCase(analysis).build());
            } catch (SecurityException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                // Some devices cannot bind both streams; retain a usable preview.
                analysis.clearAnalyzer();
                provider.unbind(preview, analysis);
                tracker.close();
                tracker = null;
                analysis = null;
                camera = provider.bindToLifecycle(owner, selector, preview);
                trackingListener.onTrackingChanged(HandTracker.State.UNAVAILABLE, null);
            }
            cameraInfo = camera.getCameraInfo();
            cameraObserver = cameraState -> {
                if (isCurrent(request) && cameraState.getError() != null) {
                    int code = cameraState.getError().getCode();
                    int message = code == CameraState.ERROR_CAMERA_DISABLED
                            ? R.string.camera_disabled : R.string.camera_busy_or_failed;
                    fail(State.ERROR, message);
                }
            };
            cameraInfo.getCameraState().observe(owner, cameraObserver);
            if (!isCurrent(request)) {
                return;
            }
            streamObserver = stream -> {
                if (isCurrent(request) && stream == PreviewView.StreamState.STREAMING) {
                    clearTimeout();
                    emit(State.ACTIVE, lensFacing == CameraSelector.LENS_FACING_FRONT
                            ? R.string.camera_front_active : R.string.camera_rear_active);
                }
            };
            surface.getPreviewStreamState().observe(owner, streamObserver);
        } catch (SecurityException exception) {
            fail(State.ERROR, R.string.camera_permission_denied);
        } catch (Exception exception) {
            fail(State.ERROR, R.string.camera_start_failed);
        }
    }

    void switchCamera() {
        if (state != State.ACTIVE || !bothCameras || disposed) {
            return;
        }
        lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        release();
        start();
    }

    void stop() {
        release();
        if (!disposed) {
            emit(State.IDLE, R.string.camera_placeholder);
        }
    }

    void dispose() {
        release();
        disposed = true;
    }

    private boolean isCurrent(int request) {
        return !disposed && running && generation == request
                && owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }

    private void fail(State failure, @StringRes int message) {
        release();
        emit(failure, message);
    }

    private void release() {
        generation++;
        running = false;
        clearTimeout();
        if (analysis != null) {
            analysis.clearAnalyzer();
        }
        if (tracker != null) {
            tracker.close();
            tracker = null;
        }
        trackingListener.onTrackingChanged(HandTracker.State.IDLE, null);
        if (streamObserver != null) {
            surface.getPreviewStreamState().removeObserver(streamObserver);
            streamObserver = null;
        }
        if (cameraInfo != null && cameraObserver != null) {
            cameraInfo.getCameraState().removeObserver(cameraObserver);
        }
        cameraInfo = null;
        cameraObserver = null;
        if (provider != null && preview != null) {
            if (analysis != null) {
                provider.unbind(preview, analysis);
            } else {
                provider.unbind(preview);
            }
            preview.setSurfaceProvider(null);
        }
        preview = null;
        analysis = null;
    }

    private void clearTimeout() {
        if (timeout != null) {
            surface.removeCallbacks(timeout);
            timeout = null;
        }
    }

    private void emit(State next, @StringRes int message) {
        state = next;
        listener.onStateChanged(next, message, lensFacing, bothCameras);
    }
}
