package com.gaifd.carradio.ui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Куда сейчас идёт звук. car = Bluetooth-аудио (обычно это машина). */
data class AudioOut(val name: String, val bluetooth: Boolean)

/** Следит за тем, куда Android выводит звук: машина/колонка по Bluetooth, наушники или динамик. */
class AudioOutWatcher(ctx: Context, private val onChange: (AudioOut) -> Unit) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val cb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onChange(current())
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onChange(current())
    }

    fun start() {
        am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        onChange(current())
    }

    fun stop() {
        am?.unregisterAudioDeviceCallback(cb)
    }

    fun current(): AudioOut {
        val devices = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return AudioOut("Динамик телефона", false)
        val bt = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
        if (bt != null) return AudioOut(bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth", true)
        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (wired != null) return AudioOut("Наушники", false)
        return AudioOut("Динамик телефона", false)
    }
}
