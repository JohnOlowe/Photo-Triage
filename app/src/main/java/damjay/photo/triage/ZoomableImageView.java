package damjay.photo.triage;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Pinch-zoom + pan ImageView that starts as fitCenter and allows scrolling when zoomed.
 * Works inside ViewPager2: requests parent disallow intercept when zoomed/panning.
 */
public class ZoomableImageView extends AppCompatImageView {

    private final Matrix baseMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final RectF displayRect = new RectF();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float minScale = 1f;
    private float maxScale = 4f;
    private boolean isScaling = false;

    private float lastX, lastY;
    private int lastPointerCount = 0;
    private boolean isDragging = false;
    private final float touchSlop = 8f;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context ctx) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleListener());
        gestureDetector = new GestureDetector(ctx, new GestureListener());
        gestureDetector.setOnDoubleTapListener(new GestureListener());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBaseMatrix();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        // post to ensure size known
        post(this::updateBaseMatrix);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        post(this::updateBaseMatrix);
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        post(this::updateBaseMatrix);
    }

    public void resetZoom() {
        suppMatrix.reset();
        updateDrawMatrix();
    }

    private void updateBaseMatrix() {
        Drawable d = getDrawable();
        if (d == null) return;
        int vw = getWidth() - getPaddingLeft() - getPaddingRight();
        int vh = getHeight() - getPaddingTop() - getPaddingBottom();
        if (vw <= 0 || vh <= 0) return;
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        baseMatrix.reset();
        float scale = Math.min((float) vw / dw, (float) vh / dh);
        float dx = (vw - dw * scale) * 0.5f;
        float dy = (vh - dh * scale) * 0.5f;
        baseMatrix.setScale(scale, scale);
        baseMatrix.postTranslate(dx + getPaddingLeft(), dy + getPaddingTop());

        // reset supp
        suppMatrix.reset();
        updateDrawMatrix();
    }

    private void updateDrawMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);
        setImageMatrix(drawMatrix);
    }

    private float getScale() {
        suppMatrix.getValues(matrixValues);
        // supp scale is at [0] and [4]; combined with base scale but we want supp scale
        float sx = matrixValues[Matrix.MSCALE_X];
        // base scale is baked into baseMatrix, supp scale is independent
        return sx;
    }

    private RectF getDisplayRect(Matrix m) {
        Drawable d = getDrawable();
        if (d == null) return null;
        displayRect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        m.mapRect(displayRect);
        return displayRect;
    }

    private void checkMatrixBounds() {
        RectF rect = getDisplayRect(drawMatrix);
        if (rect == null) return;
        float vw = getWidth();
        float vh = getHeight();
        float deltaX = 0, deltaY = 0;

        if (rect.width() <= vw) {
            deltaX = (vw - rect.width()) / 2f - rect.left;
        } else {
            if (rect.left > 0) deltaX = -rect.left;
            else if (rect.right < vw) deltaX = vw - rect.right;
        }
        if (rect.height() <= vh) {
            deltaY = (vh - rect.height()) / 2f - rect.top;
        } else {
            if (rect.top > 0) deltaY = -rect.top;
            else if (rect.bottom < vh) deltaY = vh - rect.bottom;
        }
        if (deltaX != 0 || deltaY != 0) {
            suppMatrix.postTranslate(deltaX, deltaY);
            updateDrawMatrix();
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isScaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float current = getScale();
            float target = current * scaleFactor;
            // clamp
            if (target < minScale) scaleFactor = minScale / current;
            else if (target > maxScale) scaleFactor = maxScale / current;

            suppMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            updateDrawMatrix();
            checkMatrixBounds();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isScaling = false;
            checkMatrixBounds();
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float current = getScale();
            float target = current < 2f ? 2.5f : 1f;
            float scale = target / current;
            suppMatrix.postScale(scale, scale, e.getX(), e.getY());
            updateDrawMatrix();
            checkMatrixBounds();
            // if zoomed in, keep it, else reset
            if (target == 1f) {
                // ensure centered
                checkMatrixBounds();
            }
            // animate could be added, but instant is fine
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            performClick();
            return super.onSingleTapConfirmed(e);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let detectors handle
        scaleDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                lastY = ev.getY();
                lastPointerCount = ev.getPointerCount();
                isDragging = false;
                // if zoomed, request parent not to intercept (for ViewPager2)
                if (getScale() > 1f) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isScaling) break;
                float x = ev.getX();
                float y = ev.getY();
                float dx = x - lastX;
                float dy = y - lastY;

                if (!isDragging) {
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < touchSlop) break;
                    isDragging = true;
                }

                if (getScale() > 1f || isDragging) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    suppMatrix.postTranslate(dx, dy);
                    updateDrawMatrix();
                    checkMatrixBounds();
                }
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getScale() <= 1.01f) {
                    // reset to centered if not zoomed
                    checkMatrixBounds();
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    checkMatrixBounds();
                }
                isDragging = false;
                lastPointerCount = 0;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                lastX = ev.getX();
                lastY = ev.getY();
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
