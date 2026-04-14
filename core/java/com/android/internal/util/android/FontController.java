/*
 * Copyright (C) 2025-2026 AxionOS
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
package com.android.internal.util.android;

import android.app.ActivityThread;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.SystemProperties;
import android.util.Log;
import android.util.LruCache;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FontController {

    private static final String TAG = "FontController";

    private static FontController sInstance;

    private static final String DEFAULT_FONT = "google-sans-text";
    private static final String DEFAULT_FONT_MEDIUM = "google-sans-text-medium";
    private static final String DEFAULT_HEADLINE_FONT = "google-sans-text";
    private static final String DEFAULT_HEADLINE_FONT_MEDIUM = "google-sans-medium";

    private static final int IDX_BODY = 0;
    private static final int IDX_BODY_MEDIUM = 1;
    private static final int IDX_HEADLINE = 2;
    private static final int IDX_HEADLINE_MEDIUM = 3;
    private static final int FIELD_COUNT = 4;
    private static final int WEIGHT_REGULAR = 400;
    private static final int WEIGHT_MEDIUM = 500;
    private static final int WEIGHT_SEMIBOLD = 600;

    private volatile Resources mResources;
    private volatile String[] mFontConfig;

    private final LruCache<String, Typeface> mCache = new LruCache<>(30);

    private static final Set<String> EXCLUDED_APPS = new HashSet<>(Arrays.asList(
            "it.subito",
            "tv.arte.plus7",
            "com.google.android.gm"
    ));

    private static final Set<String> WHITELIST_FONTS = new HashSet<>(Arrays.asList(
            "serif",
            "monospace",
            "cursive",
            "NotoSansSC",
            "NotoSansTC",
            "NotoSansJP",
            "NotoSansKR",
            "NotoColorEmoji",
            "NotoColorEmojiFlags",
            "NotoSansMono",
            "RobotoMono",
            "DroidSansMono",
            "CutiveMono",
            "CarroisGothicSC",
            "source-code-pro",
            "google-sans-clock",
            "google-sans-flex-clock"
    ));

    private static final String[][] WEIGHT_KEYWORDS = {
            {"thin", "100"},
            {"extra-light", "200"},
            {"extralight", "200"},
            {"light", "300"},
            {"semi-bold", "600"},
            {"semibold", "600"},
            {"extra-bold", "800"},
            {"extrabold", "800"},
            {"bold", "700"},
            {"medium", "500"},
            {"black", "900"},
    };

    public static FontController get() {
        if (sInstance == null) {
            sInstance = new FontController();
        }
        return sInstance;
    }

    private FontController() {}

    public static void onConfigurationChanged(Resources res) {
        FontController fc = get();
        fc.mResources = res;
        fc.mFontConfig = null;
        Typeface.changeFont();
    }

    public static void OnConfigurationChanged(Resources res) {
        onConfigurationChanged(res);
    }

    public static String getBodyFont() {
        return get().resolveConfig()[IDX_BODY];
    }

    public static String getBodyFontMedium() {
        return get().resolveConfig()[IDX_BODY_MEDIUM];
    }

    public static String getHeadlineFont() {
        return get().resolveConfig()[IDX_HEADLINE];
    }

    public static String getHeadlineFontMedium() {
        return get().resolveConfig()[IDX_HEADLINE_MEDIUM];
    }

    public static String getCurrentFontFamily() {
        return getBodyFont();
    }

    public static boolean isCustomFontActive() {
        String[] config = get().resolveConfig();
        return !DEFAULT_FONT.equals(config[IDX_BODY])
                || !DEFAULT_FONT_MEDIUM.equals(config[IDX_BODY_MEDIUM])
                || !DEFAULT_HEADLINE_FONT.equals(config[IDX_HEADLINE])
                || !DEFAULT_HEADLINE_FONT_MEDIUM.equals(config[IDX_HEADLINE_MEDIUM]);
    }

    public static boolean isExcludedApp() {
        String pkg = getCurrentPackageName();
        return pkg != null && EXCLUDED_APPS.contains(pkg);
    }

    public static boolean isFontWhitelisted(String familyName) {
        if (familyName == null) return false;
        for (String wl : WHITELIST_FONTS) {
            if (familyName.contains(wl)) return true;
        }
        return false;
    }

    public static Typeface getOverrideTypeface(String familyName) {
        if (familyName == null) return null;
        if (isExcludedApp()) return null;
        if (isFontWhitelisted(familyName)) return null;
        if (!isCustomFontActive()) return null;

        FontController fc = get();
        String[] config = fc.resolveConfig();

        if (familyName.equals(config[IDX_HEADLINE])) {
            return Typeface.getSystemDefaultTypeface(config[IDX_HEADLINE]);
        }
        if (familyName.equals(config[IDX_HEADLINE_MEDIUM])) {
            return Typeface.getSystemDefaultTypeface(config[IDX_HEADLINE_MEDIUM]);
        }
        if (familyName.equals(config[IDX_BODY_MEDIUM])) {
            return Typeface.getSystemDefaultTypeface(config[IDX_BODY_MEDIUM]);
        }

        boolean isVariable = familyName.startsWith("variable-");
        int weight = resolveWeight(familyName);
        boolean isItalic = familyName.contains("italic");

        Typeface cached = fc.mCache.get(familyName);
        if (cached != null) return cached;

        if (isVariable) {
            Typeface resolved = resolveVariableTypeface(config, familyName, weight, isItalic);
            if (resolved != null) {
                fc.mCache.put(familyName, resolved);
                return resolved;
            }
        }

        Typeface base = resolveBase(config, familyName, isVariable, weight);

        if (weight == WEIGHT_REGULAR && !isItalic && base == Typeface.DEFAULT) {
            return Typeface.DEFAULT;
        }

        Typeface result = Typeface.create(base, weight, isItalic);
        fc.mCache.put(familyName, result);
        return result;
    }

    private static Typeface resolveVariableTypeface(String[] config, String name, int weight,
            boolean isItalic) {
        if (shouldUseNativeVariableFamily(config, name) && !isItalic) {
            return Typeface.getSystemDefaultTypeface(name);
        }

        String baseFamily = resolveVariableFamilyName(config, name, weight);
        if (baseFamily == null || baseFamily.isEmpty()) {
            return null;
        }

        Typeface base = Typeface.getSystemDefaultTypeface(baseFamily);
        int defaultWeight = isMediumSlotFamily(config, name, baseFamily)
                ? WEIGHT_MEDIUM : WEIGHT_REGULAR;
        int baseWeight = resolveConfiguredFamilyWeight(baseFamily, defaultWeight);

        if (!isItalic && baseWeight == weight) {
            return base;
        }
        return Typeface.create(base, weight, isItalic);
    }

    private static boolean shouldUseNativeVariableFamily(String[] config, String name) {
        boolean isHeadlineRole = name.startsWith("variable-display")
                || name.startsWith("variable-headline");
        String regularFamily = isHeadlineRole ? config[IDX_HEADLINE] : config[IDX_BODY];
        String mediumFamily = isHeadlineRole ? config[IDX_HEADLINE_MEDIUM] : config[IDX_BODY_MEDIUM];
        return isGoogleSansFamily(regularFamily) && isGoogleSansFamily(mediumFamily);
    }

    private static boolean isGoogleSansFamily(String familyName) {
        return familyName != null && familyName.startsWith("google-sans");
    }

    private static String resolveVariableFamilyName(String[] config, String name, int weight) {
        boolean isHeadlineRole = name.startsWith("variable-display")
                || name.startsWith("variable-headline");
        String regularFamily = isHeadlineRole ? config[IDX_HEADLINE] : config[IDX_BODY];
        String mediumFamily = isHeadlineRole ? config[IDX_HEADLINE_MEDIUM] : config[IDX_BODY_MEDIUM];

        int regularWeight = resolveConfiguredFamilyWeight(regularFamily, WEIGHT_REGULAR);
        int mediumWeight = resolveConfiguredFamilyWeight(mediumFamily, WEIGHT_MEDIUM);

        if (mediumFamily != null && !mediumFamily.isEmpty()
                && Math.abs(mediumWeight - weight) <= Math.abs(regularWeight - weight)) {
            return mediumFamily;
        }
        return regularFamily;
    }

    private static boolean isMediumSlotFamily(String[] config, String name, String familyName) {
        boolean isHeadlineRole = name.startsWith("variable-display")
                || name.startsWith("variable-headline");
        String mediumFamily = isHeadlineRole ? config[IDX_HEADLINE_MEDIUM] : config[IDX_BODY_MEDIUM];
        return familyName.equals(mediumFamily);
    }

    private static int resolveConfiguredFamilyWeight(String familyName, int defaultWeight) {
        if (familyName == null || familyName.isEmpty()) {
            return defaultWeight;
        }

        for (String[] entry : WEIGHT_KEYWORDS) {
            if (familyName.contains(entry[0])) return Integer.parseInt(entry[1]);
        }
        return defaultWeight;
    }

    private static Typeface resolveBase(String[] config, String name,
            boolean isVariable, int weight) {
        if (!isVariable) return Typeface.DEFAULT;

        boolean isHeadlineRole = name.startsWith("variable-display")
                || name.startsWith("variable-headline");
        boolean isMediumWeight = weight >= 500;

        String baseName;
        if (isHeadlineRole) {
            baseName = isMediumWeight ? config[IDX_HEADLINE_MEDIUM] : config[IDX_HEADLINE];
        } else {
            baseName = isMediumWeight ? config[IDX_BODY_MEDIUM] : config[IDX_BODY];
        }

        Typeface base = Typeface.getSystemDefaultTypeface(baseName);
        return base != null ? base : Typeface.DEFAULT;
    }

    private static int resolveWeight(String name) {
        if (name.startsWith("variable-")) {
            boolean emphasized = name.endsWith("-emphasized");
            if (name.contains("-title-medium") || name.contains("-title-small")
                    || name.contains("-label-")) {
                return emphasized ? 600 : 500;
            }
            return emphasized ? 500 : 400;
        }

        for (String[] entry : WEIGHT_KEYWORDS) {
            if (name.contains(entry[0])) return Integer.parseInt(entry[1]);
        }
        return 400;
    }

    public static void clearCaches() {
        get().mCache.evictAll();
    }

    private String[] resolveConfig() {
        if (mFontConfig != null) {
            return mFontConfig;
        }

        String[] config = new String[FIELD_COUNT];
        Resources res = mResources;

        if (res != null) {
            config[IDX_BODY] = getResourceString(res, "config_bodyFontFamily", DEFAULT_FONT);
            config[IDX_BODY_MEDIUM] = getResourceString(res, "config_bodyFontFamilyMedium",
                    DEFAULT_FONT_MEDIUM);
            config[IDX_HEADLINE] = getResourceString(res, "config_headlineFontFamily",
                    DEFAULT_HEADLINE_FONT);
            config[IDX_HEADLINE_MEDIUM] = getResourceString(res, "config_headlineFontFamilyMedium",
                    DEFAULT_HEADLINE_FONT_MEDIUM);
        } else {
            config[IDX_BODY] = DEFAULT_FONT;
            config[IDX_BODY_MEDIUM] = DEFAULT_FONT_MEDIUM;
            config[IDX_HEADLINE] = DEFAULT_HEADLINE_FONT;
            config[IDX_HEADLINE_MEDIUM] = DEFAULT_HEADLINE_FONT_MEDIUM;
        }

        mFontConfig = config;
        logger("Font config: body=" + config[IDX_BODY]
                + " bodyMed=" + config[IDX_BODY_MEDIUM]
                + " headline=" + config[IDX_HEADLINE]
                + " headlineMed=" + config[IDX_HEADLINE_MEDIUM]);
        return config;
    }

    private static String getResourceString(Resources res, String name, String defaultValue) {
        try {
            int id = res.getIdentifier(name, "string", "android");
            if (id != 0) {
                return res.getString(id);
            }
        } catch (Exception e) {
            logger("getResourceString failed for " + name + ": " + e.getMessage());
        }
        return defaultValue;
    }

    private static String getCurrentPackageName() {
        try {
            return ActivityThread.currentPackageName();
        } catch (Exception e) {
            return null;
        }
    }

    private static void logger(String msg) {
        if (SystemProperties.getBoolean("persist.sys.ax_font_debug", false)) {
            Log.d(TAG, msg);
        }
    }
}
