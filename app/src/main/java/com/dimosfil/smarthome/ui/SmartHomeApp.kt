package com.dimosfil.smarthome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimosfil.smarthome.R
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.onboarding.DeviceCandidate
import com.dimosfil.smarthome.onboarding.OnboardingScreen
import com.dimosfil.smarthome.onboarding.ProvisioningMode
import com.dimosfil.smarthome.onboarding.ProvisioningStage
import com.dimosfil.smarthome.onboarding.SavedDevice
import com.dimosfil.smarthome.onboarding.WifiNetwork

@Composable
fun SmartHomeApp(
    state: OnboardingUiState,
    bluetoothGranted: Boolean,
    wifiScanGranted: Boolean,
    onRequestBluetooth: () -> Unit,
    onRequestWifiScan: () -> Unit,
    model: OnboardingViewModel,
) {
    BackHandler(enabled = state.screen != OnboardingScreen.DeviceList, onBack = model::goBack)

    MaterialTheme {
        when (state.screen) {
            OnboardingScreen.DeviceList -> DeviceListScreen(
                state = state,
                onAdd = { model.openDiscovery(bluetoothGranted) },
                onOpen = model::openControl,
            )
            OnboardingScreen.Discovery -> DiscoveryScreen(
                state = state,
                bluetoothGranted = bluetoothGranted,
                onBack = model::goBack,
                onRequestBluetooth = onRequestBluetooth,
                onToggleScan = {
                    if (state.isScanning) model.stopDiscovery() else model.startDiscovery(bluetoothGranted)
                },
                onSelect = model::selectCandidate,
            )
            OnboardingScreen.DeviceFound -> state.selectedCandidate?.let { candidate ->
                DeviceFoundScreen(candidate, state.errorMessage, model::goBack, model::confirmCandidate)
            } ?: MissingStateScreen(model::goBack)
            OnboardingScreen.Installation -> state.selectedCandidate?.let { candidate ->
                InstallationScreen(
                    candidate = candidate,
                    busy = state.isBusy,
                    error = state.errorMessage,
                    onBack = model::goBack,
                    onContinue = model::confirmInstallation,
                )
            } ?: MissingStateScreen(model::goBack)
            OnboardingScreen.NetworkSetup -> NetworkSetupScreen(
                state = state,
                wifiScanGranted = wifiScanGranted,
                onBack = model::goBack,
                onSsidChanged = model::updateSsid,
                onRequestWifiScan = onRequestWifiScan,
                onRefreshWifiNetworks = model::scanWifiNetworks,
                onPasswordChanged = model::updatePassword,
                onSubmit = model::submitNetwork,
            )
            OnboardingScreen.Provisioning -> ProvisioningScreen(
                state = state,
                onCancel = model::goBack,
                onRetry = model::retryProvisioning,
            )
            OnboardingScreen.Success -> SuccessScreen(
                state = state,
                onNameChanged = model::updateSuccessName,
                onRoomChanged = model::updateSuccessRoom,
                onDone = model::finishSuccess,
            )
            OnboardingScreen.Control -> {
                val device = state.savedDevices.firstOrNull { it.id == state.selectedSavedDeviceId }
                if (device == null) MissingStateScreen(model::goBack) else ControlScreen(
                    device = device,
                    state = state,
                    onBack = model::goBack,
                    onRefresh = model::refreshPower,
                    onPowerChange = model::setPower,
                    onDelete = model::removeSelectedDevice,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeviceListScreen(
    state: OnboardingUiState,
    onAdd: () -> Unit,
    onOpen: (SavedDevice) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.my_devices)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.device_list_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                TuyaAccountCard(state)
            }
            state.errorMessage?.let { message -> item { ErrorCard(message) } }
            if (state.savedDevices.isEmpty()) {
                item { EmptyDeviceListCard() }
            } else {
                items(state.savedDevices, key = SavedDevice::id) { device ->
                    SavedDeviceCard(device = device, onClick = { onOpen(device) })
                }
            }
            item {
                Button(
                    onClick = onAdd,
                    enabled = state.tuyaHomeReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(stringResource(R.string.add_device))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TuyaAccountCard(
    state: OnboardingUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.tuyaHomeReady) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.tuya_account_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            when {
                state.tuyaHomeReady -> Text(stringResource(R.string.tuya_ready))
                !state.tuyaConfigured -> Text(
                    stringResource(R.string.tuya_not_configured),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    stringResource(R.string.tuya_connecting),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.accountMessage?.let { message ->
                Text(
                    message,
                    color = if (state.tuyaHomeReady) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyDeviceListCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⌁", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.no_devices),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.no_devices_hint),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedDeviceCard(device: SavedDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("⌁", fontSize = 26.sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.room, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (device.isOnline) stringResource(R.string.online) else stringResource(R.string.offline),
                    color = if (device.isOnline) Color(0xFF008B6B) else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = when (device.powerState) {
                    true -> stringResource(R.string.on)
                    false -> stringResource(R.string.off)
                    null -> "—"
                },
                fontWeight = FontWeight.Bold,
                color = if (device.powerState == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscoveryScreen(
    state: OnboardingUiState,
    bluetoothGranted: Boolean,
    onBack: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onToggleScan: () -> Unit,
    onSelect: (DeviceCandidate) -> Unit,
) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.add_device), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(if (state.isScanning) R.string.searching_nearby else R.string.search_paused),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.searching_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RadarGraphic(active = state.isScanning)
            }
            if (!bluetoothGranted) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.bluetooth_permission_hint))
                            TextButton(onClick = onRequestBluetooth) {
                                Text(stringResource(R.string.allow_bluetooth))
                            }
                        }
                    }
                }
            }
            state.errorMessage?.let { error -> item { ErrorCard(error) } }
            item {
                Text(
                    stringResource(R.string.devices_found_count, state.candidates.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedButton(onClick = onToggleScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.isScanning) R.string.stop_search else R.string.start_search))
                }
            }
            if (state.candidates.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_candidates_yet),
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.candidates, key = { it.device.id }) { candidate ->
                    CandidateCard(candidate, onClick = { onSelect(candidate) })
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun RadarGraphic(active: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(24.dp),
    ) {
        val radius = size.minDimension / 2
        drawCircle(primary.copy(alpha = 0.08f), radius)
        drawCircle(primary.copy(alpha = 0.14f), radius * 0.66f)
        drawCircle(primary.copy(alpha = 0.20f), radius * 0.34f)
        drawCircle(primary, 6.dp.toPx())
        if (active) {
            drawArc(
                color = primary,
                startAngle = -25f,
                sweepAngle = 72f,
                useCenter = true,
                topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                alpha = 0.24f,
            )
        }
    }
}

@Composable
private fun CandidateCard(candidate: DeviceCandidate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (candidate.device.transport == DeviceTransport.Bluetooth) "B" else "Wi")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(candidate.device.name, style = MaterialTheme.typography.titleMedium)
                Text(candidate.profile.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeviceFoundScreen(
    candidate: DeviceCandidate,
    error: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.device_found), onBack) }) { padding ->
        CenteredScrollableColumn(padding = padding) {
            StatusCircle(symbol = "✓")
            Text(
                stringResource(R.string.found_named_device, candidate.device.name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                candidate.profile.displayName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let { ErrorCard(it) }
            PrimaryAction(stringResource(R.string.continue_action), onContinue)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InstallationScreen(
    candidate: DeviceCandidate,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.installation_title), onBack) }) { padding ->
        CenteredScrollableColumn(padding = padding) {
            StatusCircle(symbol = "⌁")
            Text(
                stringResource(R.string.prepare_device),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                installationHint(candidate),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let { ErrorCard(it) }
            PrimaryAction(
                label = stringResource(if (busy) R.string.checking_device else R.string.device_ready),
                onClick = onContinue,
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun installationHint(candidate: DeviceCandidate): String = when (candidate.profile.provisioningMode) {
    ProvisioningMode.RequiresWifiCredentials -> stringResource(R.string.wifi_credentials_install_hint)
    ProvisioningMode.AlreadyNetworked -> stringResource(R.string.networked_install_hint)
    ProvisioningMode.Unsupported -> stringResource(R.string.unsupported_install_hint)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NetworkSetupScreen(
    state: OnboardingUiState,
    wifiScanGranted: Boolean,
    onBack: () -> Unit,
    onSsidChanged: (String) -> Unit,
    onRequestWifiScan: () -> Unit,
    onRefreshWifiNetworks: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.network_setup), onBack) }) { padding ->
        CenteredScrollableColumn(padding = padding) {
            Text("2.4 GHz", fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.network_setup_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (wifiScanGranted) {
                WifiNetworkDropdown(
                    networks = state.availableWifiNetworks,
                    selectedSsid = state.ssid,
                    isScanning = state.isWifiScanning,
                    onSelected = onSsidChanged,
                )
                OutlinedButton(
                    onClick = onRefreshWifiNetworks,
                    enabled = !state.isWifiScanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isWifiScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(R.string.refresh_wifi_networks))
                }
            } else {
                Text(
                    stringResource(R.string.wifi_scan_permission_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = onRequestWifiScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.allow_wifi_scan))
                }
            }
            state.wifiScanError?.let { ErrorCard(it) }
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.wifi_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(stringResource(if (showPassword) R.string.hide else R.string.show))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { ErrorCard(it) }
            PrimaryAction(stringResource(R.string.confirm), onSubmit)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WifiNetworkDropdown(
    networks: List<WifiNetwork>,
    selectedSsid: String,
    isScanning: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (networks.isNotEmpty()) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedSsid,
            onValueChange = {},
            readOnly = true,
            enabled = networks.isNotEmpty(),
            label = { Text(stringResource(R.string.wifi_name)) },
            placeholder = {
                Text(
                    stringResource(
                        if (isScanning) R.string.wifi_scanning else R.string.select_wifi_network,
                    ),
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = networks.isNotEmpty(),
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            networks.forEach { network ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(network.ssid)
                            Text(
                                text = "${network.signalLevelDbm} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(network.ssid)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProvisioningScreen(
    state: OnboardingUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = state.provisioningProgress
    val completedCount = progress?.completedStages?.size ?: 0
    val progressValue = ((completedCount + if (progress == null) 0f else 0.45f) /
        ProvisioningStage.entries.size).coerceIn(0f, 1f)
    Scaffold(topBar = { AppTopBar(stringResource(R.string.connecting_device), onCancel) }) { padding ->
        CenteredScrollableColumn(padding = padding) {
            if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(74.dp))
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                strokeCap = StrokeCap.Round,
            )
            ProvisioningStage.entries.forEach { stage ->
                val completed = stage in (progress?.completedStages ?: emptySet())
                val active = progress?.stage == stage && !completed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            completed -> "✓"
                            active -> "●"
                            else -> "○"
                        },
                        color = if (completed || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = stageLabel(stage),
                        modifier = Modifier.padding(start = 12.dp),
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.errorMessage?.let { error ->
                ErrorCard(error)
                PrimaryAction(stringResource(R.string.retry), onRetry)
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun stageLabel(stage: ProvisioningStage): String = stringResource(
    when (stage) {
        ProvisioningStage.EstablishingConnection -> R.string.stage_connection
        ProvisioningStage.SendingCredentials -> R.string.stage_credentials
        ProvisioningStage.ConnectingToRouter -> R.string.stage_router
        ProvisioningStage.ActivatingDevice -> R.string.stage_activation
        ProvisioningStage.VerifyingDevice -> R.string.stage_verification
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SuccessScreen(
    state: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    onRoomChanged: (String) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.success)) }) }) { padding ->
        CenteredScrollableColumn(padding = padding) {
            StatusCircle(symbol = "✓")
            Text(
                stringResource(R.string.device_added_successfully),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = state.successName,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.device_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.successRoom,
                onValueChange = onRoomChanged,
                label = { Text(stringResource(R.string.room)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            PrimaryAction(stringResource(R.string.done), onDone)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ControlScreen(
    device: SavedDevice,
    state: OnboardingUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar(device.name, onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(device.room, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 5.dp,
                        color = if (state.powerState == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable(enabled = !state.isBusy) { onPowerChange(state.powerState != true) },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(if (state.powerState == true) R.string.on else R.string.off),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.powerState == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(if (state.powerState == true) R.string.power_on else R.string.power_off),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.power))
                Switch(
                    checked = state.powerState == true,
                    onCheckedChange = onPowerChange,
                    enabled = !state.isBusy,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !state.isBusy) {
                Text(stringResource(R.string.refresh_state))
            }
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !state.isBusy,
            ) {
                Text(
                    stringResource(R.string.delete_device),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.errorMessage?.let { ErrorCard(it) }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_device_title)) },
            text = { Text(stringResource(R.string.delete_device_message, device.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text(
                        stringResource(R.string.delete_device_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back), fontSize = 18.sp)
            }
        },
    )
}

@Composable
private fun CenteredScrollableColumn(
    padding: androidx.compose.foundation.layout.PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
private fun StatusCircle(symbol: String) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 52.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        Text(label)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MissingStateScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.error), onBack) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.missing_state))
        }
    }
}
