package com.studylock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shows either the lock screen (while locked) or the "Have you finished studying?" question. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back leaves the protected app instead of revealing it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        setContent { BlockScreen(onUnlocked = { finish() }) }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun BlockScreen(onUnlocked: () -> Unit) {
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
        val snap = remember(tick) { StudyLockState.snapshot(this@BlockActivity) }
        // The apps are open (answered Yes, or the cycle was stopped): nothing left to show.
        LaunchedEffect(snap.phase) { if (snap.phase == StudyLockState.Phase.OPEN) onUnlocked() }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9A6FF), background = Color(0xFF10111A), surface = Color(0xFF1A1B27))) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF10111A)).verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (snap.phase) {
                    StudyLockState.Phase.LOCKED -> LockContent(snap.remainingMs, onUnlocked)
                    StudyLockState.Phase.ASK -> QuestionContent(
                        onYes = {
                            StudyLockState.startOpen(this@BlockActivity)
                            onUnlocked()
                        },
                        onBreak = {
                            StudyLockState.startCycle(this@BlockActivity)
                            tick++
                        }
                    )
                    StudyLockState.Phase.OPEN -> Spacer(Modifier.height(1.dp))
                }
            }
        }
    }

    @Composable
    private fun QuestionContent(onYes: () -> Unit, onBreak: () -> Unit) {
        Text("📚", fontSize = 54.sp)
        Spacer(Modifier.height(20.dp))
        Text("Have you finished studying?", color = Color.White, fontSize = 26.sp)
        Spacer(Modifier.height(10.dp))
        Text("Your lock period is over.", color = Color(0xFFB9B9C8))
        Spacer(Modifier.height(32.dp))
        Button(onClick = onYes, modifier = Modifier.fillMaxWidth()) { Text("Yes, I've finished") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) { Text("I'm having a break") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Yes opens your apps for your allowed time. A break starts the lock again.",
            color = Color(0xFFB9B9C8), fontSize = 12.sp
        )
    }

    @Composable
    private fun LockContent(remainingMs: Long, onUnlocked: () -> Unit) {
        var challenge by remember { mutableStateOf("") }
        var entry by remember { mutableStateOf("") }
        var showChallenge by remember { mutableStateOf(false) }
        Text("🔒", fontSize = 54.sp)
        Spacer(Modifier.height(20.dp))
        Text("GO STUDY", color = Color.White, fontSize = 30.sp)
        Spacer(Modifier.height(10.dp))
        Text("Your selected apps are locked.", color = Color(0xFFB9B9C8))
        Spacer(Modifier.height(24.dp))
        Text(formatTime(remainingMs), color = Color(0xFFB9A6FF), fontSize = 36.sp)
        Text("until you can say you've finished studying", color = Color(0xFFB9B9C8), fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = {
            showChallenge = true
            challenge = StudyLockState.challenge(StudyLockState.challengeLength(this@BlockActivity))
            entry = ""
        }) { Text("Deactivate lock early") }
        if (showChallenge) {
            Spacer(Modifier.height(18.dp))
            Text("Type this new challenge exactly:", color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(challenge, color = Color(0xFFB9A6FF), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = entry,
                onValueChange = { value ->
                    if (value.length <= challenge.length && challenge.startsWith(value)) {
                        entry = value
                        if (entry == challenge) {
                            StudyLockState.startOpen(this@BlockActivity)
                            onUnlocked()
                        }
                    } else {
                        challenge = StudyLockState.challenge(StudyLockState.challengeLength(this@BlockActivity))
                        entry = ""
                    }
                },
                label = { Text("Enter challenge") },
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, autoCorrect = false),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Any incorrect character generates a completely new challenge.", color = Color(0xFFB9B9C8), fontSize = 11.sp)
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
