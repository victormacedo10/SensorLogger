package com.vicm.sensorlogger.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.vicm.sensorlogger.SettingsManager

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var height by remember { mutableStateOf(SettingsManager.getHeight(context)) }
    var weight by remember { mutableStateOf(SettingsManager.getWeight(context)) }
    
    var editingMode by remember { mutableStateOf<EditingMode?>(null) }

    if (editingMode != null) {
        val isHeight = editingMode == EditingMode.HEIGHT
        val initialVal = if (isHeight) height else weight
        
        NumberPadScreen(
            initialValue = if (initialVal == 1.0f) "" else initialVal.toString(),
            title = if (isHeight) "Height (cm)" else "Weight (kg)",
            onResult = { result ->
                val floatVal = result.toFloatOrNull() ?: 1.0f
                if (isHeight) {
                    height = floatVal
                    SettingsManager.saveHeight(context, floatVal)
                } else {
                    weight = floatVal
                    SettingsManager.saveWeight(context, floatVal)
                }
                editingMode = null
            },
            onCancel = { editingMode = null }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Settings", style = MaterialTheme.typography.title2)
            Spacer(modifier = Modifier.height(10.dp))
            
            Button(
                onClick = { editingMode = EditingMode.HEIGHT },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Height", style = MaterialTheme.typography.caption2)
                    Text("$height cm", style = MaterialTheme.typography.body1)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { editingMode = EditingMode.WEIGHT },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Weight", style = MaterialTheme.typography.caption2)
                    Text("$weight kg", style = MaterialTheme.typography.body1)
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            CompactButton(
                onClick = onBack,
                colors = ButtonDefaults.primaryButtonColors()
            ) {
                Text("Done")
            }
        }
    }
}

enum class EditingMode { HEIGHT, WEIGHT }

@Composable
fun NumberPadScreen(
    initialValue: String,
    title: String,
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.caption2)
        Text(
            text = text.ifEmpty { "0" },
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        
        val buttonSize = 34.dp
        val fontSize = 14.sp

        // Keypad rows
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeypadButton("1", buttonSize, fontSize) { text += "1" }
            KeypadButton("2", buttonSize, fontSize) { text += "2" }
            KeypadButton("3", buttonSize, fontSize) { text += "3" }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeypadButton("4", buttonSize, fontSize) { text += "4" }
            KeypadButton("5", buttonSize, fontSize) { text += "5" }
            KeypadButton("6", buttonSize, fontSize) { text += "6" }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeypadButton("7", buttonSize, fontSize) { text += "7" }
            KeypadButton("8", buttonSize, fontSize) { text += "8" }
            KeypadButton("9", buttonSize, fontSize) { text += "9" }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeypadButton(".", buttonSize, fontSize) { if (!text.contains(".")) text += "." }
            KeypadButton("0", buttonSize, fontSize) { text += "0" }
            KeypadButton("Del", buttonSize, 10.sp) { if (text.isNotEmpty()) text = text.dropLast(1) }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CompactButton(onClick = onCancel, colors = ButtonDefaults.secondaryButtonColors()) {
                Text("X")
            }
            CompactButton(onClick = { onResult(text) }, colors = ButtonDefaults.primaryButtonColors()) {
                Text("OK")
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        Text(text, fontSize = fontSize, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
