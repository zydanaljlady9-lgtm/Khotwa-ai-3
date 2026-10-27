package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.KhotwaSettingsState
import com.example.service.ActiveGuidanceSession
import com.example.service.GuidanceSessionCoordinator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidanceCenterScreen(
    settings: KhotwaSettingsState,
    sessionState: ActiveGuidanceSession,
    isServiceConnected: Boolean,
    initialGoal: String = "",
    onToggleScreenConsent: (Boolean) -> Unit,
    onToggleFloatingHud: (Boolean) -> Unit,
    onStartGuidance: (String) -> Unit,
    onNextStep: () -> Unit,
    onRefreshScreen: () -> Unit,
    onStopGuidanceNow: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    var goalInput by rememberSaveable(initialGoal) {
        mutableStateOf(
            initialGoal.ifBlank {
                "ساعدني على تفعيل التحقق بخطوتين ومراجعة أمان الحساب خطوة بخطوة"
            }
        )
    }

    val isSystemEnabled = isServiceConnected || GuidanceSessionCoordinator.isAccessibilityEnabledInSystem(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("guidance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                    Column {
                        Text(
                            text = "مركز الإرشاد داخل التطبيقات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "توجيه بصري خطوة بخطوة عبر AccessibilityService",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (sessionState.isActive) {
                    Button(
                        onClick = onStopGuidanceNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("stop_guidance_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "إيقاف الجلسة فورًا",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيقاف فورًا")
                    }
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
            // 1. Permission & System Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSystemEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = if (isSystemEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = if (isSystemEnabled) {
                                        "خدمة إمكانية الوصول: مفعّلة ومتصلة"
                                    } else {
                                        "خدمة إمكانية الوصول: غير مفعّلة في النظام"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تُستخدم لقراءة أسماء الأزرار الظاهرة وتوجيهك إليها عند طلبك فقط",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "• لا يقرأ التطبيق الشاشات المحمية (FLAG_SECURE) أو حقول كلمات المرور.\n• لا ينفذ عمليات مالية أو حساسة نيابةً عنك.\n• يمكنك تفعيل الخدمة يدويًا من إعدادات النظام أو استخدام محاكي الإرشاد التفاعلي أدناه مباشرة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        GuidanceSessionCoordinator.createAccessibilitySettingsIntent()
                                    )
                                }
                            },
                            modifier = Modifier.testTag("open_accessibility_settings_button")
                        ) {
                            Text("فتح إعدادات إمكانية الوصول بالنظام")
                        }
                    }
                }
            }

            // 2. Privacy Consent & Optional Floating HUD Controls
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "الموافقة الصريحة على فحص عناصر الشاشة",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "إرسال ملخص النصوص المرئية (بدون كلمات المرور) لتحديد الخطوة التالية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.screenInspectionConsentGranted,
                            onCheckedChange = onToggleScreenConsent,
                            modifier = Modifier.testTag("screen_consent_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "إظهار المؤشر العائم الاختياري فوق التطبيقات",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "شريط عائم قابل للتحريك والإخفاء المؤقت والإنهاء الفوري",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.floatingOverlayEnabled,
                            onCheckedChange = onToggleFloatingHud,
                            modifier = Modifier.testTag("floating_overlay_switch")
                        )
                    }
                }
            }

            // 3. Goal Input & Session Launcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ما المهمة أو التطبيق الذي تريد الإرشاد بداخله؟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guidance_goal_input"),
                        placeholder = {
                            Text("مثال: ساعدني على تفعيل التحقق بخطوتين أو ضبط الإشعارات...")
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickGoals = listOf(
                            "تفعيل المصادقة الثنائية وأمان الحساب",
                            "مراجعة أذونات الخصوصية للتطبيقات",
                            "تحسين استهلاك البطارية في الإعدادات"
                        )
                        quickGoals.forEach { qGoal ->
                            AssistChip(
                                onClick = { goalInput = qGoal },
                                label = { Text(qGoal, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!settings.screenInspectionConsentGranted) {
                                    onToggleScreenConsent(true)
                                }
                                onStartGuidance(goalInput)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("launch_guidance_session_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (sessionState.isActive) "إعادة بدء الجلسة لهذا الهدف" else "ابدأ جلسة الإرشاد الآن",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (sessionState.isActive) {
                            Button(
                                onClick = onStopGuidanceNow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("stop_guidance_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("إنهاء فورًا")
                            }
                        }
                    }
                }
            }

            // 4. Active Guidance Live Step & Visual Element Highlighter
            AnimatedVisibility(visible = sessionState.isActive) {
                ActiveGuidanceVisualizerCard(
                    sessionState = sessionState,
                    onNextStep = onNextStep,
                    onRefresh = onRefreshScreen,
                    onStopNow = onStopGuidanceNow
                )
            }
        }
    }
}

@Composable
private fun ActiveGuidanceVisualizerCard(
    sessionState: ActiveGuidanceSession,
    onNextStep: () -> Unit,
    onRefresh: () -> Unit,
    onStopNow: () -> Unit
) {
    val instruction = sessionState.currentInstruction
    val snapshot = sessionState.latestSnapshot

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_guidance_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "الخطوة الحالية (${sessionState.currentStepNumber})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = snapshot?.appDisplayName ?: "فحص الشاشة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (sessionState.isAnalyzingScreen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = sessionState.statusBanner,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (instruction != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = instruction.instructionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = instruction.simpleExplanation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!instruction.safetyWarning.isNullOrBlank()) {
                            Text(
                                text = "🛡️ ${instruction.safetyWarning}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Visual Highlight of Screen Elements
            if (snapshot != null && snapshot.elements.isNotEmpty()) {
                Text(
                    text = "عناصر الشاشة المرئية (يتم تمييز العنصر المطلوب بصريًا):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.elements.take(6).forEach { element ->
                        val isTarget = instruction?.matchedElement?.index == element.index
                        Surface(
                            color = if (isTarget) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isTarget) 2.dp else 1.dp,
                                color = if (isTarget) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isTarget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isTarget) {
                                            Icon(
                                                imageVector = Icons.Default.MyLocation,
                                                contentDescription = "العنصر المستهدف",
                                                tint = MaterialTheme.colorScheme.onSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = element.displayLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isTarget) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (element.contentDescription.isNotBlank() && element.contentDescription != element.displayLabel) {
                                            Text(
                                                text = element.contentDescription,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isTarget) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "العنصر المطلوب الآن",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Completed Steps Trail
            if (sessionState.completedStepsHistory.isNotEmpty()) {
                Text(
                    text = "الخطوات المنجزة: ${sessionState.completedStepsHistory.joinToString(" ← ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // User Confirmation & Session Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNextStep,
                    enabled = !sessionState.isAnalyzingScreen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("guidance_next_step_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نفذت الإجراء — الخطوة التالية")
                }

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !sessionState.isAnalyzingScreen,
                    modifier = Modifier.testTag("guidance_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث الشاشة",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
