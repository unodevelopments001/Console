package com.unodevelopments.cblsshmngr.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val store = (context.applicationContext as CabalApp).themeStore
    val enabled by store.appLock.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    var unlockedAt by remember { mutableLongStateOf(0L) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, prompting, unlockedAt) {
        val observer = LifecycleEventObserver { _, event ->
            val justUnlocked = SystemClock.elapsedRealtime() - unlockedAt < 1_500
            if (event == Lifecycle.Event.ON_STOP && !prompting && !justUnlocked) unlocked = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (activity == null || !enabled || unlocked) {
        content()
        return
    }
    val available = remember(activity) {
        try {
            val manager = activity.getSystemService(BiometricManager::class.java)
            manager?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: SecurityException) {
            false
        }
    }
    LockScreen(
        available = available,
        prompting = prompting,
        onUnlock = {
            prompting = true
            authenticate(
                activity,
                onSuccess = {
                    unlockedAt = SystemClock.elapsedRealtime()
                    unlocked = true
                    prompting = false
                },
                onFinished = { prompting = false },
            )
        },
        onDisable = { store.setAppLock(false) },
    )
    LaunchedEffect(available) {
        if (!available || prompting) return@LaunchedEffect
        prompting = true
        authenticate(
            activity,
            onSuccess = {
                unlockedAt = SystemClock.elapsedRealtime()
                unlocked = true
                prompting = false
            },
            onFinished = { prompting = false },
        )
    }
}

@Composable
private fun LockScreen(
    available: Boolean,
    prompting: Boolean,
    onUnlock: () -> Unit,
    onDisable: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(if (available) R.string.unlock_detail else R.string.unlock_unavailable),
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (available) {
            Button(onClick = onUnlock, enabled = !prompting) {
                Text(stringResource(R.string.unlock))
            }
        } else {
            TextButton(onClick = onDisable) {
                Text(stringResource(R.string.app_lock_disable))
            }
        }
    }
}

private fun authenticate(activity: Activity, onSuccess: () -> Unit, onFinished: () -> Unit) {
    try {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.unlock_title))
            .setSubtitle(activity.getString(R.string.unlock_detail))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFinished()
                }
            },
        )
    } catch (_: Exception) {
        onFinished()
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
