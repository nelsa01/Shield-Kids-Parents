package com.shieldtechhub.shieldkids.common.utils

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

object PasscodeManager {
    private const val PREFS_NAME = "shield_prefs"
    private const val KEY_PASSCODE_HASH = "passcode_hash"
    private const val FIXED_SALT = "shieldkids_salt_v1"

    fun isPasscodeSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PASSCODE_HASH, null)?.isNotEmpty() == true
    }

    fun setPasscode(context: Context, code: String) {
        val hash = hash(code)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PASSCODE_HASH, hash)
            .apply()
    }

    fun verifyPasscode(context: Context, code: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PASSCODE_HASH, null) ?: return false
        return stored == hash(code)
    }

    fun clearPasscode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PASSCODE_HASH)
            .apply()
    }

    private fun hash(code: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((code + FIXED_SALT).toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}


