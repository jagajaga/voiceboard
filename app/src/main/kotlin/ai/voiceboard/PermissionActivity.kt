package ai.voiceboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.*

/**
 * Setup screen — three steps:
 *  1. Grant RECORD_AUDIO
 *  2. Enable VoiceBoard in Input Method Settings
 *  3. Enter OpenAI API key (stored in SharedPreferences, never in the APK)
 */
class PermissionActivity : Activity() {

    private val RC_AUDIO = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        val btnGrant    = findViewById<Button>(R.id.btnGrant)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val tvInstr     = findViewById<TextView>(R.id.tvInstructions)
        val etApiKey    = findViewById<EditText>(R.id.etApiKey)
        val btnSaveKey  = findViewById<Button>(R.id.btnSaveKey)
        val tvKeyStatus = findViewById<TextView>(R.id.tvKeyStatus)

        // Pre-fill if already saved
        val saved = Prefs.getApiKey(this)
        if (saved.isNotEmpty()) {
            etApiKey.setText(saved)
            tvKeyStatus.text = "✓ Key saved"
        }

        updateGrantButton(btnGrant, tvInstr)

        btnGrant.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSaveKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                tvKeyStatus.text = "Key cannot be empty"
                return@setOnClickListener
            }
            Prefs.setApiKey(this, key)
            tvKeyStatus.text = "✓ Key saved"
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }

        // Allow saving with the keyboard Done action
        etApiKey.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSaveKey.performClick(); true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        updateGrantButton(
            findViewById(R.id.btnGrant),
            findViewById(R.id.tvInstructions)
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) {
            updateGrantButton(
                findViewById(R.id.btnGrant),
                findViewById(R.id.tvInstructions)
            )
        }
    }

    private fun updateGrantButton(btn: Button, tv: TextView) {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            btn.text = "✓ Microphone granted"
            btn.isEnabled = false
            tv.text = "All set! Enable VoiceBoard below, then enter your API key."
        } else {
            btn.text = "Grant Microphone Permission"
            btn.isEnabled = true
            tv.text = "Step 1: grant microphone access.\nStep 2: enable VoiceBoard in Settings.\nStep 3: paste your OpenAI key below."
        }
    }
}
