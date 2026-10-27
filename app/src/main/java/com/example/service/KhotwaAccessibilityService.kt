package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.domain.model.ScreenInspectionSnapshot
import com.example.domain.model.ScreenUiElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "KhotwaA11yService"

/**
 * Real Android AccessibilityService for Khotwa AI In-App Guidance.
 * Strictly respects user privacy:
 * - Only inspects screen nodes when an active guidance session is started by the user.
 * - Immediately stops inspecting if a password field or protected window is detected.
 * - Never performs automated clicks on sensitive actions.
 * - Provides a movable, collapsible, and immediately dismissible floating guidance bar
 *   using TYPE_ACCESSIBILITY_OVERLAY.
 */
class KhotwaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var floatingRootView: LinearLayout? = null
    private var titleTextView: TextView? = null
    private var bodyTextView: TextView? = null
    private var targetBadgeView: TextView? = null
    private var detailsContainer: LinearLayout? = null
    private var minimizeButton: Button? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "KhotwaAccessibilityService connected")
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        GuidanceSessionCoordinator.setServiceConnectionState(true)

        serviceScope.launch {
            GuidanceSessionCoordinator.sessionState.collectLatest { state ->
                if (state.isActive && state.isFloatingHudVisible) {
                    showOrUpdateFloatingOverlay(state)
                } else {
                    removeFloatingOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentSession = GuidanceSessionCoordinator.sessionState.value
        if (!currentSession.isActive) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val snapshot = captureSafeScreenSnapshot(rootNode)
            GuidanceSessionCoordinator.updateScreenSnapshot(snapshot)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect accessibility window", e)
        } finally {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { rootNode.recycle() }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "KhotwaAccessibilityService interrupted")
        removeFloatingOverlay()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        GuidanceSessionCoordinator.setServiceConnectionState(false)
        removeFloatingOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        GuidanceSessionCoordinator.setServiceConnectionState(false)
        removeFloatingOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun captureSafeScreenSnapshot(root: AccessibilityNodeInfo): ScreenInspectionSnapshot {
        val pkgName = root.packageName?.toString() ?: "unknown.package"
        var hasPasswordField = false
        val extractedElements = mutableListOf<ScreenUiElement>()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 18 || extractedElements.size >= 45) return
            if (node.isPassword) {
                hasPasswordField = true
                return
            }

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            val cls = node.className?.toString().orEmpty()
            val clickable = node.isClickable
            val editable = node.isEditable

            if ((text.isNotBlank() || desc.isNotBlank()) && node.isVisibleToUser) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 8 && rect.height() > 8) {
                    extractedElements.add(
                        ScreenUiElement(
                            index = extractedElements.size,
                            text = text.take(80),
                            contentDescription = desc.take(80),
                            viewIdResourceName = viewId,
                            className = cls,
                            isClickable = clickable,
                            isEditable = editable,
                            boundsInScreen = rect
                        )
                    )
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
            }
        }

        traverse(root, 0)

        return ScreenInspectionSnapshot(
            packageName = pkgName,
            appDisplayName = pkgName.substringAfterLast(".").replaceFirstChar { it.uppercase() },
            isProtectedOrPasswordScreen = hasPasswordField,
            elements = if (hasPasswordField) emptyList() else extractedElements
        )
    }

    private fun showOrUpdateFloatingOverlay(state: ActiveGuidanceSession) {
        val wm = windowManager ?: return
        if (floatingRootView == null) {
            createAndAttachOverlayView(wm)
        }

        val instruction = state.currentInstruction
        titleTextView?.text = when {
            state.isAnalyzingScreen -> "خُطوة AI • جاري تحليل الشاشة..."
            instruction != null -> "خُطوة ${instruction.stepIndex}: ${instruction.instructionTitle}"
            else -> "مُرشد خُطوة AI نشط"
        }

        bodyTextView?.text = instruction?.simpleExplanation ?: state.statusBanner
        val matched = instruction?.matchedElement
        if (matched != null) {
            targetBadgeView?.visibility = View.VISIBLE
            targetBadgeView?.text = "🎯 العنصر المستهدف: «${matched.displayLabel}»"
        } else {
            targetBadgeView?.visibility = View.GONE
        }

        detailsContainer?.visibility = if (state.isOverlayMinimized) View.GONE else View.VISIBLE
        minimizeButton?.text = if (state.isOverlayMinimized) "توسيع" else "إخفاء مؤقت"
    }

    private fun createAndAttachOverlayView(wm: WindowManager) {
        try {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.parseColor("#EE0B111E"))
                    setStroke(dp(2), Color.parseColor("#10B981"))
                }
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(this).apply {
                text = "مُرشد خُطوة AI"
                setTextColor(Color.parseColor("#10B981"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleTextView = titleTv

            val minBtn = Button(this).apply {
                text = "إخفاء مؤقت"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    GuidanceSessionCoordinator.toggleMinimizeOverlay()
                }
            }
            minimizeButton = minBtn

            headerRow.addView(titleTv)
            headerRow.addView(minBtn)

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            detailsContainer = details

            val bodyTv = TextView(this).apply {
                setTextColor(Color.parseColor("#F1F5F9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            bodyTextView = bodyTv

            val badgeTv = TextView(this).apply {
                setTextColor(Color.parseColor("#FDE68A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#452700"))
                }
            }
            targetBadgeView = badgeTv

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(8), 0, 0)
            }

            val nextStepBtn = Button(this).apply {
                text = "الخطوة التالية ✓"
                setTextColor(Color.parseColor("#032A1E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#10B981"))
                }
                setOnClickListener {
                    GuidanceSessionCoordinator.requestNextStepFromOverlay()
                }
            }

            val stopBtn = Button(this).apply {
                text = "إنهاء فورًا ✕"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#EF4444"))
                }
                setOnClickListener {
                    GuidanceSessionCoordinator.stopSessionImmediately()
                }
            }

            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
            }

            actionsRow.addView(nextStepBtn)
            actionsRow.addView(spacer)
            actionsRow.addView(stopBtn)

            details.addView(bodyTv)
            details.addView(badgeTv)
            details.addView(actionsRow)

            container.addView(headerRow)
            container.addView(details)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48)
            }

            // Enable vertical dragging of the floating guidance HUD
            var initialY = 0
            var initialTouchY = 0f
            headerRow.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = layoutParams.y
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceAtLeast(dp(24))
                        runCatching { wm.updateViewLayout(container, layoutParams) }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(container, layoutParams)
            floatingRootView = container
        } catch (e: Exception) {
            Log.e(TAG, "Could not attach TYPE_ACCESSIBILITY_OVERLAY view", e)
        }
    }

    private fun removeFloatingOverlay() {
        val view = floatingRootView ?: return
        runCatching {
            windowManager?.removeView(view)
        }
        floatingRootView = null
        titleTextView = null
        bodyTextView = null
        targetBadgeView = null
        detailsContainer = null
        minimizeButton = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
