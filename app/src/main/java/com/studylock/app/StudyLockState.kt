package com.studylock.app

import android.content.Context
import java.security.SecureRandom

/**
 * The cycle:
 *  LOCKED  - protected apps are blocked until the lock minutes run out.
 *  ASK     - the lock has ended; opening a protected app asks "Have you finished studying?".
 *  OPEN    - the user answered Yes; protected apps work until the user leaves them.
 */
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

    enum class Phase { LOCKED, ASK, OPEN }
    data class Snapshot(val phase: Phase, val remainingMs: Long, val enabled: Boolean)

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

    /** Starts (or restarts) a lock period. Used by Start cycle and by "I'm having a break". */
    fun startCycle(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putBoolean(KEY_ENABLED, true).putString(KEY_PHASE, Phase.LOCKED.name)
            .putLong(KEY_END, now + lockMinutes(context) * 60_000L).putLong(KEY_LAST_WALL, now).apply()
    }

    fun stopCycle(context: Context) { prefs(context).edit().putBoolean(KEY_ENABLED, false).apply() }

    @Synchronized fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ENABLED, false)) return Snapshot(Phase.OPEN, 0, false)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_WALL, now)
        // Never let a backwards clock adjustment increase elapsed time.
        val effectiveNow = maxOf(now, last)
        var phase = runCatching { Phase.valueOf(p.getString(KEY_PHASE, Phase.LOCKED.name)!!) }.getOrDefault(Phase.LOCKED)
        var end = p.getLong(KEY_END, effectiveNow + lockMinutes(context) * 60_000L)
        val startPhase = phase
        val startEnd = end
        // When the lock runs out, wait for the user's answer instead of unlocking by itself.
        if (phase == Phase.LOCKED && effectiveNow >= end) { phase = Phase.ASK; end = 0L }
        // Write only when something changed (or every 30 s for the clock guard), not on every call.
        if (phase != startPhase || end != startEnd || effectiveNow - last > 30_000L) {
            p.edit().putString(KEY_PHASE, phase.name).putLong(KEY_END, end).putLong(KEY_LAST_WALL, effectiveNow).apply()
        }
        val remaining = if (phase == Phase.LOCKED) (end - effectiveNow).coerceAtLeast(0) else 0L
        return Snapshot(phase, remaining, true)
    }

    /** "Yes, I've finished studying" (or the early-deactivation challenge): protected apps open up. */
    fun startOpen(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putBoolean(KEY_ENABLED, true).putString(KEY_PHASE, Phase.OPEN.name)
            .putLong(KEY_END, 0L).putLong(KEY_LAST_WALL, now).apply()
    }

    /** The user left the protected apps (or the screen turned off): the next open asks again. */
    @Synchronized fun endSession(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ENABLED, false)) return
        val phase = runCatching { Phase.valueOf(p.getString(KEY_PHASE, "")!!) }.getOrNull()
        if (phase == Phase.OPEN) p.edit().putString(KEY_PHASE, Phase.ASK.name).apply()
    }

    fun challenge(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*()-_=+[]{};:,.?"
        val random = SecureRandom()
        return buildString(length) { repeat(length) { append(chars[random.nextInt(chars.length)]) } }
    }
}
