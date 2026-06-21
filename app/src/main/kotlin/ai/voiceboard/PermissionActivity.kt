package ai.voiceboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * One-time setup screen shown on launcher tap.
 * Guides the user through:
 *   1. Granting RECORD_AUDIO
 *   2. Opening Language & Input settings to enable VoiceBoard
 */
class PermissionActivity : ComponentActivity() {

    private val RC_AUDIO = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        val btnGrant    = findViewById<Button>(R.id.btnGrant)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val tvInstr     = findViewById<TextView>(R.id.tvInstructions)

        updateGrantButton(btnGrant, tvInstr)

        btnGrant.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RC_AUDIO
                )
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val btnGrant = findViewById<Button>(R.id.btnGrant)
        val tvInstr  = findViewById<TextView>(R.id.tvInstructions)
        updateGrantButton(btnGrant, tvInstr)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) {
            val btnGrant = findViewById<Button>(R.id.btnGrant)
            val tvInstr  = findViewById<TextView>(R.id.tvInstructions)
            updateGrantButton(btnGrant, tvInstr)
        }
    }

    private fun updateGrantButton(btn: Button, tv: TextView) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            btn.text = "✓ Microphone granted"
            btn.isEnabled = false
            tv.text = "Microphone access granted.\nNow tap below to enable VoiceBoard as your keyboard."
        } else {
            btn.text = "Grant Microphone Permission"
            btn.isEnabled = true
            tv.text = "Tap below to grant microphone access,\nthen enable VoiceBoard in Settings."
        }
    }
}
