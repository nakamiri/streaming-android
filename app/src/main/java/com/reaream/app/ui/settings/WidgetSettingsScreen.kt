package com.reaream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reaream.app.data.model.*
import com.reaream.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    widgets: WidgetSettings,
    onNavigate: (Screen) -> Unit,
    onUpdate: (WidgetSettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widgets") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Clock Widget Section
            Text(
                text = "時計",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SwitchItem(
                title = "時計を表示",
                subtitle = "配信映像に現在時刻を表示",
                checked = widgets.clockWidget.enabled,
                onCheckedChange = {
                    onUpdate(widgets.copy(clockWidget = widgets.clockWidget.copy(enabled = it)))
                },
            )
            if (widgets.clockWidget.enabled) {
                FormatDropdown(
                    selected = widgets.clockWidget.format,
                    onSelect = { format ->
                        onUpdate(widgets.copy(
                            clockWidget = widgets.clockWidget.copy(format = format)
                        ))
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Location Widget Section
            Text(
                text = "位置情報",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SwitchItem(
                title = "位置情報を表示",
                subtitle = "配信映像に現在地を表示（位置情報の権限が必要）",
                checked = widgets.locationWidget.enabled,
                onCheckedChange = {
                    onUpdate(widgets.copy(locationWidget = widgets.locationWidget.copy(enabled = it)))
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Speed Widget Section
            Text(
                text = "速度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SwitchItem(
                title = "速度を表示",
                subtitle = "配信映像に移動速度を表示（位置情報の権限が必要）",
                checked = widgets.speedWidget.enabled,
                onCheckedChange = {
                    onUpdate(widgets.copy(speedWidget = widgets.speedWidget.copy(enabled = it)))
                },
            )
            if (widgets.speedWidget.enabled) {
                UnitDropdown(
                    selected = widgets.speedWidget.unit,
                    onSelect = { unit ->
                        onUpdate(widgets.copy(
                            speedWidget = widgets.speedWidget.copy(unit = unit)
                        ))
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Map Widget Section
            Text(
                text = "地図",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SwitchItem(
                title = "地図を表示",
                subtitle = "配信映像にミニマップを表示（位置情報の権限が必要）",
                checked = widgets.mapWidget.enabled,
                onCheckedChange = {
                    onUpdate(widgets.copy(mapWidget = widgets.mapWidget.copy(enabled = it)))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(
    selected: ClockFormat,
    onSelect: (ClockFormat) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("表示形式") },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                Text(
                    text = selected.displayName,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ClockFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.displayName) },
                            onClick = {
                                onSelect(format)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    selected: SpeedUnit,
    onSelect: (SpeedUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("単位") },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                Text(
                    text = selected.displayName,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    SpeedUnit.entries.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.displayName) },
                            onClick = {
                                onSelect(unit)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}
