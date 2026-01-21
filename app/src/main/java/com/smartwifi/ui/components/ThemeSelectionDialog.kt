package com.smartwifi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun ThemeSelectionDialog(
    currentBackground: Long,
    currentAccent: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    var selectedBackground by remember { mutableStateOf(currentBackground) }
    var selectedAccent by remember { mutableStateOf(currentAccent) }

    // Predefined Colors
    val backgrounds = listOf(
        0xFF121212 to "Standard", // Default
        0xFF000000 to "Pure Black",
        0xFF1F2937 to "Slate"
    )

    val accents = listOf(
        0xFFBB86FC, // Purple (Default)
        0xFF03DAC6, // Teal
        0xFFCF6679, // Red
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFF2196F3, // Blue
        0xFFE91E63  // Pink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Theme") },
        text = {
            Column {
                Text("Theme Background Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    backgrounds.forEach { (colorLong, label) ->
                        ColorCircle(
                            color = Color(colorLong),
                            isSelected = selectedBackground == colorLong,
                            onClick = { selectedBackground = colorLong }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Theme Accent Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    accents.forEach { colorLong ->
                        ColorCircle(
                            color = Color(colorLong),
                            isSelected = selectedAccent == colorLong,
                            onClick = { selectedAccent = colorLong }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedBackground, selectedAccent) }) {
                Text("OK", color = Color(selectedAccent)) // Preview accent
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF2d2d2d), // Fixed dark dialog bg
        textContentColor = Color.White,
        titleContentColor = Color(selectedAccent) // Dynamic Title
                                  
    )
}

@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else Color.Gray.copy(alpha=0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}
