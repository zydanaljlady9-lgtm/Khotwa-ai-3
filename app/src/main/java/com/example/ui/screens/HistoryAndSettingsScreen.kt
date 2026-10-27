package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppStartupTimer
import com.example.data.ai.ConnectionDiagnosticResult
import com.example.data.local.ConversationEntity
import com.example.data.local.KhotwaSettingsState
import com.example.domain.model.AiModelTier
import com.example.domain.model.AiProviderType
import com.example.domain.model.SearchProviderType
import com.example.domain.model.ThemePreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    conversations: List<ConversationEntity>,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Text(
                        text = "سجل المحادثات المحفوظة (${conversations.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (conversations.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_all_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مسح الكل")
                    }
                }
            }
        }

        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "لا توجد محادثات محفوظة بعد",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "يتم حفظ جميع محادثاتك وخططك محليًا على جهازك فقط.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectConversation(conv.id) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${conv.primaryIntent} • ${conv.messageCount} رسائل • ${dateFormatter.format(Date(conv.updatedAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف المحادثة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndPrivacyScreen(
    settings: KhotwaSettingsState,
    diagnosticResult: ConnectionDiagnosticResult?,
    isTestingConnection: Boolean,
    isGuidanceActive: Boolean,
    onSelectAiProvider: (AiProviderType) -> Unit,
    onSelectModelTier: (AiModelTier) -> Unit,
    onUpdateCustomEndpoint: (String) -> Unit,
    onSelectSearchProvider: (SearchProviderType) -> Unit,
    onToggleAutoSearch: (Boolean) -> Unit,
    onToggleParallelSearch: (Boolean) -> Unit,
    onToggleSafeCache: (Boolean) -> Unit,
    onToggleExternalConsent: (Boolean) -> Unit,
    onToggleScreenConsent: (Boolean) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onUpdateDailyLimit: (Int) -> Unit,
    onRunDiagnostic: () -> Unit,
    onClearHistoryAndCache: () -> Unit,
    onStopGuidanceNow: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
                Column {
                    Text(
                        text = "الإعدادات والخصوصية ومراقبة الأداء",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إدارة مزود الذكاء الاصطناعي، البحث، السرعة، والأمان",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Speed & Performance Telemetry Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "مقاييس السرعة وحدود الاستخدام الفعلية",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• زمن بدء تشغيل التطبيق (Cold Start): ${AppStartupTimer.coldStartDurationMs}ms\n" +
                            "• متوسط وقت وصول أول جزء من الرد (TTFT): ${if (settings.lastAvgTtftMs > 0) "${settings.lastAvgTtftMs}ms" else "لم يُقس بعد"}\n" +
                            "• متوسط وقت اكتمال الإجابة الكامل: ${if (settings.lastAvgTotalMs > 0) "${settings.lastAvgTotalMs}ms" else "لم يُقس بعد"}\n" +
                            "• استهلاك الطلبات: ${settings.totalRequestsUsed} / ${settings.dailySoftLimit} طلبًا",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 250, 500).forEach { limit ->
                            FilterChip(
                                selected = settings.dailySoftLimit == limit,
                                onClick = { onUpdateDailyLimit(limit) },
                                label = { Text("حد $limit") }
                            )
                        }
                    }
                }
            }

            // 2. AI Provider & Model Routing Selection + Live Connection Test
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "مزود الذكاء الاصطناعي وتوجيه النماذج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    AiProviderType.entries.forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAiProvider(provider) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.aiProvider == provider,
                                onClick = { onSelectAiProvider(provider) }
                            )
                            Column {
                                Text(
                                    text = provider.arabicName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = provider.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (settings.aiProvider == AiProviderType.CUSTOM_ENDPOINT) {
                        OutlinedTextField(
                            value = settings.customEndpointUrl,
                            onValueChange = onUpdateCustomEndpoint,
                            label = { Text("رابط الخادم الوسيط الآمن (Custom Endpoint URL)") },
                            placeholder = { Text("https://your-secure-proxy.example.com/v1/chat/completions") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = "اختيار النموذج:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    AiModelTier.entries.forEach { tier ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModelTier(tier) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.aiModelTier == tier,
                                onClick = { onSelectModelTier(tier) }
                            )
                            Column {
                                Text(
                                    text = tier.arabicName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tier.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRunDiagnostic,
                        enabled = !isTestingConnection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري اختبار الاتصال بالنموذج...")
                        } else {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اختبار الاتصال الفعلي بالنموذج الآن")
                        }
                    }

                    if (diagnosticResult != null) {
                        Surface(
                            color = if (diagnosticResult.isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (diagnosticResult.isSuccess) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = if (diagnosticResult.isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Text(
                                    text = diagnosticResult.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (diagnosticResult.isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Independent Search Layer Settings
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "طبقة البحث المستقلة عبر الإنترنت",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    SearchProviderType.entries.forEach { sp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSearchProvider(sp) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.searchProvider == sp,
                                onClick = { onSelectSearchProvider(sp) }
                            )
                            Column {
                                Text(
                                    text = sp.arabicName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sp.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingToggleRow(
                        title = "تحديد الحاجة للبحث تلقائيًا",
                        subtitle = "تشغيل البحث الحي عندما يتطلب السؤال معلومات حديثة أو مصادر",
                        checked = settings.autoWebSearchEnabled,
                        onCheckedChange = onToggleAutoSearch
                    )

                    SettingToggleRow(
                        title = "البحث المتوازي (Parallel Search)",
                        subtitle = "جمع المصادر بالتوازي لتقليل زمن الانتظار الكلي",
                        checked = settings.parallelSearchEnabled,
                        onCheckedChange = onToggleParallelSearch
                    )

                    SettingToggleRow(
                        title = "التخزين المؤقت الآمن (Safe Cache)",
                        subtitle = "منع الطلبات الشبكية المكررة لنفس السؤال خلال 15 دقيقة",
                        checked = settings.safeCacheEnabled,
                        onCheckedChange = onToggleSafeCache
                    )
                }
            }

            // 4. Appearance & Theme Mode
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "المظهر واتجاه العرض (RTL)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themePreference == mode,
                                onClick = { onSelectTheme(mode) },
                                label = { Text(mode.arabicLabel) }
                            )
                        }
                    }
                }
            }

            // 5. Privacy, Permissions & Emergency Stop
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "الأمان والخصوصية والتحكم بالبيانات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• لا يتم تضمين أي مفاتيح API سرية داخل ملف APK؛ تتم المصادقة عبر Firebase App Check الآمن.\n• يتم إرسال نص سؤالك فقط عند تفعيل الموافقة أدناه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingToggleRow(
                        title = "الموافقة على إرسال الأسئلة لمحرك الذكاء الاصطناعي",
                        subtitle = "يمكنك إيقافها في أي وقت لمنع أي اتصال خارجي",
                        checked = settings.externalAiConsentGranted,
                        onCheckedChange = onToggleExternalConsent
                    )

                    SettingToggleRow(
                        title = "الموافقة على فحص الشاشة أثناء جلسات الإرشاد",
                        subtitle = "تعمل فقط عند بدء جلسة إرشاد وتتجاهل كلمات المرور",
                        checked = settings.screenInspectionConsentGranted,
                        onCheckedChange = onToggleScreenConsent
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearHistoryAndCache,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("privacy_clear_history_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح سجل المحادثات")
                        }

                        Button(
                            onClick = onStopGuidanceNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("privacy_stop_guidance_button")
                        ) {
                            Icon(imageVector = Icons.Default.StopCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isGuidanceActive) "إيقاف الإرشاد فورًا" else "الإرشاد متوقف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ApkBuildAndInstallGuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
                Column {
                    Text(
                        text = "تنزيل وتثبيت APK على Samsung Galaxy S24 FE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "بناء Gradle الفعلي + سير عمل GitHub Actions من الهاتف مباشرة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "حالة بناء ملف APK الفعلي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• اسم الحزمة: com.aistudio.khotwaai.zrxqmp\n" +
                            "• التوافق: Android 7.0 (API 24) حتى Android 16 (API 36) ومختبر لمعمارية Samsung Galaxy S24 FE.\n" +
                            "• المسار الناتج في بيئة البناء: app/build/outputs/apk/debug/app-debug.apk",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "الطريقة 1: التنزيل المباشر من قائمة AI Studio على هاتفك",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "1. افتح قائمة الإعدادات/التصدير (Settings / Export) أعلى واجهة AI Studio.\n" +
                            "2. اختر «Download APK» أو «Build APK» لتنزيل ملف app-debug.apk الموقع جاهزًا.\n" +
                            "3. افتح الملف من إشعارات التنزيل في هاتفك Samsung Galaxy S24 FE ووافق على التثبيت.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "الطريقة 2: البناء السحابي عبر GitHub Actions (من الهاتف فقط دون كمبيوتر)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "تم تضمين ملف سير عمل جاهز في المسار:\n.github/workflows/build-android-apk.yml\n\n" +
                            "خطوات التشغيل من متصفح الهاتف:\n" +
                            "1. اضغط على زر «Push to GitHub» في قائمة AI Studio لرفع المشروع إلى حسابك في GitHub.\n" +
                            "2. افتح مستودعك في متصفح الهاتف وانتقل إلى تبويب «Actions».\n" +
                            "3. اختر «Build Khotwa AI Android APK» ثم اضغط «Run workflow».\n" +
                            "4. بعد اكتمال البناء (حوالي دقيقتين)، انزل لأسفل صفحة العملية ضمن قسم «Artifacts» واضغط على «khotwa-ai-installable-apk» لتنزيل ملف APK وتثبيته مباشرة على هاتفك.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
