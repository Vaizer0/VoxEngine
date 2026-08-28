package com.voxengine.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxengine.engine.VoiceInfo
import com.voxengine.reader.RoleProfile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsPanel(
    selectedVoiceName: String,
    selectedStyle: String,
    voices: List<VoiceInfo>,
    voiceExpanded: Boolean,
    onVoiceExpandedChange: (Boolean) -> Unit,
    onVoiceSelected: (VoiceInfo) -> Unit,
    onStyleChange: (String) -> Unit,
    roleEnabled: Boolean,
    onRoleEnabledChange: (Boolean) -> Unit,
    roleProfile: RoleProfile,
    onNarrationVoiceChange: (VoiceInfo?) -> Unit,
    onNarrationStyleChange: (String) -> Unit,
    onDialogueVoiceChange: (VoiceInfo?) -> Unit,
    onDialogueStyleChange: (String) -> Unit,
    onCharacterSave: (String, String, String) -> Unit,
    onCharacterRemove: (String) -> Unit,
    readerGapMs: Int,
    readerSleepMinutes: Int,
    readerStopAfterChapters: Int,
    conservativeRequestIntervalMs: Int,
    retryCount: Int,
    retryBaseDelayMs: Int,
    onGapChange: (Int) -> Unit,
    onSleepChange: (Int) -> Unit,
    onStopAfterChaptersChange: (Int) -> Unit,
    onConservativeRequestIntervalChange: (Int) -> Unit,
    onRetryCountChange: (Int) -> Unit,
    onRetryBaseDelayChange: (Int) -> Unit,
    onGapChangeFinished: () -> Unit,
    onSleepChangeFinished: () -> Unit,
    onStopAfterChaptersChangeFinished: () -> Unit,
    onConservativeRequestIntervalChangeFinished: () -> Unit,
    onRetryCountChangeFinished: () -> Unit,
    onRetryBaseDelayChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.48f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Listen Settings", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        ExposedDropdownMenuBox(expanded = voiceExpanded, onExpandedChange = onVoiceExpandedChange) {
            OutlinedTextField(
                value = selectedVoiceName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Voice") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = voiceExpanded, onDismissRequest = { onVoiceExpandedChange(false) }) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text("${voice.uiName} - ${voice.description}") },
                        onClick = { onVoiceSelected(voice) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = selectedStyle,
            onValueChange = onStyleChange,
            label = { Text("Style") },
            placeholder = { Text("e.g. gentle, Cantonese, Sichuan") },
            modifier = Modifier.fillMaxWidth()
        )
        // ---- 分角色朗读 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Per-role reading", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Use different voices for narration and dialogue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = roleEnabled, onCheckedChange = onRoleEnabledChange)
        }
        if (roleEnabled) {
            RoleVoicePicker(
                label = "Narration voice",
                selectedName = roleProfile.narration.voice ?: "Default (same as main voice)",
                voices = voices,
                onSelected = onNarrationVoiceChange
            )
            OutlinedTextField(
                value = roleProfile.narration.style ?: "",
                onValueChange = onNarrationStyleChange,
                label = { Text("Narration style (optional)") },
                placeholder = { Text("Leave empty to use default style") },
                modifier = Modifier.fillMaxWidth()
            )
            RoleVoicePicker(
                label = "Dialogue voice",
                selectedName = roleProfile.dialogue.voice ?: "Default (same as main voice)",
                voices = voices,
                onSelected = onDialogueVoiceChange
            )
            OutlinedTextField(
                value = roleProfile.dialogue.style ?: "",
                onValueChange = onDialogueStyleChange,
                label = { Text("Dialogue style (optional)") },
                placeholder = { Text("Leave empty to use default style") },
                modifier = Modifier.fillMaxWidth()
            )
            CharacterVoiceEditor(
                roleProfile = roleProfile,
                voices = voices,
                onSave = onCharacterSave,
                onRemove = onCharacterRemove
            )
        }
        Text("Inter-paragraph gap: ${readerGapMs}ms", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = readerGapMs.toFloat(),
            onValueChange = { onGapChange(it.roundToInt()) },
            onValueChangeFinished = onGapChangeFinished,
            valueRange = 0f..3000f
        )
        Text("Clone/design request interval: ${conservativeRequestIntervalMs}ms", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = conservativeRequestIntervalMs.toFloat(),
            onValueChange = { onConservativeRequestIntervalChange(it.roundToInt()) },
            onValueChangeFinished = onConservativeRequestIntervalChangeFinished,
            valueRange = 500f..30000f
        )
        Text("Failure retry count: ${retryCount}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = retryCount.toFloat(),
            onValueChange = { onRetryCountChange(it.roundToInt()) },
            onValueChangeFinished = onRetryCountChangeFinished,
            valueRange = 0f..8f,
            steps = 7
        )
        Text("Retry base delay: ${retryBaseDelayMs}ms", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = retryBaseDelayMs.toFloat(),
            onValueChange = { onRetryBaseDelayChange(it.roundToInt()) },
            onValueChangeFinished = onRetryBaseDelayChangeFinished,
            valueRange = 500f..15000f
        )
        Text("Scheduled stop: " + if (readerSleepMinutes == 0) "Off" else "${readerSleepMinutes} minutes", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = readerSleepMinutes.toFloat(),
            onValueChange = { onSleepChange(it.roundToInt()) },
            onValueChangeFinished = onSleepChangeFinished,
            valueRange = 0f..180f,
            steps = 17
        )
        Text("Stop after playing chapters: " + if (readerStopAfterChapters == 0) "Off" else "${readerStopAfterChapters} chapters", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = readerStopAfterChapters.toFloat(),
            onValueChange = { onStopAfterChaptersChange(it.roundToInt()) },
            onValueChangeFinished = onStopAfterChaptersChangeFinished,
            valueRange = 0f..20f,
            steps = 19
        )
        Spacer(Modifier.height(12.dp))
    }
}
