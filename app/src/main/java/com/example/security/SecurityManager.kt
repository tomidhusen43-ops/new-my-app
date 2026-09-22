package com.example.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest

object SecurityManager {
    private const val PREFS_NAME = "card_clone_security_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_SCREEN_SECURITY = "screen_security_enabled"

    val isAppLocked = mutableStateOf(false)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun isPinEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(context: Context, pin: String) {
        val prefs = getPrefs(context)
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return storedHash == hashPin(pin)
    }

    fun disablePin(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_PIN_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .apply()
        isAppLocked.value = false
    }

    fun isScreenSecurityEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SCREEN_SECURITY, false)
    }

    fun setScreenSecurity(activity: Activity, enabled: Boolean) {
        getPrefs(activity).edit().putBoolean(KEY_SCREEN_SECURITY, enabled).apply()
        applyScreenSecurity(activity, enabled)
    }

    fun applyScreenSecurity(activity: Activity, enabled: Boolean? = null) {
        val isSecure = enabled ?: isScreenSecurityEnabled(activity)
        if (isSecure) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun checkAndLock(context: Context) {
        if (isPinEnabled(context)) {
            isAppLocked.value = true
        } else {
            isAppLocked.value = false
        }
    }

    fun unlock() {
        isAppLocked.value = false
    }
}
