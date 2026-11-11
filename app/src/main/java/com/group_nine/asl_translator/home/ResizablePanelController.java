package com.group_nine.asl_translator.home;

import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.group_nine.asl_translator.R;

/**
 * Controls the split inside Home between the top camera panel and bottom translation panel.
 * Hides the camera panel completely when dragged all the way up, and stays bounded above the bottom bar.
 */
public final class ResizablePanelController {
    public static final float DEFAULT_SPLIT = 0.50f;
    private static final int SNAP_CLOSE_DP = 16;
    private static final int MIN_TRANSLATION_DP = 64;

    private final View root;
    private final View space;
    private final View camera;
    private final View translation;
    private final View divider;
    private final View previewIcon;
    private final View cameraStatus;
    private final GestureDetector gestures;
    private final View.OnLayoutChangeListener layoutListener;
    private final Runnable initialLayout = this::apply;
    private final int touchSlop;
    private final int snapClosePx;
    private final int minTranslationPx;
    private final Runnable onCameraCollapsed;
    private boolean lastCameraVisible = true;
    private boolean cameraPreviewActive;

    private float split;
    private float downY;
    private int downHeight;
    private int pointerId = -1;
    private boolean dragging;
    private boolean doubleTap;

    public ResizablePanelController(View root, float restoredSplit) {
        this(root, restoredSplit, () -> { });
    }

    public ResizablePanelController(View root, float restoredSplit, Runnable onCameraCollapsed) {
        this.root = root;
        this.onCameraCollapsed = onCameraCollapsed;
        space = root.findViewById(R.id.resizable_space);
        camera = root.findViewById(R.id.camera_panel);
        translation = root.findViewById(R.id.translation_panel);
        divider = root.findViewById(R.id.resize_divider);
        previewIcon = root.findViewById(R.id.preview_icon);
        cameraStatus = root.findViewById(R.id.camera_status);

        float density = root.getResources().getDisplayMetrics().density;
        snapClosePx = Math.round(SNAP_CLOSE_DP * density);
        minTranslationPx = Math.round(MIN_TRANSLATION_DP * density);

        split = Float.isFinite(restoredSplit) ? Math.max(0f, Math.min(1f, restoredSplit)) : DEFAULT_SPLIT;
        touchSlop = ViewConfiguration.get(root.getContext()).getScaledTouchSlop();

        gestures = new GestureDetector(root.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                doubleTap = true;
                reset();
                return true;
            }
        });

        divider.setOnTouchListener(this::onTouch);
        divider.setOnClickListener(view -> reset());
        divider.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                adjust(-0.08f);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                adjust(0.08f);
                return true;
            }
            return false;
        });

        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
                root.getContext().getString(R.string.resize_more_camera),
                (view, args) -> {
                    adjust(0.08f);
                    return true;
                });
        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
                root.getContext().getString(R.string.resize_more_translation),
                (view, args) -> {
                    adjust(-0.08f);
                    return true;
                });
        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                root.getContext().getString(R.string.resize_reset),
                (view, args) -> {
                    reset();
                    return true;
                });

        layoutListener = (view, l, top, r, b, oldL, oldT, oldR, oldB) -> {
            if ((b - top) != (oldB - oldT) || (r - l) != (oldR - oldL)) {
                apply();
            }
        };
        space.addOnLayoutChangeListener(layoutListener);
        root.addOnLayoutChangeListener(layoutListener);
        root.post(initialLayout);
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            doubleTap = false;
            pointerId = event.getPointerId(0);
            downY = event.getRawY();
            downHeight = (camera.getVisibility() == View.GONE) ? 0 : camera.getHeight();
            dragging = false;
            view.getParent().requestDisallowInterceptTouchEvent(true);
        }
        gestures.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (pointerId == -1 || doubleTap) {
                    break;
                }
                float delta = event.getRawY() - downY;
                if (Math.abs(delta) > touchSlop) {
                    dragging = true;
                }
                int available = availableHeight();
                if (dragging && available > 0) {
                    int maxCamera = Math.max(0, available - Math.min(minTranslationPx, available / 4));
                    int rawHeight = Math.round(downHeight + delta);

                    // Snap to 0 (completely hidden) when dragged near the very top
                    int clampedHeight = (rawHeight <= snapClosePx)
                            ? 0
                            : Math.min(maxCamera, rawHeight);

                    split = clampedHeight / (float) available;
                    apply();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) {
                    finishTouch(view);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishTouch(view);
                break;

            default:
                break;
        }
        return true;
    }

    private void finishTouch(View view) {
        pointerId = -1;
        dragging = false;
        view.getParent().requestDisallowInterceptTouchEvent(false);
    }

    private int availableHeight() {
        return Math.max(0, space.getHeight() - space.getPaddingTop() - space.getPaddingBottom() - divider.getHeight());
    }

    private void apply() {
        if (space.getWidth() <= 0 || root.getHeight() <= 0 || divider.getHeight() <= 0) return;
        View header = root.findViewById(R.id.editor_header);
        header.measure(View.MeasureSpec.makeMeasureSpec(space.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int editorMinimum = minTranslationPx + header.getMeasuredHeight() + 1;
        View controls = root.findViewById(R.id.camera_controls);
        controls.measure(View.MeasureSpec.makeMeasureSpec(space.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int controlsMargin = ((ViewGroup.MarginLayoutParams) controls.getLayoutParams()).topMargin;
        int required = editorMinimum + (split > 0 ? Math.round(112 * root.getResources().getDisplayMetrics().density) : 0)
                + divider.getHeight() + space.getPaddingTop() + space.getPaddingBottom();
        View content = (View) space.getParent();
        int viewport = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int desired = Math.max(required, viewport - space.getTop() - controls.getMeasuredHeight()
                - controlsMargin - content.getPaddingBottom());
        // Bound TextureView's natural size; short windows scroll instead of obscuring text/buttons.
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) space.getLayoutParams();
        if (params.height != desired || params.weight != 0) {
            params.height = desired; params.weight = 0; space.setLayoutParams(params); return;
        }
        int available = availableHeight();
        if (space.getWidth() <= 0 || available <= 0) {
            return;
        }

        int maxCamera = Math.max(0, available - editorMinimum);
        int cameraHeight = Math.max(0, Math.min(maxCamera, Math.round(available * split)));

        if (cameraHeight <= snapClosePx) {
            cameraHeight = 0;
        }

        if (cameraHeight == 0) {
            camera.setVisibility(View.GONE);
        } else {
            if (camera.getVisibility() != View.VISIBLE) {
                camera.setVisibility(View.VISIBLE);
            }
            ViewGroup.LayoutParams camParams = camera.getLayoutParams();
            if (camParams.height != cameraHeight) {
                camParams.height = cameraHeight;
                camera.setLayoutParams(camParams);
            }
        }

        root.findViewById(R.id.camera_details).setVisibility(cameraHeight >= 112 * root.getResources().getDisplayMetrics().density
                ? View.VISIBLE : View.GONE);
        if (cameraStatus != null) {
            cameraStatus.setVisibility(cameraHeight > 120 ? View.VISIBLE : View.GONE);
        }
        if (previewIcon != null) {
            previewIcon.setVisibility(!cameraPreviewActive && cameraHeight > 170 ? View.VISIBLE : View.GONE);
        }

        int cameraPercent = Math.round(cameraHeight * 100f / available);
        ViewCompat.setStateDescription(divider, root.getContext().getString(
                R.string.split_state, 100 - cameraPercent, cameraPercent));
        boolean visible = cameraHeight > 0;
        boolean collapsed = lastCameraVisible && !visible;
        lastCameraVisible = visible;
        if (collapsed) root.post(onCameraCollapsed);
    }

    private void adjust(float amount) {
        split = Math.max(0f, Math.min(1f, split + amount));
        apply();
    }

    public void reset() {
        split = DEFAULT_SPLIT;
        apply();
    }

    public float getSplit() {
        return Math.max(0f, Math.min(1f, split));
    }

    public boolean isCameraVisible() { return camera.getVisibility() == View.VISIBLE; }

    public void showCamera() {
        if (split <= 0 || !isCameraVisible()) { split = DEFAULT_SPLIT; apply(); }
    }

    public void setCameraPreviewActive(boolean active) {
        if (cameraPreviewActive != active) { cameraPreviewActive = active; refresh(); }
    }

    public void refresh() { root.removeCallbacks(initialLayout); root.post(initialLayout); }

    public void dispose() {
        space.removeCallbacks(initialLayout);
        root.removeCallbacks(initialLayout);
        root.removeCallbacks(onCameraCollapsed);
        space.removeOnLayoutChangeListener(layoutListener);
        root.removeOnLayoutChangeListener(layoutListener);
        divider.setOnTouchListener(null);
        divider.setOnKeyListener(null);
        divider.setOnClickListener(null);
    }
}
