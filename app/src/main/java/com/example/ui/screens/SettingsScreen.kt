package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAgentActive by viewModel.isAgentActive.collectAsStateWithLifecycle()
    val webhookUrl by viewModel.webhookUrl.collectAsStateWithLifecycle()
    val webhookSecret by viewModel.webhookSecret.collectAsStateWithLifecycle()
    val processWhatsApp by viewModel.processWhatsApp.collectAsStateWithLifecycle()
    val processWhatsAppBusiness by viewModel.processWhatsAppBusiness.collectAsStateWithLifecycle()
    val ignoreGroups by viewModel.ignoreGroups.collectAsStateWithLifecycle()
    val strictGroupCheck by viewModel.strictGroupCheck.collectAsStateWithLifecycle()
    val ignoredContacts by viewModel.ignoredContacts.collectAsStateWithLifecycle()
    val maxRetries by viewModel.maxRetries.collectAsStateWithLifecycle()
    val enableContactsPhoneLookup by viewModel.enableContactsPhoneLookup.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingWebhook.collectAsStateWithLifecycle()
    val testResult by viewModel.testWebhookResult.collectAsStateWithLifecycle()

    var showPassword by remember { mutableStateOf(false) }
    var newIgnoredContactText by remember { mutableStateOf("") }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado
        Text(
            text = "Configuración del Sistema",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Sección 1: Estado del Agente
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agente Activo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Habilita o pausa el reenvío de notificaciones a tu webhook",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isAgentActive,
                        onCheckedChange = { viewModel.setAgentActive(it) },
                        modifier = Modifier.testTag("settings_agent_switch"),
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manage_notification_access_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar Acceso a Notificaciones de Android")
                }
            }
        }

        // Sección 2: Configuración del Webhook
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = CyanAccent)
                    Text(
                        text = "Servidor Webhook",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // URL del Webhook
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { viewModel.setWebhookUrl(it) },
                    label = { Text("URL del Webhook (HTTPS)") },
                    placeholder = { Text("https://mi-servidor.com/api/webhook") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webhook_url_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(12.dp)
                )

                // Token o Secreto
                OutlinedTextField(
                    value = webhookSecret,
                    onValueChange = { viewModel.setWebhookSecret(it) },
                    label = { Text("Secreto / Token Bearer (Opcional)") },
                    placeholder = { Text("mi_secreto_super_seguro") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webhook_secret_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Ocultar" else "Mostrar"
                            )
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Almacenado cifrado mediante Android KeyStore (AES-256-GCM).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Botón de prueba de conexión
                Button(
                    onClick = { viewModel.testWebhookConnection() },
                    enabled = !isTesting && webhookUrl.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_test_webhook_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Probando conexión...", color = Color.Black)
                    } else {
                        Text("Probar Conexión con el Webhook", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Resultado de la prueba
                AnimatedVisibility(visible = testResult != null) {
                    testResult?.let { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.success) SuccessGreen.copy(alpha = 0.15f) else CoralRed.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.success) SuccessGreen else CoralRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (res.success) "Conexión exitosa (HTTP ${res.statusCode}) en ${res.durationMs}ms" else "Error de conexión",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.success) SuccessGreen else CoralRed
                                    )
                                }
                                if (res.errorMessage != null) {
                                    Text(
                                        text = res.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (!res.responseBody.isNullOrBlank()) {
                                    Text(
                                        text = "Respuesta: ${res.responseBody}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sección 3: Filtrado de Mensajería y Grupos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Reglas de Filtrado y Detección",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Procesar WhatsApp Estándar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Procesar WhatsApp Estándar", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Paquete com.whatsapp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = processWhatsApp,
                        onCheckedChange = { viewModel.setProcessWhatsApp(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                // Procesar WhatsApp Business
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Procesar WhatsApp Business", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Paquete com.whatsapp.w4b",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = processWhatsAppBusiness,
                        onCheckedChange = { viewModel.setProcessWhatsAppBusiness(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                // Ignorar Grupos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Ignorar Grupos", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Descarta completamente los mensajes provenientes de chats grupales",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = ignoreGroups,
                        onCheckedChange = { viewModel.setIgnoreGroups(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                // Detección Estricta de Grupos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Detección Estricta de Grupos", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Si no se puede comprobar al 100% que es individual, descartar por seguridad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = strictGroupCheck,
                        onCheckedChange = { viewModel.setStrictGroupCheck(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                // Reintentos máximos
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Número Máximo de Reintentos", fontWeight = FontWeight.SemiBold)
                        Text(text = "$maxRetries intentos", fontWeight = FontWeight.Bold, color = CyanAccent)
                    }
                    Slider(
                        value = maxRetries.toFloat(),
                        onValueChange = { viewModel.setMaxRetries(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    Text(
                        text = "Backoff exponencial: 1s, 2s, 4s...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Sección 4: Resolución de Teléfono y Contactos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, tint = EmeraldPrimary)
                    Text(
                        text = "Resolución de Teléfono",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "WhatsApp solo incluye el número cuando el contacto NO está guardado en tu teléfono (el nombre de chat es el número). Para contactos guardados por nombre, JuanBetterZapia puede consultar legítimamente la libreta del sistema si le concedes permiso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Consultar Contactos del Sistema", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (hasContactsPermission) "Permiso concedido" else "Requiere permiso READ_CONTACTS",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasContactsPermission) SuccessGreen else CoralRed
                        )
                    }
                    Switch(
                        checked = enableContactsPhoneLookup && hasContactsPermission,
                        onCheckedChange = { checked ->
                            if (checked && !hasContactsPermission) {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            } else {
                                viewModel.setEnableContactsPhoneLookup(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                    )
                }

                if (!hasContactsPermission) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Solicitar Permiso de Contactos")
                    }
                }
            }
        }

        // Sección 5: Lista de Contactos Ignorados
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Contactos / Chats Ignorados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Los mensajes de estos nombres de chat serán descartados automáticamente sin enviarse al webhook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newIgnoredContactText,
                        onValueChange = { newIgnoredContactText = it },
                        placeholder = { Text("Nombre exacto del contacto") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ignored_contact_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.addIgnoredContact(newIgnoredContactText)
                            newIgnoredContactText = ""
                        },
                        enabled = newIgnoredContactText.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_ignored_contact_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir")
                    }
                }

                if (ignoredContacts.isEmpty()) {
                    Text(
                        text = "No hay contactos en la lista de ignorados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ignoredContacts.forEach { contact ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.removeIgnoredContact(contact) },
                                label = { Text(contact) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Eliminar",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
