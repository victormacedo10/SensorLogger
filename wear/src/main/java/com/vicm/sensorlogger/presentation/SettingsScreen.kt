package com.vicm.sensorlogger.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.vicm.sensorlogger.SensorService

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // State for height and weight as Strings for input
    // Height is stored in meters in SensorService, displayed in cm
    var heightText by remember { mutableStateOf(String.format("%.1f", SensorService.userHeight)) }
    var weightText by remember { mutableStateOf(String.format("%.1f", SensorService.userWeight)) }

    var editingHeight by remember { mutableStateOf(false) }
    var editingWeight by remember { mutableStateOf(false) }

    if (editingHeight) {
        NumberPad(
            initialValue = heightText,
            onResult = {
                heightText = it
                editingHeight = false
            },
            onCancel = { editingHeight = false }
        )
    } else if (editingWeight) {
        NumberPad(
            initialValue = weightText,
            onResult = {
                weightText = it
                editingWeight = false
            },
            onCancel = { editingWeight = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Height label and input
            Text(
                text = "Height (cm)",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = heightText,
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingHeight = true }
                    .padding(vertical = 4.dp)
            )

            // Weight label and input
            Text(
                text = "Weight (kg)",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = weightText,
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingWeight = true }
                    .padding(vertical = 4.dp)
            )

            Button(
                onClick = {
                    val h = heightText.toDoubleOrNull()
                    val w = weightText.toDoubleOrNull()

                    if (h != null && w != null) {
                        SensorService.updateUserMetrics(context, h, w)
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun NumberPad(
    initialValue: String,
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number Grid Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NumButton("1") { value = appendNum(value, "1") }
                NumButton("2") { value = appendNum(value, "2") }
                NumButton("3") { value = appendNum(value, "3") }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NumButton("4") { value = appendNum(value, "4") }
                NumButton("5") { value = appendNum(value, "5") }
                NumButton("6") { value = appendNum(value, "6") }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NumButton("7") { value = appendNum(value, "7") }
                NumButton("8") { value = appendNum(value, "8") }
                NumButton("9") { value = appendNum(value, "9") }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NumButton(".") { 
                    if (!value.contains(".")) {
                        value += "."
                    }
                }
                NumButton("0") { value = appendNum(value, "0") }
                NumButton("⌫", isAction = true) { 
                    if (value.isNotEmpty()) {
                        value = value.dropLast(1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Actions Column
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(38.dp)
            ) {
                Text("✕")
            }
            Button(
                onClick = { onResult(value) },
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier.size(38.dp)
            ) {
                Text("✓")
            }
        }
    }
}

@Composable
fun NumButton(text: String, isAction: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isAction) MaterialTheme.colors.surface else MaterialTheme.colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = if (isAction) MaterialTheme.colors.error else MaterialTheme.colors.onSurface
        )
    }
}

fun appendNum(current: String, num: String): String {
    if (current == "0" && num != ".") return num
    return current + num
}
