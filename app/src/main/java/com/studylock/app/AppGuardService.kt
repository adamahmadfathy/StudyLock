package com.studylock.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class AppGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var lastBlockedPackage: String? = null

    // Re-checks once per second so a lock that ends (or starts) while a protected app is
    // already open still takes effect, without waiting for another window event.
    private val poll = object : Runnable {
        override fun run() {
            evaluate(currentPackage)
            handler.postDelayed(this, 1000)
        }
    }

    // Looks for a new StudyLock release now and then (it only acts if one exists).
    private val updateCheck = object : Runnable {
        override fun run() {
            UpdateManager.check(applicationContext, force = false, silentOnly = true)
            handler.postDelayed(this, 60 * 60 * 1000L)
        }
    }

    // Turning the screen off ends an "open" session, so the next open asks the question again.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            StudyLockState.endSession(applicationContext)
            lastBlockedPackage = null
        }
    }
    private var screenReceiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poll)
        handler.removeCallbacks(updateCheck)
        handler.post(poll)
        handler.postDelayed(updateCheck, 30_000L)
        if (!screenReceiverRegistered) {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenReceiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // StudyLock's own screens never change what counts as the foreground app.
        if (pkg == applicationContext.packageName) return
        // The keyboard and system panels sit on top of apps; they are not apps the user switched to.
        if (pkg == "com.android.systemui" || pkg == keyboardPackage()) return
        if (pkg != currentPackage) {
            lastBlockedPackage = null
            // Leaving the protected apps ends an "open" session.
            if (pkg !in StudyLockState.selected(this)) StudyLockState.endSession(this)
        }
        currentPackage = pkg
        evaluate(pkg)
    }

    private fun evaluate(pkg: String?) {
        if (pkg == null || pkg !in StudyLockState.selected(this)) {
            lastBlockedPackage = null
            return
        }
        val state = StudyLockState.snapshot(this)
        if (state.phase == StudyLockState.Phase.OPEN) {
            lastBlockedPackage = null
            return
        }
        // LOCKED shows the lock screen; ASK shows "Have you finished studying?". Both use BlockActivity.
        if (lastBlockedPackage != pkg) {
            lastBlockedPackage = pkg
            startActivity(
                Intent(this, BlockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
                )
            )
        }
    }

    private fun keyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    override fun onInterrupt() { lastBlockedPackage = null }

    private fun stopWork() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(updateCheck)
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenOff) } catch (e: Exception) { /* already gone */ }
            screenReceiverRegistered = false
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopWork()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopWork()
        super.onDestroy()
    }
}
