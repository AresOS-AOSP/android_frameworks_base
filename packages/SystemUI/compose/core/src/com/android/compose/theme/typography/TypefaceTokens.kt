/*
 * Copyright (C) 2022 The Android Open Source Project
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

@file:OptIn(ExperimentalTextApi::class)

package com.android.compose.theme.typography

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal class TypefaceTokens(typefaceNames: TypefaceNames) {
    companion object {
        val WeightMedium = FontWeight.Medium
        val WeightRegular = FontWeight.Normal
        val WeightSemiBold = FontWeight.SemiBold
    }

    private val brandFont = DeviceFontFamilyName(typefaceNames.brand)
    private val plainFont = DeviceFontFamilyName(typefaceNames.plain)

    // Google Sans Flex emphasized styles
    private val displayLargeEmphasizedFont =
        DeviceFontFamilyName("variable-display-large-emphasized")
    private val displayMediumEmphasizedFont =
        DeviceFontFamilyName("variable-display-medium-emphasized")
    private val displaySmallEmphasizedFont =
        DeviceFontFamilyName("variable-display-small-emphasized")
    private val headlineLargeEmphasizedFont =
        DeviceFontFamilyName("variable-headline-large-emphasized")
    private val headlineMediumEmphasizedFont =
        DeviceFontFamilyName("variable-headline-medium-emphasized")
    private val headlineSmallEmphasizedFont =
        DeviceFontFamilyName("variable-headline-small-emphasized")
    private val titleLargeEmphasizedFont = DeviceFontFamilyName("variable-title-large-emphasized")
    private val titleMediumEmphasizedFont = DeviceFontFamilyName("variable-title-medium-emphasized")
    private val titleSmallEmphasizedFont = DeviceFontFamilyName("variable-title-small-emphasized")
    private val bodyLargeEmphasizedFont = DeviceFontFamilyName("variable-body-large-emphasized")
    private val bodyMediumEmphasizedFont = DeviceFontFamilyName("variable-body-medium-emphasized")
    private val bodySmallEmphasizedFont = DeviceFontFamilyName("variable-body-small-emphasized")
    private val labelLargeEmphasizedFont = DeviceFontFamilyName("variable-label-large-emphasized")
    private val labelMediumEmphasizedFont = DeviceFontFamilyName("variable-label-medium-emphasized")
    private val labelSmallEmphasizedFont = DeviceFontFamilyName("variable-label-small-emphasized")

    val brand =
        FontFamily(
            Font(brandFont, weight = WeightMedium),
            Font(brandFont, weight = WeightRegular),
        )
    val plain =
        FontFamily(
            Font(plainFont, weight = WeightMedium),
            Font(plainFont, weight = WeightRegular),
        )

    val displayLargeEmphasized = FontFamily(Font(displayLargeEmphasizedFont, WeightMedium))
    val displayMediumEmphasized = FontFamily(Font(displayMediumEmphasizedFont, WeightMedium))
    val displaySmallEmphasized = FontFamily(Font(displaySmallEmphasizedFont, WeightMedium))
    val headlineLargeEmphasized = FontFamily(Font(headlineLargeEmphasizedFont, WeightMedium))
    val headlineMediumEmphasized = FontFamily(Font(headlineMediumEmphasizedFont, WeightMedium))
    val headlineSmallEmphasized = FontFamily(Font(headlineSmallEmphasizedFont, WeightMedium))
    val titleLargeEmphasized = FontFamily(Font(titleLargeEmphasizedFont, WeightMedium))
    val titleMediumEmphasized = FontFamily(Font(titleMediumEmphasizedFont, WeightSemiBold))
    val titleSmallEmphasized = FontFamily(Font(titleSmallEmphasizedFont, WeightSemiBold))
    val bodyLargeEmphasized = FontFamily(Font(bodyLargeEmphasizedFont, WeightMedium))
    val bodyMediumEmphasized = FontFamily(Font(bodyMediumEmphasizedFont, WeightMedium))
    val bodySmallEmphasized = FontFamily(Font(bodySmallEmphasizedFont, WeightMedium))
    val labelLargeEmphasized = FontFamily(Font(labelLargeEmphasizedFont, WeightSemiBold))
    val labelMediumEmphasized = FontFamily(Font(labelMediumEmphasizedFont, WeightSemiBold))
    val labelSmallEmphasized = FontFamily(Font(labelSmallEmphasizedFont, WeightSemiBold))
}

internal data class TypefaceNames
private constructor(
    val brand: String,
    val plain: String,
) {
    private enum class Config(val configName: String, val default: String) {
        Brand("config_headlineFontFamily", "sans-serif"),
        Plain("config_bodyFontFamily", "sans-serif"),
    }

    companion object {
        fun get(context: Context): TypefaceNames {
            return TypefaceNames(
                brand = getTypefaceName(context, Config.Brand),
                plain = getTypefaceName(context, Config.Plain),
            )
        }

        private fun getTypefaceName(context: Context, config: Config): String {
            return context
                .getString(context.resources.getIdentifier(config.configName, "string", "android"))
                .takeIf { it.isNotEmpty() }
                ?: config.default
        }
    }
}
