/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.screenshot

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.util.Log
import android.util.Size
import android.view.Display
import com.android.internal.R
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.notification.NotificationUtils
import com.android.systemui.util.NotificationChannels
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Convenience class to handle showing and hiding notifications while taking a screenshot. */
class ScreenshotNotificationsController
@AssistedInject
internal constructor(
    @Assisted private val displayId: Int,
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val devicePolicyManager: DevicePolicyManager,
    private val actionIntentCreator: ActionIntentCreator,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    constructor(
        displayId: Int,
        context: Context,
        notificationManager: NotificationManager,
        devicePolicyManager: DevicePolicyManager,
    ) : this(
        displayId = displayId,
        context = context,
        notificationManager = notificationManager,
        devicePolicyManager = devicePolicyManager,
        actionIntentCreator =
            ActionIntentCreator(
                context,
                context.packageManager,
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
                Dispatchers.IO,
            ),
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        backgroundDispatcher = Dispatchers.IO,
    )

    private val res = context.resources

    /**
     * Sends a notification that the screenshot capture has failed.
     *
     * Errors for the non-default display are shown in a unique separate notification.
     */
    fun notifyScreenshotError(msgResId: Int) {
        val displayErrorString =
            if (displayId != Display.DEFAULT_DISPLAY) {
                " ($externalDisplayString)"
            } else {
                ""
            }
        val errorMsg = res.getString(msgResId) + displayErrorString

        // Repurpose the existing notification or create a new one
        val builder =
            Notification.Builder(context, NotificationChannels.ALERTS)
                .setTicker(res.getString(com.android.systemui.res.R.string.screenshot_failed_title))
                .setContentTitle(
                    res.getString(com.android.systemui.res.R.string.screenshot_failed_title)
                )
                .setContentText(errorMsg)
                .setSmallIcon(com.android.systemui.res.R.drawable.stat_notify_image_error)
                .setWhen(System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC) // ok to show outside lockscreen
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setColor(context.getColor(R.color.system_notification_accent_color))
        val intent =
            devicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE
            )
        if (intent != null) {
            val pendingIntent =
                PendingIntent.getActivityAsUser(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE,
                    null,
                    UserHandle.CURRENT,
                )
            builder.setContentIntent(pendingIntent)
        }
        NotificationUtils.overrideNotificationAppName(context, builder, true)
        val notification = Notification.BigTextStyle(builder).bigText(errorMsg).build()
        // A different id for external displays to keep the 2 error notifications separated.
        val id =
            if (displayId == Display.DEFAULT_DISPLAY) {
                SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT
            } else {
                SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT_EXTERNAL_DISPLAY
            }
        notificationManager.notify(id, notification)
    }

    fun showSavedScreenshotNotification(savedResult: ScreenshotSavedResult) {
        applicationScope.launch {
            try {
                postSavedScreenshotNotification(savedResult)
            } catch (t: Throwable) {
                Log.w(
                    LOG_TAG,
                    "Failed to post saved screenshot notification for ${savedResult.uri}",
                    t,
                )
            }
        }
    }

    private suspend fun postSavedScreenshotNotification(savedResult: ScreenshotSavedResult) {
        val notificationId = savedResult.uri.hashCode()
        val contentIntent =
            try {
                createActivityPendingIntent(
                    requestCode = notificationId,
                    intent = actionIntentCreator.createView(savedResult.uri),
                    user = savedResult.user,
                )
            } catch (_: Throwable) {
                null
            }
        val shareIntent =
            createActivityPendingIntent(
                requestCode = notificationId + 1,
                intent =
                    actionIntentCreator.createShareWithSubject(savedResult.uri, savedResult.subject),
                user = savedResult.user,
            )
        val builder =
            Notification.Builder(context, NotificationChannels.SCREENSHOTS_HEADSUP)
                .setTicker(res.getString(com.android.systemui.res.R.string.screenshot_saved_title))
                .setContentTitle(
                    res.getString(com.android.systemui.res.R.string.screenshot_saved_title)
                )
                .setContentText(
                    res.getString(
                        com.android.systemui.res.R.string.screenshot_saved_notification_text
                    )
                )
                .setSmallIcon(com.android.systemui.res.R.drawable.screenshot_image)
                .setWhen(savedResult.imageTime)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setGroup(POST_SCREENSHOT_NOTIFICATION_GROUP_KEY)
                .setColor(context.getColor(R.color.system_notification_accent_color))
                .addAction(
                    Notification.Action.Builder(
                            Icon.createWithResource(
                                context,
                                com.android.systemui.res.R.drawable.ic_screenshot_share,
                            ),
                            res.getText(com.android.systemui.res.R.string.screenshot_share_label),
                            shareIntent,
                        )
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                            Icon.createWithResource(
                                context,
                                com.android.systemui.res.R.drawable.ic_screenshot_delete,
                            ),
                            res.getText(R.string.delete),
                            actionIntentCreator.createDelete(savedResult.uri),
                        )
                        .build()
                )
        contentIntent?.let(builder::setContentIntent)
        loadNotificationPreview(savedResult.uri)?.let { preview -> builder.applyPreview(preview) }
        NotificationUtils.overrideNotificationAppName(context, builder, true)
        notificationManager.notify(POST_SCREENSHOT_NOTIFICATION_TAG, notificationId, builder.build())
    }

    private val externalDisplayString: String
        get() =
            res.getString(
                com.android.systemui.res.R.string.screenshot_failed_external_display_indication
            )

    /** Factory for [ScreenshotNotificationsController]. */
    @AssistedFactory
    fun interface Factory {
        fun create(displayId: Int): ScreenshotNotificationsController
    }

    private fun createActivityPendingIntent(
        requestCode: Int,
        intent: android.content.Intent,
        user: UserHandle,
    ): PendingIntent {
        return PendingIntent.getActivityAsUser(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            null,
            user,
        )
    }

    private suspend fun loadNotificationPreview(uri: android.net.Uri): Bitmap? =
        withContext(backgroundDispatcher) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    getNotificationPreviewSize(),
                    null,
                )
            }.getOrNull()
        }

    private fun getNotificationPreviewSize(): Size {
        val displayMetrics = res.displayMetrics
        return Size(
            min(displayMetrics.widthPixels, MAX_NOTIFICATION_PREVIEW_EDGE_PX),
            min(displayMetrics.heightPixels, MAX_NOTIFICATION_PREVIEW_EDGE_PX),
        )
    }

    private fun Notification.Builder.applyPreview(preview: Bitmap) {
        setLargeIcon(preview)
        setStyle(Notification.BigPictureStyle().bigPicture(preview).showBigPictureWhenCollapsed(true))
    }

    companion object {
        const val POST_SCREENSHOT_NOTIFICATION_TAG = "ScreenshotSavedNotification"
        private const val LOG_TAG = "ScreenshotNotifications"
        private const val POST_SCREENSHOT_NOTIFICATION_GROUP_KEY = "saved_screenshots"
        private const val MAX_NOTIFICATION_PREVIEW_EDGE_PX = 2048
    }
}
