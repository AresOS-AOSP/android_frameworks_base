/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume.dialog.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.axion.blur.AxBlurBackgroundRenderer;
import com.android.axion.blur.AxBlurColors;
import com.android.axion.blur.model.AxBackdropBlurSettingsSpec;

/**
 * Background view for the volume dialog panel that renders a backdrop blur behind its
 * {@link GradientDrawable} background when cross-window blur is active, falling back to the
 * original solid background otherwise. Keeps a {@link GradientDrawable} as the view background
 * so binders that mutate corner radii keep working.
 */
public class VolumeDialogBlurBackgroundView extends View {
    private final AxBlurBackgroundRenderer mBlurRenderer;
    private int mOverlayColor;

    public VolumeDialogBlurBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBlurRenderer = new AxBlurBackgroundRenderer(this, AxBackdropBlurSettingsSpec.system(),
                true);
        updateColors();
        Drawable background = getBackground();
        if (background instanceof GradientDrawable) {
            setBackground(new BlurGradientDrawable((GradientDrawable) background));
        }
    }

    @Override
    public void setBackground(Drawable background) {
        // Re-wrap any plain GradientDrawable set later (e.g. via setBackgroundResource) so the
        // backdrop blur survives background swaps.
        if (mBlurRenderer != null
                && background instanceof GradientDrawable
                && !(background instanceof BlurGradientDrawable)) {
            background = new BlurGradientDrawable((GradientDrawable) background);
        }
        super.setBackground(background);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
        mBlurRenderer.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        mBlurRenderer.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mBlurRenderer.onVisibilityAggregated(isVisible);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateColors();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return mBlurRenderer.verifyDrawable(who) || super.verifyDrawable(who);
    }

    private void updateColors() {
        mOverlayColor = AxBlurColors.surfaceContainerTint(getContext());
        invalidate();
    }

    /**
     * GradientDrawable that draws a backdrop blur clipped to its current corner radius when
     * cross-window blur is active, and falls back to the solid source drawable otherwise.
     * Extends GradientDrawable so existing binders can keep casting and animating corners.
     */
    private class BlurGradientDrawable extends GradientDrawable {
        private final Object mBlurKey = new Object();

        BlurGradientDrawable(GradientDrawable source) {
            setShape(source.getShape());
            if (source.getColor() != null) {
                setColor(source.getColor());
            }
            float[] radii = source.getCornerRadii();
            if (radii != null) {
                setCornerRadii(radii);
            } else {
                setCornerRadius(source.getCornerRadius());
            }
            setAlpha(source.getAlpha());
        }

        @Override
        public void draw(Canvas canvas) {
            if (getBounds().isEmpty()) {
                return;
            }
            boolean drewBlur;
            float[] radii = getCornerRadii();
            if (radii != null && radii.length >= 8) {
                drewBlur = mBlurRenderer.draw(
                        canvas,
                        mBlurKey,
                        getBounds().left,
                        getBounds().top,
                        getBounds().right,
                        getBounds().bottom,
                        radii,
                        mOverlayColor,
                        getAlpha());
            } else {
                drewBlur = mBlurRenderer.draw(
                        canvas,
                        mBlurKey,
                        getBounds().left,
                        getBounds().top,
                        getBounds().right,
                        getBounds().bottom,
                        getCornerRadius(),
                        mOverlayColor,
                        getAlpha());
            }
            if (!drewBlur && getAlpha() > 0 && !mBlurRenderer.isCrossWindowBlurActive()) {
                super.draw(canvas);
            }
        }
    }
}
