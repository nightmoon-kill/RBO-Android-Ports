package com.nakpaaiyaa.rbo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

final class HudIcons {
    enum Icon { TOUCH, CHEAT, MOVES, STATE }

    static FrameLayout.LayoutParams position(View touch, int column) {
        FrameLayout.LayoutParams anchor = (FrameLayout.LayoutParams) touch.getLayoutParams();
        int size = Math.round(42 * touch.getResources().getDisplayMetrics().density);
        int gap = Math.round(4 * touch.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, anchor.gravity);
        params.topMargin = anchor.topMargin;
        params.rightMargin = anchor.rightMargin + column * (size + gap);
        return params;
    }

    static void apply(Button button, Icon icon, String description) {
        button.setText(null);
        button.setContentDescription(AppLanguage.text(description));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setAlpha(1);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundTintList(null);
        button.setBackground(new Symbol(icon));
    }

    private static final class Symbol extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Symbol(Icon icon) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            switch (icon) {
                case TOUCH:
                    line(10, 5, 14, 5, 14, 10, 19, 10, 19, 14, 14, 14, 14, 19,
                            10, 19, 10, 14, 5, 14, 5, 10, 10, 10);
                    path.close();
                    break;
                case CHEAT:
                    line(9, 5, 15, 5);
                    line(10, 5, 10, 10, 5.5f, 17, 5.5f, 19, 18.5f, 19, 18.5f, 17, 14, 10, 14, 5);
                    line(13, 11, 10.5f, 14, 13.5f, 14, 11, 17);
                    break;
                case MOVES:
                    path.moveTo(12, 8);
                    path.cubicTo(10, 6, 6, 5.5f, 4.5f, 6.5f);
                    path.lineTo(4.5f, 17.5f);
                    path.cubicTo(7, 16.5f, 10, 17, 12, 19);
                    path.cubicTo(14, 17, 17, 16.5f, 19.5f, 17.5f);
                    path.lineTo(19.5f, 6.5f);
                    path.cubicTo(18, 5.5f, 14, 6, 12, 8);
                    path.close();
                    line(12, 8, 12, 19);
                    line(7, 10, 9.5f, 10.5f);
                    line(7, 13, 9.5f, 13.5f);
                    line(14.5f, 10.5f, 17, 10);
                    line(14.5f, 13.5f, 17, 13);
                    break;
                case STATE:
                    line(5, 5, 16, 5, 19, 8, 19, 19, 5, 19);
                    path.close();
                    line(8, 5, 8, 10, 15, 10, 15, 5);
                    line(8, 19, 8, 13, 16, 13, 16, 19);
                    break;
            }
        }

        private void line(float... points) {
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        }

        @Override public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            boolean highlighted = false;
            for (int state : getState()) {
                if (state == android.R.attr.state_pressed || state == android.R.attr.state_focused
                        || state == android.R.attr.state_selected) highlighted = true;
            }
            paint.setColor(0xA0000000);
            paint.setStrokeWidth(1.9f);
            canvas.drawPath(path, paint);
            paint.setColor(highlighted ? 0xD0FFF8E8 : 0x92FFF8E8);
            paint.setStrokeWidth(1.05f);
            canvas.drawPath(path, paint);
            paint.setColor(highlighted ? 0xA5FFF8E8 : 0x62FFF8E8);
            paint.setStrokeWidth(.7f);
            canvas.drawRoundRect(1.3f, 1.3f, 22.7f, 22.7f, 3, 3, paint);
            canvas.restore();
        }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) {
            invalidateSelf();
            return true;
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
