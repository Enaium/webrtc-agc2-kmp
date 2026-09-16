/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.webrtc.agc2.examples.basic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val controller = Agc2LoopbackController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Agc2LoopbackScreen(
                    controller = controller,
                    onToggle = { toggleProcessing() },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.stop()
    }

    private fun toggleProcessing() {
        if (controller.isRunning) {
            controller.stop()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO,
                )
                return
            }
            controller.start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int,
    ) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                controller.start()
            } else {
                controller.status = "Microphone permission denied"
            }
        }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 100
    }
}

@Composable
fun Agc2LoopbackScreen(
    controller: Agc2LoopbackController,
    onToggle: () -> Unit,
) {
    // mutableStateOf-backed properties are already observable by Compose, so a
    // plain read is enough to trigger recomposition on change.
    val isRunning = controller.isRunning
    val agc2Enabled = controller.agc2Enabled
    val adaptiveDigitalEnabled = controller.adaptiveDigitalEnabled
    val inputVolumeControllerEnabled = controller.inputVolumeControllerEnabled
    val useInternalVad = controller.useInternalVad
    val autoApplyRecommendedVolume = controller.autoApplyRecommendedVolume
    val fixedGainDb = controller.fixedGainDb
    val referenceLevelDb = controller.referenceLevelDb
    val appliedInputVolume = controller.appliedInputVolume
    val recommendedInputVolume = controller.recommendedInputVolume
    val status = controller.status
    val error = controller.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "WebRTC AGC2 Loopback",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Plays a 440 Hz reference tone through the speaker, records the " +
                "microphone, and applies the AGC2 gain controller in real time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // ---- AGC2 switch ----
        // Switching AGC2 off plays the raw capture back, so the difference the
        // controller makes stays audible.
        ControlSwitch(
            label = "Gain control",
            checked = agc2Enabled,
            tag = "agc2Switch",
            onCheckedChange = { controller.agc2Enabled = it },
        )

        Spacer(Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        // ---- Config-level switches ----
        // These recreate the controller on the audio thread; the UI only owns
        // the flags.
        ControlSwitch(
            label = "Adaptive digital",
            checked = adaptiveDigitalEnabled,
            tag = "adaptiveDigitalSwitch",
            onCheckedChange = { controller.adaptiveDigitalEnabled = it },
        )

        Spacer(Modifier.height(8.dp))

        ControlSwitch(
            label = "Input volume controller",
            checked = inputVolumeControllerEnabled,
            tag = "inputVolumeControllerSwitch",
            onCheckedChange = { controller.inputVolumeControllerEnabled = it },
        )

        Spacer(Modifier.height(8.dp))

        ControlSwitch(
            label = "Internal VAD",
            checked = useInternalVad,
            tag = "internalVadSwitch",
            onCheckedChange = { controller.useInternalVad = it },
        )

        Spacer(Modifier.height(8.dp))

        ControlSwitch(
            label = "Apply recommended volume",
            checked = autoApplyRecommendedVolume,
            tag = "autoApplyVolumeSwitch",
            onCheckedChange = { controller.autoApplyRecommendedVolume = it },
        )

        Spacer(Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        // ---- Fixed gain slider ----
        ControlSlider(
            label = "Fixed gain: %.0f dB".format(fixedGainDb),
            value = fixedGainDb,
            valueRange = 0f..40f,
            tag = "fixedGainSlider",
            onValueChange = { controller.fixedGainDb = it },
        )

        Spacer(Modifier.height(16.dp))

        // ---- Reference tone slider ----
        ControlSlider(
            label = "Reference tone: %.0f dBFS".format(referenceLevelDb),
            value = referenceLevelDb,
            valueRange = -40f..0f,
            tag = "referenceLevelSlider",
            onValueChange = { controller.referenceLevelDb = it },
        )

        Spacer(Modifier.height(16.dp))

        // ---- Applied input volume slider ----
        // While the recommendation is applied, the audio thread overwrites
        // whatever this slider sets on the next update, so it is only editable
        // when the recommendation is not applied.
        ControlSlider(
            label = "Applied volume: $appliedInputVolume " +
                "(recommended: ${recommendedInputVolume ?: "-"})",
            value = appliedInputVolume.toFloat(),
            valueRange = 0f..255f,
            tag = "appliedVolumeSlider",
            enabled = !autoApplyRecommendedVolume,
            onValueChange = { controller.appliedInputVolume = it.toInt() },
        )

        Spacer(Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        // ---- Status ----
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Error: $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Start / Stop ----
        if (isRunning) {
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("startStopButton"),
            ) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("startStopButton"),
            ) {
                Text("Start")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ControlSwitch(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    tag: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}
