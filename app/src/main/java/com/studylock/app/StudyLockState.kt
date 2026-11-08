package com.studylock.app

import android.content.Context
import java.security.SecureRandom

object StudyLockState {
    private const val PREFS = "studylock_settings"
    private const val KEY_LOCK_MIN = "lock_minutes"
    private const val KEY_USE_MIN = "use_minutes"
    private const val KEY_CHALLENGE_LEN = "challenge_length"
    private const val KEY_PACKAGES = "selected_packages"
    private const val KEY_PHASE = "phase"
    private const val KEY_END = "phase_end_wall_ms"
    private const val KEY_LAST_WALL = "last_wall_ms"
    private const val KEY_ENABLED = "cycle_enabled"

    enum class Phase { LOCKED, ALLOWED }
    data class Snapshot(val phase: Phase, val remainingMs: Long, val lockMinutes: Int, val useMinutes: Int)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun lockMinutes(context: Context) = prefs(context).getInt(KEY_LOCK_MIN, 60)
    fun useMinutes(context: Context) = prefs(context).getInt(KEY_USE_MIN, 15)
    fun challengeLength(context: Context) = prefs(context).getInt(KEY_CHALLENGE_LEN, 100)
    fun selected(context: Context): Set<String> = prefs(context).getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun saveSettings(context: Context, lock: Int, use: Int, challenge: Int, packages: Set<String>) {
        require(lock >= 30) { "Lock duration must be at least 30 minutes." }
        require(use in 1..30) { "Allowed usage must be between 1 and 30 minutes." }
        require(challenge in 20..300) { "Challenge length must be between 20 and 300." }
        prefs(context).edit().putInt(KEY_LOCK_MIN, lock).putInt(KEY_USE_MIN, use)
            .putInt(KEY_CHALLENGE_LEN, challenge).putStringSet(KEY_PACKAGES, packages.toSet()).apply()
    }

    fun startCycle(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putBoolean(KEY_ENABLED, true).putString(KEY_PHASE, Phase.LOCKED.name)
            .putLong(KEY_END, now + lockMinutes(context) * 60_000L).putLong(KEY_LAST_WALL, now).apply()
    }

    fun stopCycle(context: Context) { prefs(context).edit().putBoolean(KEY_ENABLED, false).apply() }

    @Synchronized fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        val lock = lockMinutes(context); val use = useMinutes(context)
        if (!p.getBoolean(KEY_ENABLED, false)) return Snapshot(Phase.ALLOWED, 0, lock, use)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_WALL, now)
        // Never let a backwards clock adjustment increase elapsed time.
        val effectiveNow = maxOf(now, last)
        var phase = runCatching { Phase.valueOf(p.getString(KEY_PHASE, Phase.LOCKED.name)!!) }.getOrDefault(Phase.LOCKED)
        var end = p.getLong(KEY_END, effectiveNow + lock * 60_000L)
        var guard = 0
        while (effectiveNow >= end && guard++ < 100) {
            phase = if (phase == Phase.LOCKED) Phase.ALLOWED else Phase.LOCKED
            end += (if (phase == Phase.LOCKED) lock else use) * 60_000L
        }
        p.edit().putString(KEY_PHASE, phase.name).putLong(KEY_END, end).putLong(KEY_LAST_WALL, effectiveNow).apply()
        return Snapshot(phase, (end - effectiveNow).coerceAtLeast(0), lock, use)
    }

    fun startAllowedNow(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putBoolean(KEY_ENABLED, true).putString(KEY_PHASE, Phase.ALLOWED.name)
            .putLong(KEY_END, now + useMinutes(context) * 60_000L).putLong(KEY_LAST_WALL, now).apply()
    }

    fun challenge(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*()-_=+[]{};:,.?"
        val random = SecureRandom()
        return buildString(length) { repeat(length) { append(chars[random.nextInt(chars.length)]) } }
    }
}
