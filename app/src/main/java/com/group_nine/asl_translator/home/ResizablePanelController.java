package com.group_nine.asl_translator.home;

import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.group_nine.asl_translator.R;

/**
 * Controls the split inside Home, independently of the stationary app chrome.
 * Stores a ratio rather than pixels. Measured text and controls establish the
 * bounds; exceptionally short windows scroll instead of hiding essential content.
 */
public final class ResizablePanelController {
    public static final float DEFAULT_SPLIT = 0.45f;
    private final View root;
    private final View space;
    private final View translation;
    private final View divider;
    private final GestureDetector gestures;
    private final View.OnLayoutChangeListener layoutListener;
    private final Runnable initialLayout = this::apply;
    private final int touchSlop;
    private float split;
    private float downY;
    private int downHeight;
    private int pointerId = -1;
    private boolean dragging;
    private boolean doubleTap;
    private boolean cameraPreviewActive;


    public ResizablePanelController(View root, float restoredSplit) {
        this.root = root;
        space = root.findViewById(R.id.resizable_space);
        translation = root.findViewById(R.id.translation_panel);
        divider = root.findViewById(R.id.resize_divider);
        split = Float.isFinite(restoredSplit) ? Math.max(0, Math.min(1, restoredSplit)) : DEFAULT_SPLIT;
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
                adjust(-0.05f);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                adjust(0.05f);
                return true;
            }
            return false; // Enter/Space use the normal click-to-reset action.
        });
        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
                root.getContext().getString(R.string.resize_more_translation),
                (view, args) -> {
                    adjust(0.05f);
                    return true;
                });
        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
                root.getContext().getString(R.string.resize_more_camera),
                (view, args) -> {
                    adjust(-0.05f);
                    return true;
                });
        ViewCompat.replaceAccessibilityAction(divider,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                root.getContext().getString(R.string.resize_reset),
                (view, args) -> {
                    reset();
                    return true;
                });
        layoutListener = (view, l, top, r, b, oldL, oldT, oldR, oldB) -> apply();
        space.addOnLayoutChangeListener(layoutListener);
        root.addOnLayoutChangeListener(layoutListener);
        space.post(initialLayout);
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            doubleTap = false;
            pointerId = event.getPointerId(0);
            downY = event.getRawY();
            downHeight = translation.getHeight();
            dragging = false;
            view.getParent().requestDisallowInterceptTouchEvent(true);
        }
        gestures.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (pointerId == -1 || doubleTap) {
                    break;
                }
                int index = event.findPointerIndex(pointerId);
                if (index < 0) {
                    break;
                }
                float rawY = event.getY(index) + event.getRawY() - event.getY();
                float delta = rawY - downY;
                if (Math.abs(delta) > touchSlop) {
                    dragging = true;
                }
                if (dragging && availableHeight() > 0) {
                    split = (downHeight + delta) / availableHeight();
                    apply();
                    // Persist the actual bounded split, including at drag extremes.
                    split = translation.getLayoutParams().height / (float) availableHeight();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) {
                    finishTouch(view);
                }
                break;
            case MotionEvent.ACTION_UP:
                // Touch uses double-tap to reset; keyboard/accessibility use click.
                finishTouch(view);
                break;
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
        return space.getHeight() - space.getPaddingTop() - space.getPaddingBottom() - divider.getHeight();
    }

    private int textMinimum(int id, boolean fullText) {
        TextView text = root.findViewById(id);
        if (fullText) {
            text.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, space.getWidth()), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            return text.getMeasuredHeight();
        }
        // Keep two lines in both translation areas; longer text scrolls locally.
        return 2 * text.getLineHeight() + text.getPaddingTop() + text.getPaddingBottom();
    }

    private void apply() {
        if (space.getWidth() <= 0 || divider.getHeight() <= 0 || root.getHeight() <= 0) {
            return;
        }
        int translationMin = textMinimum(R.id.translation_input, false)
                + textMinimum(R.id.translation_output, false) + 2;
        View controls = root.findViewById(R.id.camera_controls);
        controls.measure(View.MeasureSpec.makeMeasureSpec(space.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        View preview = root.findViewById(R.id.camera_preview);
        View placeholder = root.findViewById(R.id.camera_placeholder);
        int previewMin = Math.max(preview.getMinimumHeight(),
                textMinimum(R.id.camera_status, true) + placeholder.getPaddingTop()
                        + placeholder.getPaddingBottom());
        View switchCamera = root.findViewById(R.id.switch_camera);
        switchCamera.measure(View.MeasureSpec.makeMeasureSpec(space.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int cameraMin = controls.getMeasuredHeight() + switchCamera.getMeasuredHeight()
                + textMinimum(R.id.live_caption, true) + previewMin;
        int required = translationMin + cameraMin + divider.getHeight()
                + space.getPaddingTop() + space.getPaddingBottom();
        if (space.getMinimumHeight() != required) {
            space.setMinimumHeight(required);
        }
        // TextureView can report a large natural size inside an unconstrained scroll child.
        // Bound the split area to the viewport, expanding only for measured essential controls.
        ViewGroup content = (ViewGroup) space.getParent();
        int viewport = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int desiredHeight = Math.max(required, viewport - space.getTop() - content.getPaddingBottom());
        LinearLayout.LayoutParams spaceParams = (LinearLayout.LayoutParams) space.getLayoutParams();
        if (spaceParams.height != desiredHeight || spaceParams.weight != 0) {
            spaceParams.height = desiredHeight;
            spaceParams.weight = 0;
            space.setLayoutParams(spaceParams);
            return;
        }
        int available = availableHeight();
        if (available < translationMin + cameraMin) {
            return; // A following layout expands scroll content.
        }
        int height = Math.max(translationMin, Math.min(available - cameraMin, Math.round(available * split)));
        if (translation.getLayoutParams().height != height) {
            ViewGroup.LayoutParams params = translation.getLayoutParams();
            params.height = height;
            translation.setLayoutParams(params);
        }
        int iconHeight = root.getResources().getDimensionPixelSize(R.dimen.icon_size);
        root.findViewById(R.id.preview_icon).setVisibility(
                !cameraPreviewActive && available - height - cameraMin >= iconHeight
                        ? View.VISIBLE : View.GONE);
        int percent = Math.round(height * 100f / available);
        ViewCompat.setStateDescription(divider, root.getContext().getString(
                R.string.split_state, percent, 100 - percent));
    }

    private void adjust(float amount) {
        if (availableHeight() > 0) {
            split = translation.getHeight() / (float) availableHeight();
        }
        split = Math.max(0, Math.min(1, split + amount));
        apply();
    }

    public void reset() {
        split = DEFAULT_SPLIT;
        apply();
    }

    public void setCameraPreviewActive(boolean active) {
        cameraPreviewActive = active;
        apply();
    }

    public float getSplit() {
        return Math.max(0, Math.min(1, split));
    }

    public void dispose() {
        space.removeCallbacks(initialLayout);
        space.removeOnLayoutChangeListener(layoutListener);
        root.removeOnLayoutChangeListener(layoutListener);
        divider.setOnTouchListener(null);
        divider.setOnKeyListener(null);
        divider.setOnClickListener(null);
    }
}
