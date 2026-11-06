package ua.moneytracking

import android.content.Context

/** Local presentation and quick-entry preferences; financial data stays in Room. */
data class UserPreferences(
    val hideBalance: Boolean = false,
    val haptics: Boolean = true,
    val defaultAccountId: Long? = null,
    val defaultKind: String = "expense"
)

private const val PREFERENCES_FILE = "finance_preferences"

fun loadPreferences(context: Context): UserPreferences {
    val preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    val defaultKind = preferences.getString("default_kind", "expense") ?: "expense"
    return UserPreferences(
        hideBalance = preferences.getBoolean("hide_balance", false),
        haptics = preferences.getBoolean("keypad_haptics", true),
        defaultAccountId = if (preferences.contains("default_account")) {
            preferences.getLong("default_account", 0L).takeIf { it > 0L }
        } else null,
        defaultKind = defaultKind.takeIf { it in setOf("expense", "income", "transfer") } ?: "expense"
    )
}

fun savePreferences(context: Context, value: UserPreferences) {
    context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE).edit().apply {
        putBoolean("hide_balance", value.hideBalance)
        putBoolean("keypad_haptics", value.haptics)
        putString("default_kind", value.defaultKind)
        if (value.defaultAccountId == null) remove("default_account")
        else putLong("default_account", value.defaultAccountId)
    }.apply()
}
