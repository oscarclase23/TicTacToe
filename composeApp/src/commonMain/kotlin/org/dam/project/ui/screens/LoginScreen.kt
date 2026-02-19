package org.dam.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dam.project.client.GameClient

/**
 * Login screen.
 *
 * IMPORTANT: onLogin is a plain (String)->Unit lambda, NOT suspend.
 * Do NOT wrap it in scope.launch{} - the caller (App.kt) already handles
 * launching in a stable scope. Wrapping in another scope.launch here was
 * the original cause of the ForgottenCoroutineScopeException.
 */
@Composable
fun LoginScreen(
    gameClient: GameClient,
    onLogin: (String) -> Unit   // Plain lambda, NOT suspend
) {
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp).width(400.dp)
        ) {
            Text(
                text = "Enter the Battlefield",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Warrior Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank()) {
                        errorMessage = "Name cannot be empty!"
                    } else {
                        // Call directly - NO scope.launch needed here.
                        // onLogin is plain (String)->Unit, App.kt handles the coroutine.
                        onLogin(username)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Join Battle", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}