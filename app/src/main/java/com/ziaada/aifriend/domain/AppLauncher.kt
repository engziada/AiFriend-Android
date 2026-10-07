package com.ziaada.aifriend.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.text.Normalizer
import java.util.Locale

/** Finds an installed launcher app by its visible name and opens it. */
class AppLauncher(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    data class Result(val opened: Boolean, val message: String)

    fun launch(requestedName: String): Result {
        val query = normalize(requestedName)
            .replace(Regex("^(?:تطبيق|البرنامج|برنامج|app|application)\\s+"), "")
            .trim()
        if (query.isBlank()) return Result(false, "قولّي اسم التطبيق اللي أفتحه.")

        val aliases = aliases(query)
        val launchableApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        ).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            AppCandidate(label, activity.packageName)
        }.distinctBy { it.packageName }

        val app = launchableApps.firstOrNull { candidate ->
            val label = normalize(candidate.label)
            val pkg = normalize(candidate.packageName)
            label == query || pkg == query || aliases.any { alias -> label == alias || pkg.contains(alias) }
        } ?: launchableApps.firstOrNull { candidate ->
            val label = normalize(candidate.label)
            aliases.any { alias -> alias.length >= 3 && (label.contains(alias) || alias.contains(label)) }
        }

        if (app == null) {
            return Result(false, "مش لاقي تطبيق مثبت اسمه $requestedName.")
        }

        return try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return Result(false, "تطبيق ${app.label} ما عندوش شاشة تشغيل متاحة.")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Result(true, "فتحت ${app.label}.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch ${app.packageName}", e)
            Result(false, "ما قدرتش أفتح ${app.label}.")
        }
    }

    private fun aliases(query: String): Set<String> {
        val mapped = when {
            query.contains("يوتيوب") || query.contains("youtube") -> setOf("youtube", "يوتيوب")
            query.contains("خرائط") || query.contains("maps") || query.contains("مابس") -> setOf("maps", "خرائط", "google maps")
            query.contains("واتساب") || query.contains("whatsapp") -> setOf("whatsapp", "واتساب")
            query.contains("سبوتيفاي") || query.contains("spotify") -> setOf("spotify", "سبوتيفاي")
            query.contains("موسيقى") || query.contains("music") -> setOf("music", "موسيقى", "مشغل الموسيقى")
            query.contains("راديو") || query.contains("radio") -> setOf("radio", "راديو")
            query.contains("متصفح") || query.contains("browser") -> setOf("browser", "متصفح", "chrome")
            else -> emptySet()
        }
        return mapped + query
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[َُِّْٰـ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private data class AppCandidate(val label: String, val packageName: String)

    companion object { private const val TAG = "AppLauncher" }
}
