package com.cryptotalk.app.util

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * DeviceIdHelper gets a unique ID for the phone.
 * This is used to make sure someone doesn't try to log into your account from another device.
 */
object DeviceIdHelper {
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
    }
}
