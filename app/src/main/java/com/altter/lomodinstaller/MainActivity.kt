package com.altter.lomodinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import rikka.shizuku.ShizukuProvider
import java.io.File
import kotlin.reflect.KMutableProperty0


class MainActivity : AppCompatActivity() {
    private val switches: HashMap<Switch, String> = HashMap()
    private val switches2: HashMap<Switch, DocumentFile?> = HashMap()
    private lateinit var platformRepository: PlatformRepository
    private val mShizukuShell: ShizukuShell = ShizukuShell(serviceCallback = this::recheckShizuku)

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("GRANTED_URIS", Context.MODE_PRIVATE)
    }

    companion object {
        const val SHIZUKU_CODE = 10023
        const val PREF_MOD_FOLDER = "pref_mod_folder"
        const val PREF_MOD_URI = "pref_mod_uri"
        const val PREF_DATA_URI = "pref_data_uri"
        const val PREF_ONESTORE_URI = "pref_onestore_uri"
        const val PREF_PLAYSTORE_URI = "pref_playstore_uri"
        const val PREF_PLAYSTORE_JP_URI = "pref_playstore_jp_uri"
        const val PREF_FANZA_URI = "pref_fanza_uri"
        const val PREF_TW_URI = "pref_tw_uri"
        const val PREF_TW_R_URI = "pref_tw_r_uri"
        const val PREF_WAYI_URI = "pref_wayi_uri"
        const val PREF_WAYI_R_URI = "pref_wayi_r_uri"
    }
    private var modFolder: String = ""
    private var modDoc: DocumentFile? = null
    private var dataDoc: DocumentFile? = null

    private val storages: List<String> =
        File("/storage").listFiles()?.filter { it.name != "self" }?.map { it.name } ?: listOf()

    private val REQUEST_PERMISSION_RESULT_LISTENER =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                grantResult
            )
        }

    data class PlatformConfig(
        @IdRes val buttonId: Int,
        @IdRes val switchId: Int,
        val prefUriKey: String,
        val packageName: String,
        val requestCode: Int,
        var document: DocumentFile? = null
    ) {
        // Automatically generate androidDataPath with invisible character
        val androidDataPath: String get() = "Android/data/$packageName"

        // Helper properties
        val normalizedPackageName: String get() = packageName.replace(".", "\\.")
        val uriEncodedPath: String get() = androidDataPath.replace("Android/", "A\u200Bndroid/").replace("/", "%2F").replace(".", "%2E")

        fun getStoragePath(version: Int): String = when {
            version >= Build.VERSION_CODES.TIRAMISU -> uriEncodedPath
            version >= Build.VERSION_CODES.Q -> packageName
            else -> androidDataPath
        }
    }
    class PlatformRepository(private val context: Context, private val prefs: SharedPreferences) {
        private val allPlatforms = listOf(
            PlatformConfig(
                buttonId = R.id.button_grant_onestore,
                switchId = R.id.switch_filter_onestore,
                prefUriKey = PREF_ONESTORE_URI,
                packageName = "com.smartjoy.LastOrigin_C",
                requestCode = 991
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_playstore,
                switchId = R.id.switch_filter_playstore,
                prefUriKey = PREF_PLAYSTORE_URI,
                packageName = "com.smartjoy.LastOrigin_G",
                requestCode = 992
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_playstore_jp,
                switchId = R.id.switch_filter_playstore_jp,
                prefUriKey = PREF_PLAYSTORE_JP_URI,
                packageName = "com.pig.laojp.aos",
                requestCode = 993
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_fanza,
                switchId = R.id.switch_filter_fanza,
                prefUriKey = PREF_FANZA_URI,
                packageName = "jp.co.fanzagames.lastorigin_r",
                requestCode = 994
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_tw,
                switchId = R.id.switch_filter_tw,
                prefUriKey = PREF_TW_URI,
                packageName = "com.valofe.laotw",
                requestCode = 995
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_tw_r,
                switchId = R.id.switch_filter_tw_r,
                prefUriKey = PREF_TW_R_URI,
                packageName = "com.valofe.laotw.ero",
                requestCode = 996
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_wayi,
                switchId = R.id.switch_filter_wayi,
                prefUriKey = PREF_WAYI_URI,
                packageName = "com.valofe.laotw.wayi_n",
                requestCode = 997
            ),
            PlatformConfig(
                buttonId = R.id.button_grant_wayi_r,
                switchId = R.id.switch_filter_wayi_r,
                prefUriKey = PREF_WAYI_R_URI,
                packageName = "com.valofe.laotw.wayi",
                requestCode = 998
            )
        )
        fun getPlatformsForVersion(version: Int): List<PlatformConfig> {
            return allPlatforms.onEach { platform ->
                platform.document = if (version >= Build.VERSION_CODES.TIRAMISU) {
                    safeParseDocumentUri(prefs.getString(platform.prefUriKey, null))
                } else {
                    null
                }
            }
        }

        fun safeParseDocumentUri(uriString: String?): DocumentFile? {
            return try {
                uriString?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        platformRepository = PlatformRepository(this, prefs)
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        // Load persisted data
        initializePersistedData()

        // Setup UI based on Android version
        setupVersionSpecificUI()

        // Set up remaining UI components
        setupUIComponents()

        // Check Shizuku status
        recheckShizuku()

    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    private fun initializePersistedData() {
        // Load mod folder and URI
        modFolder = prefs.getString(PREF_MOD_FOLDER, null) ?: ""
        modDoc = platformRepository.safeParseDocumentUri(prefs.getString(PREF_MOD_URI, null))

        // Load data document (only for Q-Tiramisu)
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.TIRAMISU) {
            dataDoc = platformRepository.safeParseDocumentUri(prefs.getString(PREF_DATA_URI, null))
        }
    }

    private fun setupVersionSpecificUI() {
        val label = findViewById<TextView>(R.id.tip_text)
        val grantButton = findViewById<Button>(R.id.button_grant)
        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        // Setup button visibility
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                grantButton.visibility = View.GONE
                platforms.forEach { findViewById<Button>(it.buttonId).visibility = View.GONE }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                label.setText(R.string.tip_saf_selected)
                platforms.forEach { findViewById<Button>(it.buttonId).visibility = View.GONE }
            }
            else -> {
                label.setText(R.string.tip_saf_selected)
                grantButton.visibility = View.GONE
            }
        }

        // Setup platform switches
        platforms.forEach { platform ->
            val switch = findViewById<Switch>(platform.switchId)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    switches2[switch] = platform.document
                }
                else -> {
                    switches[switch] = platform.getStoragePath(Build.VERSION.SDK_INT)
                }
            }
        }
    }

    private fun setupUIComponents() {
        // Update mod folder display
        if (modFolder.isEmpty()) {
            findViewById<TextView>(R.id.mod_text).text = getString(R.string.EMPTY_MOD_FOLDER)
        } else {
            findViewById<TextView>(R.id.mod_text).text =
                getString(R.string.CURRENT_MOD_FOLDER, modFolder)
        }

        // Set up button click listeners
        findViewById<Button>(R.id.button_patch).setOnClickListener { beforePatch() }
        findViewById<Button>(R.id.button_folder).setOnClickListener { selectFolder() }
        findViewById<Button>(R.id.button_clear).setOnClickListener { showClearConfirmation() }
        findViewById<Button>(R.id.button_grant).setOnClickListener { showGrantPermissionDialog() }
        findViewById<Button>(R.id.button_shizuku).setOnClickListener { requestShizukuPermission() }

        // Set up platform button click listeners
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT).forEach { platform ->
                findViewById<Button>(platform.buttonId).setOnClickListener {
                    showPlatformPermissionDialog(platform)
                }
            }
        }

        updateSwitches()
    }

    private fun requestShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            requestPermissions(arrayOf(ShizukuProvider.PERMISSION), SHIZUKU_CODE)
        } else {
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.CLEAR_TITLE)
            setMessage(R.string.CLEAR_MESSAGE)
            setPositiveButton(R.string.CLEAR_OK) { _, _ -> beforeClear() }
        }.create().show()
    }

    private fun showGrantPermissionDialog() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.GRANT_PERMISSION_TITLE)
            setMessage(R.string.GRANT_PERMISSION_MESSAGE)
            setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ -> handleGrantPermission() }
        }.create().show()
    }

    private fun handleGrantPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sm = getSystemService(STORAGE_SERVICE) as StorageManager
            val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
            val startSubDir = "A\u200Bndroid%2Fdata"

            var uri = intent.getParcelableExtra<Uri>("android.provider.extra.INITIAL_URI")
            var scheme = uri.toString()
            scheme = scheme.replace("/root/", "/document/")
            scheme += "%3A$startSubDir"
            uri = Uri.parse(scheme)

            intent.putExtra("android.provider.extra.INITIAL_URI", uri)
            startActivityForResult(intent, 44)
        } else {
            val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE").apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                putExtra("android.content.extra.SHOW_ADVANCED", true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val uri = getSAFTreeUri()
                    putExtra(
                        "android.provider.extra.INITIAL_URI",
                        DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getTreeDocumentId(uri)
                        )
                    )
                }
            }
            startActivityForResult(intent, 44)
        }
    }

    private fun showPlatformPermissionDialog(platform: PlatformConfig) {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.GRANT_PERMISSION_TITLE)
            setMessage(R.string.GRANT_PERMISSION_MESSAGE)
            setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val sm = getSystemService(STORAGE_SERVICE) as StorageManager
                    newPermission(platform.uriEncodedPath, platform.requestCode, sm)
                }
            }
        }.create().show()
    }

    // Granted 상황에 맞춰 사용 가능 갱신
    private fun updateSwitches() {
        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        when {
            mShizukuShell.isReady() -> {
                platforms.forEach { platform ->
                    val switch = findViewById<Switch>(platform.switchId)
                    val fullPath = "/storage/emulated/0/${platform.androidDataPath}"

                    if (mShizukuShell.checkDirExist(fullPath)) {
                        switches[switch] = fullPath
                        switch.apply {
                            isChecked = true
                            isEnabled = true
                        }
                    } else {
                        switch.apply {
                            isChecked = false
                            isEnabled = false
                        }
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                platforms.forEach { platform ->
                    val switch = findViewById<Switch>(platform.switchId)
                    val usable = platform.document != null

                    switch.apply {
                        isChecked = usable
                        isEnabled = usable
                    }
                }
            }
            else -> {
                platforms.forEach { platform ->
                    val switch = findViewById<Switch>(platform.switchId)
                    val usable = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                            this.dataDoc?.let { findMatchedDoc(platform.getStoragePath(Build.VERSION.SDK_INT), it) } != null
                        else ->
                            File("/storage/emulated/0/${platform.androidDataPath}/files/UnityCache/Shared/").exists()
                    }

                    switch.apply {
                        isChecked = usable
                        isEnabled = usable
                    }
                }
            }
        }
    }

    private fun newPermission(path: String, code: Int, sm: StorageManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()

            var uri =
                intent.getParcelableExtra<Uri>("android.provider.extra.INITIAL_URI")

            var scheme = uri.toString()


            scheme = scheme.replace("/root/", "/document/")

            scheme += "%3A$path"

            uri = Uri.parse(scheme)

            intent.putExtra("android.provider.extra.INITIAL_URI", uri)

            startActivityForResult(
                intent,
                code
            )
        }
    }

    @SuppressLint("WrongViewCast")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        val uri = data.data!!
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        when (requestCode) {
            44 -> handleDataUri(uri)
            9999 -> handleModUri(uri)
            else -> {
                platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)
                    .find { it.requestCode == requestCode }
                    ?.let { platform ->
                        handlePlatformUri(
                            uri,
                            platform,
                        )
                    }
            }
        }
    }

    private fun handleDataUri(uri: Uri) {
        DocumentFile.fromTreeUri(this, uri)?.let { doc ->
            dataDoc = doc
            prefs.edit().putString(PREF_DATA_URI, doc.uri.toString()).apply()
            updateSwitches()
        }
    }

    private fun handlePlatformUri(
        uri: Uri,
        platform: PlatformConfig
    ) {
        val doc = DocumentFile.fromTreeUri(this, uri)

        if (doc == null) {
            logFunction(getString(R.string.FAIL_FOLDER_13))
            return
        }

        doc.name?.let { logFunction(it) }

        if (doc.name == platform.packageName) {
            platform.document = doc
            prefs.edit().putString(platform.prefUriKey, doc.uri.toString()).apply()
            val switch = findViewById<Switch>(platform.switchId)
            switches2[switch] = doc
            switch.apply {
                isChecked = true
                isEnabled = true
            }
        } else {
            logFunction(getString(R.string.WRONG_FOLDER_13))
            platform.document = null
        }
    }

    private fun handleModUri(uri: Uri) {
        val file = uri.path?.let { File(it) } ?: return
        DocumentFile.fromTreeUri(this, uri)?.let { doc ->
            modDoc = doc
            prefs.edit().putString(PREF_MOD_URI, doc.uri.toString()).apply()
        }

        val split = file.path.split(":").toTypedArray()
        modFolder = split.getOrNull(1)?.plus("/") ?: return
        prefs.edit().putString(PREF_MOD_FOLDER, modFolder).apply()
        findViewById<TextView>(R.id.mod_text).text =
            getString(R.string.CURRENT_MOD_FOLDER, modFolder)
    }

    @SuppressLint("SetTextI18n")
    fun logFunction(text: String) {
        CoroutineScope(Main).launch {
            val el = findViewById<TextView>(R.id.textLog)
            el.text = text + "\n" + el.text.toString()
        }
    }

    private fun beforePatch() {
        CoroutineScope(Default).launch { this@MainActivity.doPatch() }
    }

    private fun beforeClear() {
        CoroutineScope(Default).launch { this@MainActivity.doClear() }
    }

    private fun selectFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, 9999)
    }

    private fun doPatch() {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        CoroutineScope(Main).launch { patchBtn.isEnabled = false }
        if (mShizukuShell.isReady()) {
            runPatcherShizuku(this, this.modFolder, this.switches, 1, { s -> this.logFunction(s) }, mShizukuShell)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runPatcherSAF13(this, this.modDoc, this.switches2, 1) { s -> this.logFunction(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runPatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 1) { s -> this.logFunction(s) }
        else
            runPatcher(this, this.storages, this.modFolder, this.switches, 1) { s -> this.logFunction(s) }

        CoroutineScope(Main).launch { patchBtn.isEnabled = true }
    }

    private fun doClear() {
        val clearBtn = findViewById<Button>(R.id.button_clear)
        CoroutineScope(Main).launch { clearBtn.isEnabled = false }
        if (mShizukuShell.isReady()) {
            runPatcherShizuku(this, this.modFolder, this.switches, 2, { s -> this.logFunction(s) }, mShizukuShell)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runPatcherSAF13(this, this.modDoc, this.switches2, 2) { s -> this.logFunction(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runPatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 2) { s -> this.logFunction(s) }
        else
            runPatcher(this, this.storages, this.modFolder, this.switches, 2) { s -> this.logFunction(s) }

        CoroutineScope(Main).launch { clearBtn.isEnabled = true }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (granted) recheckShizuku()
    }

    private fun checkPermission(needRequest: Boolean) {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            permissions.any { p ->
                ContextCompat.checkSelfPermission(
                    this,
                    p
                ) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            patchBtn.isEnabled = false
            if (needRequest)
                this.requestPermission(permissions)
        } else
            patchBtn.isEnabled = true

        this.updateSwitches()
    }

    private fun requestPermission(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, 0)
        this.checkPermission(false)

        if (permissions.any { p ->
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    p
                )
            }) {
            Toast.makeText(
                this,
                "To run the app, you need to set the storage permission.\nPlease set it manually in the app information.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun recheckShizuku() {
        val shizukuButton = findViewById<Button>(R.id.button_shizuku)
        val shizukuLabel = findViewById<TextView>(R.id.tip_text_shizuku)
        val safLabel = findViewById<TextView>(R.id.tip_text)

        // Hide all platform grant buttons that would be replaced by Shizuku
        val platformButtons = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)
            .map { it.buttonId }
            .map { findViewById<Button>(it) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !mShizukuShell.isSupported()) {
            shizukuButton.visibility = View.GONE
            shizukuLabel.visibility = View.GONE
        } else {
            // Hide SAF-related UI elements when Shizuku is available
            findViewById<Button>(R.id.button_grant).visibility = View.GONE
            platformButtons.forEach { it.visibility = View.GONE }
            safLabel.visibility = View.GONE

            if (mShizukuShell.isReady()) {
                shizukuLabel.setText(R.string.tip_saf_selected)
                shizukuLabel.visibility = View.VISIBLE
                updateSwitches()
            } else {
                shizukuLabel.visibility = View.GONE
            }
        }
    }
}