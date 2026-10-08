// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.applock.AppLockManager
import org.koin.compose.koinInject

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun AppLockScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appLockManager: AppLockManager = koinInject()
    var message by remember { mutableStateOf<String?>(null) }

    val activity = context as? FragmentActivity
    val prompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        message = null
                        appLockManager.unlock()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        message = errString.toString()
                    }

                    override fun onAuthenticationFailed() {
                        message = null
                    }
                },
            )
        }
    }

    fun authenticate() {
        message = null
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_title))
            .setSubtitle(context.getString(R.string.app_lock_hint))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        if (prompt != null) {
            prompt.authenticate(info)
        } else {
            message = context.getString(R.string.app_lock_no_credentials)
        }
    }

    LaunchedEffect(Unit) { authenticate() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_lock_locked),
            style = MaterialTheme.typography.titleMedium,
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { authenticate() }) {
            Text(stringResource(R.string.app_lock_unlock))
        }
    }
}
