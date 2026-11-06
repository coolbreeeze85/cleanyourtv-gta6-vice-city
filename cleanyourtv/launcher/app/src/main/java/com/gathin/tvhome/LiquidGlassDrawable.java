package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Translucent refractive "Liquid Glass" capsule drawable for the Vice City HUD header and pills.
 * Draws a frosted dark-glass gradient base, a bright top-half specular glass highlight, a bottom cyan
 * inner refraction band, an inner white specular rim, and an outer neon cyan/white rim.
 */
final class LiquidGlassDrawable extends Drawable {

    private final float radius;
    private final float rimWidth;
    private final float innerInset;
    private final int topFill;
    private final int bottomFill;
    private final int rimColor;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint refractPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF sheenRect = new RectF();
    private final RectF innerRect = new RectF();

    LiquidGlassDrawable(float density, float radiusDp, int rimColor) {
        this.radius = radiusDp * density;
        this.rimWidth = 1.8f * density;
        this.innerInset = 1.6f * density;
        this.topFill = 0x8C1A1034;
        this.bottomFill = 0x680C203A;
        this.rimColor = rimColor;

        innerRimPaint.setStyle(Paint.Style.STROKE);
        innerRimPaint.setStrokeWidth(1.0f * density);
        innerRimPaint.setColor(0x66FFFFFF);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
        rimPaint.setColor(rimColor);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        float half = rimWidth * 0.5f;
        boundsF.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half);
        float h = boundsF.height();
        float sheenH = Math.max(8f, h * 0.46f);
        sheenRect.set(boundsF.left + innerInset, boundsF.top + innerInset,
                boundsF.right - innerInset, boundsF.top + sheenH);
        innerRect.set(boundsF.left + innerInset, boundsF.top + innerInset,
                boundsF.right - innerInset, boundsF.bottom - innerInset);

        fillPaint.setShader(new LinearGradient(
                boundsF.left, boundsF.top, boundsF.right, boundsF.bottom,
                topFill, bottomFill, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(
                sheenRect.left, sheenRect.top, sheenRect.left, sheenRect.bottom,
                0x58FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        refractPaint.setShader(new LinearGradient(
                boundsF.left, boundsF.bottom - h * 0.32f, boundsF.left, boundsF.bottom,
                0x0000F0FF, 0x2600F0FF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundsF.isEmpty()) return;
        canvas.drawRoundRect(boundsF, radius, radius, fillPaint);
        canvas.drawRoundRect(boundsF, radius, radius, refractPaint);
        float sr = Math.max(4f, radius - innerInset);
        canvas.drawRoundRect(sheenRect, sr, sr, sheenPaint);
        canvas.drawRoundRect(innerRect, sr, sr, innerRimPaint);
        canvas.drawRoundRect(boundsF, radius, radius, rimPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        sheenPaint.setAlpha(alpha);
        refractPaint.setAlpha(alpha);
        innerRimPaint.setAlpha(alpha);
        rimPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
