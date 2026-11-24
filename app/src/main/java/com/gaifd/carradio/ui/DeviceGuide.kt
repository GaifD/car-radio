package com.gaifd.carradio.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.gaifd.carradio.L
import com.gaifd.carradio.R

/**
 * Каждый производитель по-своему выключает приложения в фоне. Определяем телефон
 * и показываем инструкцию именно для него (по мотивам dontkillmyapp.com).
 */
object DeviceGuide {
    enum class Vendor { SAMSUNG, XIAOMI, HUAWEI, ONEPLUS, OPPO, VIVO, OTHER }

    val vendor: Vendor by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            "samsung" in m -> Vendor.SAMSUNG
            "xiaomi" in m || "redmi" in m || "poco" in m -> Vendor.XIAOMI
            "huawei" in m || "honor" in m -> Vendor.HUAWEI
            "oneplus" in m -> Vendor.ONEPLUS
            "oppo" in m || "realme" in m -> Vendor.OPPO
            "vivo" in m || "iqoo" in m -> Vendor.VIVO
            else -> Vendor.OTHER
        }
    }

    /** Название производителя так, как его пишут сами: Samsung, Xiaomi, OnePlus… */
    val brand: String
        get() = when (vendor) {
            Vendor.SAMSUNG -> "Samsung"
            Vendor.XIAOMI -> "Xiaomi"
            Vendor.HUAWEI -> if ("honor" in Build.MANUFACTURER.lowercase()) "Honor" else "Huawei"
            Vendor.ONEPLUS -> "OnePlus"
            Vendor.OPPO -> if ("realme" in Build.MANUFACTURER.lowercase()) "realme" else "OPPO"
            Vendor.VIVO -> "vivo"
            Vendor.OTHER -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }

    /** Есть ли у этого телефона что-то сверх обычного «снять ограничение батареи». */
    val hasExtra: Boolean get() = vendor != Vendor.OTHER

    fun steps(): String = L.s(
        when (vendor) {
            Vendor.SAMSUNG -> R.string.set_087
            Vendor.XIAOMI -> R.string.dev_xiaomi
            Vendor.HUAWEI -> R.string.dev_huawei
            Vendor.ONEPLUS -> R.string.dev_oneplus
            Vendor.OPPO -> R.string.dev_oppo
            Vendor.VIVO -> R.string.dev_vivo
            Vendor.OTHER -> R.string.dev_other
        }
    )

    /** Экраны автозапуска разных производителей. Какой-то из них обычно есть; если нет — свойства приложения. */
    private val autostart = mapOf(
        Vendor.XIAOMI to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        Vendor.HUAWEI to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
        Vendor.OPPO to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
        Vendor.VIVO to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
        Vendor.ONEPLUS to listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
    )

    fun open(ctx: Context) {
        for ((pkg, cls) in autostart[vendor].orEmpty()) {
            try {
                ctx.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
