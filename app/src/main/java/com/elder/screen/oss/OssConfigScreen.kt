// 对应 docs/v0.10.0.md §6.7：OssConfigScreen（阿里云 OSS 配置入口；6 字段输入 + 测试 + 保存 + 立即同步）。
// §4.6 表格:不引入新权限(Internet/NetworkState 已声明);§4.2 最小 24sp 起步硬约束保留。
package com.elder.android.screen.oss

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.oss.OssSyncWorker
import com.elder.android.ui.component.ElderToast
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssConfigScreen(
    onBack: () -> Unit,
    vm: OssConfigViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // v0.11.x UI agent M-2:测试连接失败用 ElderToast(§4.8 错误 Toast)
    // 成功 / 保存成功 / 触发同步保留 android.widget.Toast(非错误,符合 prd §4.8 不应走 ElderToast)
    var testError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.oss_sync_title), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.BgGray),
            )
        },
        containerColor = BrandColor.BgGray,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.Md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            // 顶部说明卡(单行 28sp 主色,符合 §4.2)
            Text(
                text = stringResource(R.string.oss_sync_summary),
                fontSize = FontSize.body(),
                color = BrandColor.TextSecondary,
            )

            // 6 字段输入
            OssField(label = R.string.oss_endpoint, value = state.endpoint, onChange = vm::onEndpointChange, enabled = !state.busy)
            OssField(label = R.string.oss_bucket, value = state.bucket, onChange = vm::onBucketChange, enabled = !state.busy)
            OssField(label = R.string.oss_region, value = state.region, onChange = vm::onRegionChange, enabled = !state.busy)
            OssField(label = R.string.oss_access_key_id, value = state.accessKeyId, onChange = vm::onAccessKeyIdChange, enabled = !state.busy)
            OssField(label = R.string.oss_access_key_secret, value = state.accessKeySecret, onChange = vm::onAccessKeySecretChange, isSecret = true, enabled = !state.busy)
            OssField(label = R.string.oss_sts_token, value = state.stsToken, onChange = vm::onStsTokenChange, isSecret = true, optional = true, enabled = !state.busy)
            OssField(label = R.string.oss_prefix, value = state.prefix, onChange = vm::onPrefixChange, enabled = !state.busy)

            // 测试按钮
            Button(
                onClick = {
                    scope.launch {
                        val ok = vm.testConnection()
                        if (ok) {
                            // 成功 → 短暂 android.widget.Toast(非错误,prd §4.8)
                            Toast.makeText(context, R.string.oss_test_ok, Toast.LENGTH_SHORT).show()
                        } else {
                            // 失败 → ElderToast(§4.8 错误 Toast,4s 底部 Error500)
                            testError = context.getString(R.string.oss_test_fail)
                        }
                    }
                },
                enabled = !state.busy && state.isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Corner.Button),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            ) {
                Text(text = stringResource(R.string.oss_test_button), fontSize = FontSize.body(), fontWeight = FontWeight.Bold)
            }

            // 保存按钮
            Button(
                onClick = {
                    scope.launch {
                        vm.save()
                        Toast.makeText(context, R.string.oss_save_ok, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !state.busy && state.isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Corner.Button),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            ) {
                Text(text = stringResource(R.string.common_save), fontSize = FontSize.body(), fontWeight = FontWeight.Bold)
            }

            // 立即同步
            Button(
                onClick = {
                    OssSyncWorker.enqueueOnce(context)
                    Toast.makeText(context, R.string.oss_sync_triggered, Toast.LENGTH_SHORT).show()
                },
                enabled = !state.busy && state.isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(Corner.Button),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColor.BgGray, contentColor = BrandColor.TextPrimary),
            ) {
                Text(text = stringResource(R.string.oss_sync_now), fontSize = FontSize.body(), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(Spacing.Lg))

            // 最近同步结果(读 OssConfigEntity.last_sync_result JSON 简化展示)
            // v0.11.x UI agent M-2:测试失败的 ElderToast
            if (!testError.isNullOrBlank()) {
                ElderToast(message = testError, onDismiss = { testError = null })
            }

            state.lastSyncResult?.let { result ->
                Text(text = stringResource(R.string.oss_last_sync, result), fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
            }
        }
    }
}

@Composable
private fun OssField(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    isSecret: Boolean = false,
    optional: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            val base = stringResource(label)
            Text(text = if (optional) "$base（可选）" else base, fontSize = FontSize.body())
        },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}
