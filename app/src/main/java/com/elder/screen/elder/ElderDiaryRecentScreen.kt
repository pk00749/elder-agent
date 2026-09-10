// §3.1.7 老人端「今日记录」时间轴屏（v3.0 MVP 版）
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.component.ElderEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderDiaryRecentScreen(
    onBack: () -> Unit,
    vm: ElderDiaryRecentViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.CardWhite) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_recent_screen_title), fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                FilterChip(
                    selected = state.range == DiaryRecentRange.TODAY,
                    onClick = { vm.setRange(DiaryRecentRange.TODAY) },
                    label = { Text(stringResource(R.string.diary_recent_today_tab), fontSize = FontSize.body()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColor.Brand500,
                        selectedLabelColor = Color.White,
                    ),
                )
                FilterChip(
                    selected = state.range == DiaryRecentRange.LAST_7_DAYS,
                    onClick = { vm.setRange(DiaryRecentRange.LAST_7_DAYS) },
                    label = { Text(stringResource(R.string.diary_recent_7d_tab), fontSize = FontSize.body()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColor.Brand500,
                        selectedLabelColor = Color.White,
                    ),
                )
            }

            if (state.entries.isEmpty()) {
                // PR #4：空态走标准 ElderEmptyState 组件（prd.md §4.7 居中 + ▶ TTS 按钮）
                // TTS 由 ElderDiaryRecentViewModel.ttsPlay 注入，保留后续接入千问 TTS 的扩展点
                val emptyMsg = when (state.range) {
                    DiaryRecentRange.TODAY -> stringResource(R.string.empty_no_diary)
                    DiaryRecentRange.LAST_7_DAYS -> stringResource(R.string.empty_no_diary_7d)
                }
                ElderEmptyState(
                    text = emptyMsg,
                    onTtsClick = { vm.ttsPlay(emptyMsg) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
                ) {
                    items(items = state.entries, key = { it.id }) { entry: DiaryEntryEntity ->
                        DiaryRow(
                            entry = entry,
                            onEdit = { vm.startEdit(entry.id, entry.text) },
                        )
                    }
                }
            }
        }
    }

    val editingId = state.editingId
    if (editingId != null) {
        AlertDialog(
            onDismissRequest = vm::cancelEdit,
            title = { Text(stringResource(R.string.diary_recent_edit_dialog_title), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = state.editingDraft,
                    onValueChange = vm::updateDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.EditDialogFieldHeight),
                    textStyle = TextStyle(fontSize = FontSize.BodyInputSp.sp),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.saveEdit() }) {
                    Text(stringResource(R.string.common_save), color = BrandColor.Brand500, fontSize = FontSize.body())
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelEdit) {
                    Text(stringResource(R.string.common_cancel), color = BrandColor.TextSecondary, fontSize = FontSize.body())
                }
            },
        )
    }
}

@Composable
private fun DiaryRow(entry: DiaryEntryEntity, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        color = BrandColor.BgGray,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(entry.createdAt),
                    fontSize = FontSize.caption(),
                    color = BrandColor.TextSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.Xs))
                Text(
                    text = entry.text,
                    fontSize = FontSize.body(),
                    color = BrandColor.TextPrimary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.diary_recent_edit_icon_desc), tint = BrandColor.Brand500)
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))
