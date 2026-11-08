package com.studylock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class AppGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var lastBlockedPackage: String? = null

    // Re-checks once per second so a lock period that starts while a protected app is
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poll)
        handler.removeCallbacks(updateCheck)
        handler.post(poll)
        handler.postDelayed(updateCheck, 30_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // StudyLock's own screens never change what counts as the foreground app.
        if (pkg == applicationContext.packageName) return
        // The keyboard and system panels sit on top of apps; they are not apps the user switched to.
        if (pkg == "com.android.systemui" || pkg == keyboardPackage()) return
        if (pkg != currentPackage) lastBlockedPackage = null
        currentPackage = pkg
        evaluate(pkg)
    }

    private fun evaluate(pkg: String?) {
        if (pkg == null || pkg !in StudyLockState.selected(this)) {
            lastBlockedPackage = null
            return
        }
        val state = StudyLockState.snapshot(this)
        if (state.phase == StudyLockState.Phase.LOCKED) {
            if (lastBlockedPackage != pkg) {
                lastBlockedPackage = pkg
                startActivity(
                    Intent(this, BlockActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
                    )
                )
            }
        } else {
            lastBlockedPackage = null
        }
    }

    private fun keyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    override fun onInterrupt() { lastBlockedPackage = null }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(updateCheck)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(updateCheck)
        super.onDestroy()
    }
}
