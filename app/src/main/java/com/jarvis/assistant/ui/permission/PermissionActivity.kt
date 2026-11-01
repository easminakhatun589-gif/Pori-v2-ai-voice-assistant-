package com.jarvis.assistant.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.util.PermissionHelper

class PermissionActivity : AppCompatActivity() {

    private lateinit var statusContainer: LinearLayout

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        renderPermissionRows()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#EEF2FF"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(18).toFloat()
            }
        }

        val btnBack = Button(this).apply {
            text = "← Back"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { finish() }
        }

        val tvTitle = TextView(this).apply {
            text = "🛡️ JARVIC Permissions & Security Setup"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
        }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        val btnGrantAll = Button(this).apply {
            text = "✓ Grant Microphone, Camera & Location"
            isAllCaps = false
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        root.addView(btnGrantAll)

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }
        root.addView(statusContainer)

        scroll.addView(root)
        setContentView(scroll)
        renderPermissionRows()
    }

    override fun onResume() {
        super.onResume()
        if (::statusContainer.isInitialized) {
            renderPermissionRows()
        }
    }

    private fun renderPermissionRows() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        statusContainer.removeAllViews()

        val items = listOf(
            Triple(
                "🎙️ Microphone (Live Voice)",
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            Triple(
                "📷 Camera (Active Camera Vision)",
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
            Triple(
                "📍 Location (Live Weather & Country)",
                PermissionHelper.hasLocationPermission(this)
            ) {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            Triple(
                "🪟 Display Over Other Apps (Floating HUD)",
                Settings.canDrawOverlays(this)
            ) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            },
            Triple(
                "🤖 Accessibility Service (Mobile Control)",
                false
            ) {
                PermissionHelper.openAccessibilitySettings(this)
            }
        )

        for ((label, granted, action) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor("#CBD5E1"))
                }
            }

            val tv = TextView(this).apply {
                text = "$label\nStatus: ${if (granted) "✓ Granted" else "Action Available"}"
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (granted) Color.parseColor("#15803D") else Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btn = Button(this).apply {
                text = if (granted) "Granted" else "Configure"
                isAllCaps = false
                setOnClickListener { action() }
            }

            row.addView(tv)
            row.addView(btn)
            statusContainer.addView(row)
        }
    }
}
