// §3.2 / §6.1 登录：SMS 验证码
package com.elder.android.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.data.TokenStore
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun LoginScreen(role: String, onLoggedIn: (TokenStore.Snapshot) -> Unit, onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var canResendAt by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isElder = role == "elder"

    fun submitSendSms() {
        scope.launch {
            error = null
            runCatching {
                ServiceLocator.apiClient.accountApi.sendSmsCode(
                    com.elder.android.network.dto.SmsCodeRequest(phone),
                )
            }.onSuccess { canResendAt = System.currentTimeMillis() + 60_000 }
                .onFailure { e ->
                    error = when ((e as? HttpException)?.code()) {
                        429 -> stringResource(R.string.toast_server_busy)
                        else -> stringResource(R.string.toast_network_error)
                    }
                }
        }
    }

    LaunchedEffect(Unit) { submitSendSms() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite)
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isElder) stringResource(R.string.identity_elder) else stringResource(R.string.identity_family),
            fontSize = FontSize.title(),
            color = BrandColor.TextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.Xl))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
            label = { Text(stringResource(R.string.login_phone_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.login_code_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        if (canResendAt > System.currentTimeMillis()) {
            val sec = ((canResendAt - System.currentTimeMillis()) / 1000).toInt()
            Text(
                text = stringResource(R.string.login_resend_in, sec),
                fontSize = FontSize.caption(),
                color = BrandColor.TextSecondary,
            )
        } else {
            TextButton(onClick = ::submitSendSms) {
                Text(stringResource(R.string.login_send_code))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Button(
            onClick = {
                scope.launch {
                    error = null
                    runCatching {
                        ServiceLocator.apiClient.accountApi.login(
                            com.elder.android.network.dto.LoginRequest(phone, code),
                        )
                    }.onSuccess { resp ->
                        val elderId = if (resp.role == "elder") resp.userId else null
                        ServiceLocator.tokenStore.save(
                            resp.token, resp.userId, resp.role, elderId,
                        )
                        onLoggedIn(TokenStore.Snapshot(resp.token, resp.userId, resp.role, elderId))
                    }.onFailure { e ->
                        error = if ((e as? HttpException)?.code() == 401) {
                            stringResource(R.string.login_invalid_code)
                        } else {
                            stringResource(R.string.toast_network_error)
                        }
                    }
                }
            },
            enabled = phone.length == 11 && code.length == 6,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = BrandColor.CardWhite,
            ),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            Text(stringResource(R.string.login_button), fontSize = FontSize.body())
        }
        error?.let { msg ->
            Spacer(modifier = Modifier.height(Spacing.Md))
            Text(msg, color = BrandColor.Error500, fontSize = FontSize.caption())
        }
        Spacer(modifier = Modifier.height(Spacing.Md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        }
    }
}
