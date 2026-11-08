package com.studylock.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    data class AppChoice(val label: String, val packageName: String)

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    /** Validates the three numbers; returns an error message, or null when they are fine. */
    private fun validate(l: Int?, u: Int?, c: Int?): String? =
        if (l == null || l < 30 || u == null || u !in 1..30 || c == null || c !in 20..300)
            "Lock ≥ 30, usage 1–30, challenge 20–300." else null

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun isProtectionOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = ComponentName(this, AppGuardService::class.java)
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) || it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    @Composable
    private fun MainScreen() {
        var lock by remember { mutableStateOf(StudyLockState.lockMinutes(this@MainActivity).toString()) }
        var use by remember { mutableStateOf(StudyLockState.useMinutes(this@MainActivity).toString()) }
        var challenge by remember { mutableStateOf(StudyLockState.challengeLength(this@MainActivity).toString()) }
        val apps = remember { loadApps() }
        var selected by remember { mutableStateOf(StudyLockState.selected(this@MainActivity)) }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
        val resumed = resumeTick.intValue
        val enabled = StudyLockState.isEnabled(this@MainActivity)
        val snap = remember(tick, resumed) { StudyLockState.snapshot(this@MainActivity) }
        val protectionOn = remember(tick, resumed) { isProtectionOn() }
        val locked = enabled && snap.phase == StudyLockState.Phase.LOCKED

        // Saves the typed values; returns false (after showing why) when they are not valid.
        fun save(): Boolean {
            val l = lock.toIntOrNull(); val u = use.toIntOrNull(); val c = challenge.toIntOrNull()
            val error = validate(l, u, c)
            if (error != null) { toast(error); return false }
            StudyLockState.saveSettings(this@MainActivity, l!!, u!!, c!!, selected)
            return true
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9A6FF), background = Color(0xFF10111A), surface = Color(0xFF1A1B27))) {
            Scaffold(containerColor = Color(0xFF10111A)) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("STUDYLOCK", color = Color.White, fontSize = 27.sp)
                        Text("Make distraction harder.", color = Color(0xFFB9B9C8))
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1B27))) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    if (!enabled) "NOT STARTED" else if (locked) "LOCKED" else "USAGE WINDOW",
                                    color = Color(0xFFB9A6FF)
                                )
                                Text(if (enabled) formatTime(snap.remainingMs) else "Ready to configure", color = Color.White, fontSize = 28.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (protectionOn) "App protection is on." else "App protection is off. Turn it on in Accessibility settings.",
                                    color = if (protectionOn) Color(0xFF6FD39A) else Color(0xFFF2B866),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    item { Text("YOUR TIMERS", color = Color.White, fontSize = 18.sp) }
                    item {
                        OutlinedTextField(
                            value = lock, enabled = !locked,
                            onValueChange = { lock = it.filter(Char::isDigit).take(5) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Lock duration (minutes) — minimum 30") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = use, enabled = !locked,
                            onValueChange = { use = it.filter(Char::isDigit).take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Allowed usage (minutes) — maximum 30") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = challenge, enabled = !locked,
                            onValueChange = { challenge = it.filter(Char::isDigit).take(3) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Challenge length (default 100)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Text("CHOOSE APPS TO PROTECT", color = Color.White, fontSize = 18.sp) }
                    if (locked) item { Text("Timers and apps cannot be changed during a lock period.", color = Color(0xFFF2B866), fontSize = 12.sp) }
                    items(apps, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = app.packageName in selected,
                                enabled = !locked,
                                onCheckedChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName }
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { if (save()) toast("Settings saved") },
                            enabled = !locked,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save settings") }
                    }
                    item {
                        OutlinedButton(
                            onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable app protection in Accessibility settings") }
                    }
                    item {
                        Button(
                            onClick = {
                                if (locked) toast("Already locked.")
                                else if (selected.isEmpty()) toast("Choose at least one app first.")
                                else if (save()) {
                                    StudyLockState.startCycle(this@MainActivity)
                                    tick++
                                    if (!protectionOn) toast("Cycle started, but app protection is off. Turn it on in Accessibility settings.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start automatic cycle") }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                if (locked) toast("You can stop the cycle during a usage window. To get one now, use \"Deactivate lock early\" on the lock screen.")
                                else { StudyLockState.stopCycle(this@MainActivity); tick++ }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop cycle") }
                    }
                    item {
                        Text(
                            "Cycle: locked → automatically allowed → locked again. You can uninstall StudyLock at any time.",
                            color = Color(0xFFB9B9C8), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    private fun loadApps(): List<AppChoice> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
            val p = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (p == packageName) return@mapNotNull null
            AppChoice(ri.loadLabel(pm).toString(), p)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}
