/*
 * Copyright (C) 2025-2026 AxionOS
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
 * limitations under the License
 */
package com.android.systemui.util;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

public class ScreenAnimationController {

    private static ScreenAnimationController sInstance;

    private ContentResolver mContentResolver = null;

    private boolean mPanelExpandedWhenScreenOff = false;
    private boolean mLandscapeWhenScreenOff = false;
    private boolean mIsPressSleepButton = false;
    private boolean mAnimationEnabled = true;

    public static final String SCREEN_ANIMATION_ENABLED = "screen_animation_enabled";
    
    private ContentObserver mSettingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }
    };

    private ScreenAnimationController() {}

    public static synchronized ScreenAnimationController INSTANCE() {
        if (sInstance == null) {
            sInstance = new ScreenAnimationController();
        }
        return sInstance;
    }

    public void updateCsfStates(boolean expanded, boolean landscape, boolean powerButton) {
        mPanelExpandedWhenScreenOff = expanded;
        mLandscapeWhenScreenOff = landscape;
        mIsPressSleepButton = powerButton;
    }

    public void init(Context context) {
        mContentResolver = context.getContentResolver();
        register();
    }
    
    private void register() {
        if (mContentResolver != null) {
            mContentResolver.registerContentObserver(
                Settings.System.getUriFor(SCREEN_ANIMATION_ENABLED), 
                false, 
                mSettingsObserver, 
                UserHandle.USER_CURRENT);
            updateSettings();
        }
    }
    
    private void updateSettings() {
        if (mContentResolver != null) {
            mAnimationEnabled = Settings.System.getIntForUser(
                mContentResolver, 
                SCREEN_ANIMATION_ENABLED, 
                1, 
                ActivityManager.getCurrentUser()) == 1;
        }
    }

    public boolean isLandscapeScreenOff() {
        return mLandscapeWhenScreenOff;
    }

    public boolean isPanelExpandedWhenScreenOff() {
        return mPanelExpandedWhenScreenOff;
    }

    public boolean shouldPlayAnimation() {
        if (!mAnimationEnabled) {
            return false;
        }

        return !(mPanelExpandedWhenScreenOff || mLandscapeWhenScreenOff || mIsPressSleepButton);
    }

    public void setAnimationEnabled(boolean enabled) {
        if (mContentResolver != null) {
            Settings.System.putIntForUser(
                mContentResolver, 
                SCREEN_ANIMATION_ENABLED, 
                enabled ? 1 : 0, 
                ActivityManager.getCurrentUser());
        }
    }

    public boolean isAnimationEnabled() {
        return mAnimationEnabled;
    }

    public void destroy() {
        if (mContentResolver != null && mSettingsObserver != null) {
            mContentResolver.unregisterContentObserver(mSettingsObserver);
        }
    }
}
