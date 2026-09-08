// §3.2.3 / §3.2.4 设置服药提醒 / 设置就医提醒
// MVP 表单：medication (药名 + 剂量 + 时间 + 重复) | appointment (医院 + 科室 + 日期 + 提前)
package com.elder.android.screen.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.ReminderCreateRequest
import kotlinx.coroutines.launch

@Composable
fun FamilyReminderCreateScreen(
    initialType: String,
    elderId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val isMedication = initialType == "medication"
    val titleRes = if (isMedication) R.string.reminder_create_medication else R.string.reminder_create_appointment
    val doseRes = R.string.reminder_med_dose
    val timeRes = R.string.reminder_time_label
    val repeatRes = R.string.reminder_repeat_label
    val hospitalRes = R.string.reminder_hospital
    val deptRes = R.string.reminder_department
    val datetimeRes = R.string.reminder_datetime
    val advanceRes = R.string.reminder_advance_label
    val noteRes = R.string.reminder_note_optional

    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("pill") }
    var time by remember { mutableStateOf("08:00") }
    var repeat by remember { mutableStateOf("daily") }
    var hospital by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    var dt by remember { mutableStateOf("") }
    var advance by remember { mutableStateOf(60) }
    var note by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Spacer(Modifier.weight(1f))
        }
        Text(stringResource(titleRes), fontSize = FontSize.title(), color = BrandColor.TextPrimary)
        Spacer(Modifier.height(Spacing.Lg))
        if (isMedication) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text(stringResource(R.string.reminder_med_name)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.Md))
            DosePicker(current = dose, onChange = { dose = it })
            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(value = time, onValueChange = { v -> if (v.length <= 5 && (v.lastOrNull()?.isDigit() != false || v.last() == ':')) time = v }, label = { Text(stringResource(timeRes)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.Md))
            RepeatPicker(current = repeat, onChange = { repeat = it })
        } else {
            OutlinedTextField(value = hospital, onValueChange = { hospital = it.take(20) }, label = { Text(stringResource(hospitalRes)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(value = dept, onValueChange = { dept = it.take(20) }, label = { Text(stringResource(deptRes)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(value = dt, onValueChange = { dt = it }, label = { Text(stringResource(datetimeRes)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.Md))
            AdvancePicker(current = advance, onChange = { advance = it })
        }
        Spacer(Modifier.height(Spacing.Md))
        OutlinedTextField(value = note, onValueChange = { v -> note = if (v.length <= 100) v else v.take(100) }, label = { Text(stringResource(noteRes)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { showConfirm = true },
            enabled = if (isMedication) name.isNotBlank() && time.matches(Regex("^\\d{1,2}:\\d{2}$")) else hospital.isNotBlank() && dept.isNotBlank() && dt.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
        ) { Text(stringResource(R.string.common_save), fontSize = FontSize.body()) }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        val payload: Map<String, Any?> = if (isMedication) mapOf(
                            "med_name" to name,
                            "dosage" to mapOf("type" to dose),
                            "schedule" to listOf(mapOf("time" to time, "repeat" to repeat)),
                            "note" to (note.ifBlank { null }),
                        ) else mapOf(
                            "hospital" to hospital,
                            "department" to dept,
                            "datetime" to (if (dt.length == 16) "${dt}:00" else dt),
                            "advance_remind_min" to advance,
                            "repeat" to "none",
                            "note" to (note.ifBlank { null }),
                        )
                        runCatching {
                            ServiceLocator.apiClient.reminderApi.createReminder(
                                ReminderCreateRequest(
                                    elderId = elderId,
                                    type = initialType,
                                    payload = payload,
                                ),
                            )
                        }.onSuccess { onSaved() }
                    }
                }) { Text(stringResource(R.string.reminder_summary_sheet_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.reminder_summary_edit))
                }
            },
            title = { Text(stringResource(R.string.reminder_summary_sheet_title)) },
            text = {
                Column {
                    if (isMedication) {
                        Text("$name", fontSize = FontSize.body())
                        Text("${stringResource(doseRes)}: ${stringResource(doseStringRes(dose))}")
                        Text("${stringResource(timeRes)}: $time")
                    } else {
                        Text("$hospital $dept", fontSize = FontSize.body())
                        Text("${stringResource(datetimeRes)}: $dt")
                        Text("${stringResource(advanceRes)}: $advance 分钟")
                    }
                }
            },
        )
    }
}

@Composable
private fun DosePicker(current: String, onChange: (String) -> Unit) {
    Row {
        listOf("pill", "half", "spoon").forEach { dose ->
            Button(
                onClick = { onChange(dose) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (current == dose) BrandColor.Brand500 else BrandColor.BgGray,
                    contentColor = if (current == dose) BrandColor.CardWhite else BrandColor.TextPrimary,
                ),
                modifier = Modifier.padding(end = Spacing.Sm),
            ) { Text(stringResource(doseStringRes(dose))) }
        }
    }
}

@Composable
private fun RepeatPicker(current: String, onChange: (String) -> Unit) {
    Row {
        listOf("daily", "weekdays", "custom").forEach { repeat ->
            Button(
                onClick = { onChange(repeat) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (current == repeat) BrandColor.Brand500 else BrandColor.BgGray,
                    contentColor = if (current == repeat) BrandColor.CardWhite else BrandColor.TextPrimary,
                ),
                modifier = Modifier.padding(end = Spacing.Sm),
            ) { Text(stringResource(repeatStringRes(repeat))) }
        }
    }
}

@Composable
private fun AdvancePicker(current: Int, onChange: (Int) -> Unit) {
        Row {
        listOf(30, 60, 120).forEach { mins ->
            Button(
                onClick = { onChange(mins) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (current == mins) BrandColor.Brand500 else BrandColor.BgGray,
                    contentColor = if (current == mins) BrandColor.CardWhite else BrandColor.TextPrimary,
                ),
                modifier = Modifier.padding(end = Spacing.Sm),
            ) { Text("$mins") }
        }
    }
}

private fun doseStringRes(dose: String): Int = when (dose) {
    "pill" -> R.string.reminder_dose_pill
    "half" -> R.string.reminder_dose_half
    "spoon" -> R.string.reminder_dose_spoon
    else -> R.string.reminder_dose_pill
}

private fun repeatStringRes(repeat: String): Int = when (repeat) {
    "daily" -> R.string.reminder_repeat_daily
    "weekdays" -> R.string.reminder_repeat_weekdays
    "custom" -> R.string.reminder_repeat_custom
    else -> R.string.reminder_repeat_daily
}
