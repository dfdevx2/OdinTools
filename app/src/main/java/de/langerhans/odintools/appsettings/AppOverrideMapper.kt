package de.langerhans.odintools.appsettings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.data.AppOverrideEntity
import javax.inject.Inject

class AppOverrideMapper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun mapOverrideCandidates(existingOverrides: List<AppOverrideEntity>): List<AppUiModel> {
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.enabled
        }.filterNot { appInfo ->
            existingOverrides.any { appInfo.packageName == it.packageName }
        }.map {
            val icon = context.packageManager.getApplicationIcon(it.packageName)
            val label = context.packageManager.getApplicationLabel(it).toString()
            AppUiModel(it.packageName, label, icon)
        }.sortedBy {
            it.appName
        }
    }

    fun mapAppOverrides(overrides: List<AppOverrideEntity>): List<AppUiModel> {
        return overrides.mapNotNull(::mapAppOverride)
    }

    fun mapAppOverride(app: AppOverrideEntity): AppUiModel? {
        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
        }.onFailure { return null }.getOrNull() ?: return null

        val tdp = app.tdpProfile ?: "Stock"
        val clock = app.clockProfile ?: "Stock"
        val fan = app.fanProfile ?: "Stock"

        return AppUiModel(
            packageName = app.packageName,
            appName = context.packageManager.getApplicationLabel(appInfo).toString(),
            appIcon = context.packageManager.getApplicationIcon(appInfo),
            subtitle = getSubtitle(tdp, clock, fan),
            tdpProfile = tdp,
            clockProfile = clock,
            fanProfile = fan
        )
    }

    fun mapEmptyOverride(packageName: String): AppUiModel {
        val appInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)

        return AppUiModel(
            packageName = packageName,
            appName = context.packageManager.getApplicationLabel(appInfo).toString(),
            appIcon = context.packageManager.getApplicationIcon(appInfo),
        )
    }

    private fun getSubtitle(tdp: String, clock: String, fan: String): String? {
        return buildString {
            if (tdp != "Stock") append("TDP: $tdp | ")
            if (clock != "Stock") append("Clock: $clock | ")
            if (fan != "Stock") append("Fan: $fan | ")
        }.trimEnd(' ', '|').ifEmpty { "Sem limites customizados (Stock)" }
    }
}
