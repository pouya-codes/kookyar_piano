package com.PouyaApp.kookyarpiano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

/**
 * A scrollable 88-key piano keyboard (A0 .. C8).
 *
 * Tap a key to select it; drag or fling to scroll. The selected key is drawn
 * in the accent colour and the key the tuner is currently hearing in green.
 * Allocation-free drawing: paints, rects and the key gradient are created once.
 */
public class PianoKeyboardView extends View {

    public interface OnKeyListener {
        void onKeySelected(int midi);
    }

    public static final int FIRST_MIDI = 21;   // A0
    public static final int LAST_MIDI = 108;   // C8
    public static final int KEY_COUNT = LAST_MIDI - FIRST_MIDI + 1;
    private static final int WHITE_KEY_COUNT = 52;
    private static final boolean[] IS_BLACK = {false, true, false, true, false, false, true, false, true, false, true, false};
    private static final String[] NOTE_LETTERS = {"C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"};

    private static final int COLOR_WHITE_TOP = 0xFFF4F2F8;
    private static final int COLOR_WHITE_BOTTOM = 0xFFDCD9E4;
    private static final int COLOR_BLACK_TOP = 0xFF2B2836;
    private static final int COLOR_BLACK_BOTTOM = 0xFF0F0E14;
    private static final int COLOR_GAP = 0xFF35323F;
    private static final int COLOR_SELECTED = 0xFFFFBB33;
    private static final int COLOR_DETECTED = 0xFF4CD964;
    private static final int COLOR_LABEL = 0xFF5B5869;
    private static final int COLOR_LABEL_ON_ACCENT = 0xFF2A1F00;

    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF keyRect = new RectF();

    private float density;
    private float whiteW, blackW, blackH, cornerW, cornerB;
    private int contentWidth;
    private int selectedMidi = -1;
    private int detectedMidi = -1;

    private final OverScroller scroller;
    private final GestureDetector gestures;
    @Nullable
    private OnKeyListener listener;

    // Index of the white key immediately at/left of each midi note, and the
    // absolute x offset of every key, both computed once.
    private final float[] keyLeft = new float[KEY_COUNT];

    public PianoKeyboardView(Context context) {
        this(context, null);
    }

    public PianoKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (!scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                scrollBy(clampDx(dx), 0);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                scroller.fling(getScrollX(), 0, (int) -vx, 0, 0, maxScroll(), 0, 0);
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int midi = hitTest(e.getX() + getScrollX(), e.getY());
                if (midi >= 0) {
                    setSelectedMidi(midi);
                    performClick();
                    if (listener != null) {
                        listener.onKeySelected(midi);
                    }
                }
                return true;
            }
        });
        gapPaint.setColor(COLOR_GAP);
        gapPaint.setStyle(Paint.Style.STROKE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setClickable(true);
    }

    public void setOnKeyListener(@Nullable OnKeyListener listener) {
        this.listener = listener;
    }

    public int getSelectedMidi() {
        return selectedMidi;
    }

    public void setSelectedMidi(int midi) {
        if (selectedMidi != midi) {
            selectedMidi = midi;
            invalidate();
        }
    }

    /** Key the tuner is hearing right now, or -1 for none. */
    public void setDetectedMidi(int midi) {
        if (detectedMidi != midi) {
            detectedMidi = midi;
            invalidate();
        }
    }

    /** Scrolls so the given note sits in the middle of the view. */
    public void scrollToMidi(int midi, boolean animate) {
        if (contentWidth == 0 || midi < FIRST_MIDI || midi > LAST_MIDI) {
            return;
        }
        float center = keyLeft[midi - FIRST_MIDI] + (isBlack(midi) ? blackW : whiteW) / 2f;
        int target = Math.max(0, Math.min(maxScroll(), Math.round(center - getWidth() / 2f)));
        scroller.forceFinished(true);
        if (animate) {
            scroller.startScroll(getScrollX(), 0, target - getScrollX(), 0, 350);
            postInvalidateOnAnimation();
        } else {
            scrollTo(target, 0);
        }
    }

    public static boolean isBlack(int midi) {
        return IS_BLACK[((midi % 12) + 12) % 12];
    }

    /** Scientific pitch name, e.g. "C4" or "F♯3". */
    public static String scientificName(int midi) {
        return NOTE_LETTERS[((midi % 12) + 12) % 12] + (midi / 12 - 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        whiteW = dp(44);
        blackW = whiteW * 0.62f;
        blackH = h * 0.62f;
        cornerW = dp(5);
        cornerB = dp(4);
        contentWidth = Math.round(whiteW * WHITE_KEY_COUNT);

        whitePaint.setShader(new LinearGradient(0, 0, 0, h, COLOR_WHITE_TOP, COLOR_WHITE_BOTTOM, Shader.TileMode.CLAMP));
        blackPaint.setShader(new LinearGradient(0, 0, 0, blackH, COLOR_BLACK_TOP, COLOR_BLACK_BOTTOM, Shader.TileMode.CLAMP));
        gapPaint.setStrokeWidth(dp(1));
        labelPaint.setTextSize(dp(11));

        int whiteIndex = 0;
        for (int i = 0; i < KEY_COUNT; i++) {
            int midi = FIRST_MIDI + i;
            if (isBlack(midi)) {
                // Black keys sit on the boundary between the previous and next white key.
                keyLeft[i] = whiteIndex * whiteW - blackW / 2f;
            } else {
                keyLeft[i] = whiteIndex * whiteW;
                whiteIndex++;
            }
        }
        if (getScrollX() > maxScroll()) {
            scrollTo(maxScroll(), 0);
        }
    }

    private int maxScroll() {
        return Math.max(0, contentWidth - getWidth());
    }

    private int clampDx(float dx) {
        int target = Math.round(getScrollX() + dx);
        target = Math.max(0, Math.min(maxScroll(), target));
        return target - getScrollX();
    }

    private int hitTest(float x, float y) {
        // Black keys are on top, so test them first.
        for (int i = 0; i < KEY_COUNT; i++) {
            int midi = FIRST_MIDI + i;
            if (isBlack(midi) && y <= blackH && x >= keyLeft[i] && x < keyLeft[i] + blackW) {
                return midi;
            }
        }
        for (int i = 0; i < KEY_COUNT; i++) {
            int midi = FIRST_MIDI + i;
            if (!isBlack(midi) && x >= keyLeft[i] && x < keyLeft[i] + whiteW) {
                return midi;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int h = getHeight();
        float left = getScrollX();
        float right = left + getWidth();

        // White keys.
        for (int i = 0; i < KEY_COUNT; i++) {
            int midi = FIRST_MIDI + i;
            if (isBlack(midi)) {
                continue;
            }
            float x = keyLeft[i];
            if (x + whiteW < left || x > right) {
                continue;
            }
            keyRect.set(x + dp(1), 0, x + whiteW - dp(1), h);
            Paint p = whitePaint;
            if (midi == selectedMidi) {
                fillPaint.setColor(COLOR_SELECTED);
                p = fillPaint;
            } else if (midi == detectedMidi) {
                fillPaint.setColor(COLOR_DETECTED);
                p = fillPaint;
            }
            canvas.drawRoundRect(keyRect, cornerW, cornerW, p);
            canvas.drawRoundRect(keyRect, cornerW, cornerW, gapPaint);

            int pc = midi % 12;
            if (pc == 0 || midi == selectedMidi || midi == detectedMidi) {
                labelPaint.setColor(midi == selectedMidi || midi == detectedMidi ? COLOR_LABEL_ON_ACCENT : COLOR_LABEL);
                canvas.drawText(scientificName(midi), x + whiteW / 2f, h - dp(10), labelPaint);
            }
        }
        // Black keys on top.
        for (int i = 0; i < KEY_COUNT; i++) {
            int midi = FIRST_MIDI + i;
            if (!isBlack(midi)) {
                continue;
            }
            float x = keyLeft[i];
            if (x + blackW < left || x > right) {
                continue;
            }
            keyRect.set(x, 0, x + blackW, blackH);
            Paint p = blackPaint;
            if (midi == selectedMidi) {
                fillPaint.setColor(COLOR_SELECTED);
                p = fillPaint;
            } else if (midi == detectedMidi) {
                fillPaint.setColor(COLOR_DETECTED);
                p = fillPaint;
            }
            canvas.drawRoundRect(keyRect, cornerB, cornerB, p);
            if (midi == selectedMidi || midi == detectedMidi) {
                labelPaint.setColor(COLOR_LABEL_ON_ACCENT);
                canvas.drawText(scientificName(midi), x + blackW / 2f, blackH - dp(8), labelPaint);
            }
        }
    }

    private float dp(float v) {
        return v * density;
    }
}
