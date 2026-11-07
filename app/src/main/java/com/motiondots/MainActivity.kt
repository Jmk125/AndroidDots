package com.motiondots

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var settings: DotSettings
    private lateinit var preview: DotsView
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val swatches = mutableListOf<Pair<Int, View>>()

    private val palette = intArrayOf(
        Color.WHITE, Color.BLACK, 0xFF9AA0A6.toInt(), 0xFFFF5252.toInt(), 0xFFFF9800.toInt(),
        0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(), 0xFF00E5FF.toInt(), 0xFF448AFF.toInt(),
        0xFFE040FB.toInt()
    )

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = DotSettings(this)
        buildUi()
    }

    override fun onStart() {
        super.onStart()
        DotsService.stateListener = { runOnUiThread { refreshState() } }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onStop() {
        DotsService.stateListener = null
        super.onStop()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(32f))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "Motion Dots"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Dots float over every app and slide with your phone's motion, giving your eyes " +
                "the movement your inner ear feels. Helps with motion sickness in cars, trains and boats."
            setPadding(0, dp(6f), 0, dp(14f))
        })

        // Live preview
        preview = DotsView(this, demo = true)
        val previewFrame = LinearLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(0xFF2B303B.toInt())
            }
            clipToOutline = true
        }
        previewFrame.addView(preview, LinearLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(230f)))

        status = TextView(this).apply { setPadding(0, dp(14f), 0, dp(6f)) }
        root.addView(status)

        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle, LinearLayout.LayoutParams(-1, dp(56f)))

        header("Look")
        spinner("Pattern", DotSettings.PATTERN_NAMES, settings.pattern) { settings.pattern = it }
        slider("Dots per side", 3, 14, settings.count, { "$it" }) { settings.count = it }
        slider("Dot size", 3, 28, settings.sizeDp, { "$it dp" }) { settings.sizeDp = it }
        slider("Opacity", 10, 100, settings.opacity, { "$it%" }) { settings.opacity = it }
        slider("Distance from edge", 0, 80, settings.marginDp, { "$it dp" }) { settings.marginDp = it }
        colorRow()
        toggleSwitch("Contrast outline", "Thin border so dots stay visible on any background",
            settings.outline) { settings.outline = it }

        header("Motion")
        slider("Sensitivity", 20, 300, settings.sensitivity, { "$it%" }) { settings.sensitivity = it }
        slider("Responsiveness", 0, 100, settings.responsiveness, { "$it" }) { settings.responsiveness = it }
        toggleSwitch("Invert direction", "Dots move with the acceleration instead of against it",
            settings.invert) { settings.invert = it }
        toggleSwitch("Dim when still", "Fade the dots when the phone hasn't moved for a few seconds",
            settings.fadeWhenStill) { settings.fadeWhenStill = it }

        root.addView(Button(this).apply {
            text = "Reset to defaults"
            setOnClickListener {
                settings.reset()
                root.removeAllViews()
                swatches.clear()
                buildUi()
                preview.reload()
            }
        }, LinearLayout.LayoutParams(-1, dp(52f)).apply { topMargin = dp(24f) })

        root.addView(TextView(this).apply {
            text = "Tip: add the “Motion Dots” tile to your Quick Settings panel for one-swipe on/off."
            setPadding(0, dp(16f), 0, 0)
            alpha = 0.7f
        })
    }

    private fun header(title: String) {
        root.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(26f), 0, dp(4f))
        })
    }

    private fun changed() = preview.reload()

    private fun slider(
        label: String, min: Int, max: Int, value: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit
    ) {
        val text = TextView(this).apply {
            this.text = "$label: ${fmt(value)}"
            setPadding(0, dp(12f), 0, 0)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + min
                    text.text = "$label: ${fmt(v)}"
                    if (fromUser) { onChange(v); changed() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(text)
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
    }

    private fun spinner(label: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        root.addView(TextView(this).apply { text = label; setPadding(0, dp(12f), 0, 0) })
        var last = selected
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selected)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos != last) {
                        last = pos
                        onPick(pos); changed()
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
    }

    private fun toggleSwitch(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, 0)
        }
        val sw = Switch(this).apply {
            text = title
            isChecked = value
            setOnCheckedChangeListener { _, checked -> onChange(checked); changed() }
        }
        box.addView(sw)
        box.addView(TextView(this).apply { text = subtitle; alpha = 0.65f; textSize = 13f })
        root.addView(box)
    }

    private fun colorRow() {
        root.addView(TextView(this).apply { text = "Colour"; setPadding(0, dp(14f), 0, dp(6f)) })
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (c in palette) {
            val swatch = View(this)
            swatches.add(c to swatch)
            swatch.setOnClickListener {
                settings.color = c
                refreshSwatches()
                changed()
            }
            row.addView(swatch, LinearLayout.LayoutParams(0, dp(34f), 1f).apply {
                marginEnd = dp(5f)
            })
        }
        refreshSwatches()
        root.addView(row)
    }

    private fun refreshSwatches() {
        for ((c, v) in swatches) {
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                val selected = c == settings.color
                setStroke(if (selected) dp(3f) else dp(1f), if (selected) 0xFF448AFF.toInt() else 0x66888888)
            }
        }
    }

    // ---------------------------------------------------------------- state / actions

    private fun refreshState() {
        val hasPerm = DotsService.hasOverlayPermission(this)
        status.text = when {
            DotsService.running -> "● Running – dots are on screen."
            !hasPerm -> "Needs permission to display over other apps."
            else -> "Stopped."
        }
        toggle.text = if (DotsService.running) "Stop dots" else "Start dots"
    }

    private fun onToggle() {
        if (DotsService.running) {
            DotsService.stop(this)
            return
        }
        if (!DotsService.hasOverlayPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle("Allow display over other apps")
                .setMessage("Motion Dots draws its dots above other apps. On the next screen, " +
                    "enable “Allow display over other apps” for Motion Dots, then come back.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(DotsService.overlayPermissionIntent(this))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        DotsService.start(this)
    }
}
