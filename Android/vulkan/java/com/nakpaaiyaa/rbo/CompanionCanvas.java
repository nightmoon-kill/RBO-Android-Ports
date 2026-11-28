package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

abstract class CompanionCanvas extends View {
    static final int GOLD = 0xFFDFBE7C, WHITE = 0xFFF7EDDB, MUTED = 0xFFB3A58C;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF box = new RectF();
    CompanionCanvas(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    final void card(Canvas c, float x, float y, float w, float h, int fill, int border) {
        box.set(x,y,x+w,y+h);
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
        c.drawRoundRect(box, 12, 12, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(border);
        c.drawRoundRect(box, 12, 12, paint); paint.setStyle(Paint.Style.FILL);
    }
    final void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
        value = AppLanguage.text(value);
        paint.setShader(null); paint.setColor(color); paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }
    final void right(Canvas c, String value, float x, float y, float size, int color) {
        value = AppLanguage.text(value);
        paint.setTextSize(size); paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text(c, value, x-paint.measureText(value), y, size, color, true);
    }
    @Override protected final void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(0xFF191713);
        float scale = Math.min(getWidth()/960f, getHeight()/470f);
        c.save(); c.translate((getWidth()-960*scale)/2, (getHeight()-470*scale)/2); c.scale(scale,scale);
        drawPage(c); c.restore();
    }
    abstract void drawPage(Canvas canvas);
}
