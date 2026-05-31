package com.altter.lomodinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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


class MainActivity : AppCompatActivity() {
    private lateinit var platformRepository: PlatformRepository
    private val mShizukuShell: ShizukuShell = ShizukuShell(serviceCallback = this::recheckShizuku)

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("GRANTED_URIS", Context.MODE_PRIVATE)
    }

    companion object {
        const val SHIZUKU_CODE = 10023
        const val PREF_DATA_URI = "pref_data_uri"
    }
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

    enum class PlatformGroup(@StringRes val labelRes: Int) {
        KR(R.string.group_kr),
        JP(R.string.group_jp),
        TW_GL(R.string.group_tw_gl)
    }

    class PlatformConfig(
        val prefUriKey: String,
        val packageName: String,
        val requestCode: Int,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        val group: PlatformGroup,
        var document: DocumentFile? = null,
        var legacyPath: String? = null
    ) {
        var switch: Switch? = null
        var grantButton: Button? = null
        var modButton: Button? = null
        var modText: TextView? = null
        var modFolder: String = ""
        var modDoc: DocumentFile? = null

        val modRequestCode: Int get() = requestCode + 2000
        val prefModUriKey: String get() = prefUriKey.replace("_uri", "_mod_uri")
        val prefModFolderKey: String get() = prefUriKey.replace("_uri", "_mod_folder")

        val androidDataPath: String get() = "Android/data/$packageName"
        val uriEncodedPath: String get() = androidDataPath
            .replace("Android/", "A\u200Bndroid/")
            .replace("/", "%2F")
            .replace(".", "%2E")

        fun getStoragePath(version: Int): String = when {
            version >= Build.VERSION_CODES.TIRAMISU -> uriEncodedPath
            version >= Build.VERSION_CODES.Q -> packageName
            else -> androidDataPath
        }
    }

    class PlatformRepository(private val context: Context, private val prefs: SharedPreferences) {
        val allPlatforms = listOf(
            PlatformConfig("pref_onestore_uri",     "com.smartjoy.LastOrigin_C",     991,  R.string.platform_onestore,     R.drawable.onestore,    PlatformGroup.KR),
            PlatformConfig("pref_playstore_uri",    "com.smartjoy.LastOrigin_G",     992,  R.string.platform_playstore,    R.drawable.playstore,   PlatformGroup.KR),
            PlatformConfig("pref_playstore_jp_uri", "com.pig.laojp.aos",             993,  R.string.platform_playstore_jp, R.drawable.playstore_jp, PlatformGroup.JP),
            PlatformConfig("pref_fanza_uri",        "jp.co.fanzagames.lastorigin_r", 994,  R.string.platform_fanza_jp,     R.drawable.fanza,       PlatformGroup.JP),
            PlatformConfig("pref_tw_uri",           "com.valofe.laotw",              995,  R.string.platform_tw,           R.drawable.pmang,       PlatformGroup.TW_GL),
            PlatformConfig("pref_tw_erolabs_r_uri", "com.valofe.laotw.ero",          996,  R.string.platform_tw_erolabs_r, R.drawable.pmang,       PlatformGroup.TW_GL),
            PlatformConfig("pref_tw_vfun_uri",      "com.valofe.lastorigin.vfun.global",  1000, R.string.platform_tw_vfun,      R.drawable.pmang,       PlatformGroup.TW_GL),
            PlatformConfig("pref_wayi_uri",         "com.valofe.laotw.wayi_n",       997,  R.string.platform_wayi,         R.drawable.pmang,       PlatformGroup.TW_GL),
            PlatformConfig("pref_wayi_r_uri",       "com.valofe.laotw.wayi",         998,  R.string.platform_wayi_r,       R.drawable.pmang,       PlatformGroup.TW_GL),
            PlatformConfig("pref_tw_pmang_r_uri",   "com.valofe.laotw.pmang",        999,  R.string.platform_tw_r,         R.drawable.pmang_r,     PlatformGroup.TW_GL),
        )

        fun getPlatformsForVersion(version: Int): List<PlatformConfig> {
            return allPlatforms.onEach { platform ->
                platform.document = if (version >= Build.VERSION_CODES.TIRAMISU) {
                    safeParseDocumentUri(prefs.getString(platform.prefUriKey, null))
                } else {
                    null
                }
                platform.modDoc = safeParseDocumentUri(prefs.getString(platform.prefModUriKey, null))
                platform.modFolder = prefs.getString(platform.prefModFolderKey, null) ?: ""
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

        // On Android 15+ edge-to-edge, content draws under ActionBar; push it down with insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val captionBar = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            val navBars   = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(
                top    = statusBars.top + captionBar.top,
                bottom = navBars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        platformRepository = PlatformRepository(this, prefs)
        this.checkPermission(true)
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        // Load persisted data
        initializePersistedData()

        // Inflate platform cards before version-specific setup needs the switches
        inflatePlatformCards()

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
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.TIRAMISU) {
            dataDoc = platformRepository.safeParseDocumentUri(prefs.getString(PREF_DATA_URI, null))
        }
    }

    private fun setupVersionSpecificUI() {
        val label = findViewById<TextView>(R.id.tip_text)
        val safCard = findViewById<View>(R.id.card_saf)
        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                safCard.visibility = View.GONE
                platforms.forEach { it.grantButton?.visibility = View.GONE }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                label.setText(R.string.tip_saf_selected)
                platforms.forEach { it.grantButton?.visibility = View.GONE }
            }
            else -> {
                safCard.visibility = View.GONE
            }
        }

        // Store legacy path for non-Tiramisu builds (Shizuku overrides this in updateSwitches)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            platforms.forEach { it.legacyPath = it.getStoragePath(Build.VERSION.SDK_INT) }
        }
    }

    private fun setupUIComponents() {
        // Set up tabs
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        val permissionsContent = findViewById<ScrollView>(R.id.tab_permissions_content)
        val patchContent = findViewById<LinearLayout>(R.id.tab_patch_content)
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_permissions))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_patch))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { permissionsContent.visibility = View.VISIBLE; patchContent.visibility = View.GONE }
                    1 -> { permissionsContent.visibility = View.GONE; patchContent.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Set up button click listeners
        findViewById<Button>(R.id.button_patch).setOnClickListener { beforePatch() }
        findViewById<Button>(R.id.button_clear).setOnClickListener { showClearConfirmation() }
        findViewById<Button>(R.id.button_grant).setOnClickListener { showGrantPermissionDialog() }
        findViewById<Button>(R.id.button_shizuku).setOnClickListener { requestShizukuPermission() }
        findViewById<Button>(R.id.button_copy_log).setOnClickListener { copyLogToClipboard() }
        findViewById<Button>(R.id.button_clear_log).setOnClickListener { clearLog() }

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
            setNegativeButton(android.R.string.cancel, null)
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

    private fun inflatePlatformCards() {
        val container = findViewById<LinearLayout>(R.id.platform_cards_container)
        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        PlatformGroup.values().forEach { group ->
            val groupPlatforms = platforms.filter { it.group == group }
            if (groupPlatforms.isEmpty()) return@forEach

            // Section header
            val header = layoutInflater.inflate(R.layout.item_platform_header, container, false)
            header.findViewById<TextView>(R.id.header_title).setText(group.labelRes)
            container.addView(header)

            // 2-column grid: chunk into rows of 2
            groupPlatforms.chunked(2).forEach { row ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                row.forEach { platform ->
                    val card = layoutInflater.inflate(R.layout.item_platform, rowLayout, false)
                    (card.layoutParams as LinearLayout.LayoutParams).apply {
                        width = 0
                        weight = 1f
                    }
                    card.findViewById<ImageView>(R.id.platform_icon).setImageResource(platform.iconRes)
                    card.findViewById<TextView>(R.id.platform_name).setText(platform.labelRes)
                    val sw = card.findViewById<Switch>(R.id.platform_switch)
                    platform.switch = sw
                    val btn = card.findViewById<Button>(R.id.platform_grant_button)
                    platform.grantButton = btn
                    btn.setOnClickListener { showPlatformPermissionDialog(platform) }
                    val modBtn = card.findViewById<Button>(R.id.platform_mod_button)
                    platform.modButton = modBtn
                    val modTv = card.findViewById<TextView>(R.id.platform_mod_text)
                    platform.modText = modTv
                    modTv.text = if (platform.modFolder.isEmpty()) getString(R.string.EMPTY_MOD_FOLDER)
                                 else getString(R.string.CURRENT_MOD_FOLDER, platform.modFolder)
                    modBtn.setOnClickListener { selectPlatformFolder(platform) }
                    sw.setOnCheckedChangeListener { _, isChecked -> modBtn.isEnabled = isChecked }
                    rowLayout.addView(card)
                }

                // Pad with empty space if row has only 1 item
                if (row.size < 2) {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    }
                    rowLayout.addView(spacer)
                }

                container.addView(rowLayout)
            }
        }
    }

    private fun updateSwitches() {
        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        when {
            mShizukuShell.isReady() -> {
                platforms.forEach { platform ->
                    val switch = platform.switch ?: return@forEach
                    val fullPath = "/storage/emulated/0/${platform.androidDataPath}"
                    if (mShizukuShell.checkDirExist(fullPath)) {
                        platform.legacyPath = fullPath
                        switch.apply { isChecked = true; isEnabled = true }
                    } else {
                        switch.apply { isChecked = false; isEnabled = false }
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                platforms.forEach { platform ->
                    val switch = platform.switch ?: return@forEach
                    val usable = platform.document != null
                    switch.apply { isChecked = usable; isEnabled = usable }
                }
            }
            else -> {
                platforms.forEach { platform ->
                    val switch = platform.switch ?: return@forEach
                    val usable = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                            dataDoc?.let { findMatchedDoc(platform.getStoragePath(Build.VERSION.SDK_INT), it) } != null
                        else ->
                            File("/storage/emulated/0/${platform.androidDataPath}/files/UnityCache/Shared/").exists()
                    }
                    switch.apply { isChecked = usable; isEnabled = usable }
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

        val platforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)
        when (requestCode) {
            44 -> handleDataUri(uri)
            else -> {
                platforms.find { it.requestCode == requestCode }
                    ?.let { handlePlatformUri(uri, it) }
                    ?: platforms.find { it.modRequestCode == requestCode }
                        ?.let { handlePlatformModUri(uri, it) }
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
            platform.switch?.apply { isChecked = true; isEnabled = true }
        } else {
            logFunction(getString(R.string.WRONG_FOLDER_13))
            platform.document = null
        }
    }

    private fun handlePlatformModUri(uri: Uri, platform: PlatformConfig) {
        val file = uri.path?.let { File(it) } ?: return
        DocumentFile.fromTreeUri(this, uri)?.let { doc ->
            platform.modDoc = doc
            prefs.edit().putString(platform.prefModUriKey, doc.uri.toString()).apply()
        }
        val split = file.path.split(":").toTypedArray()
        platform.modFolder = split.getOrNull(1)?.plus("/") ?: return
        prefs.edit().putString(platform.prefModFolderKey, platform.modFolder).apply()
        platform.modText?.text = getString(R.string.CURRENT_MOD_FOLDER, platform.modFolder)
    }

    @SuppressLint("SetTextI18n")
    fun logFunction(text: String) {
        CoroutineScope(Main).launch {
            val el = findViewById<TextView>(R.id.textLog)
            el.text = text + "\n" + el.text.toString()
        }
    }

    private fun copyLogToClipboard() {
        val text = findViewById<TextView>(R.id.textLog).text.toString()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("log", text))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun clearLog() {
        findViewById<TextView>(R.id.textLog).text = ""
    }

    private fun beforePatch() {
        CoroutineScope(Default).launch { this@MainActivity.doPatch() }
    }

    private fun beforeClear() {
        CoroutineScope(Default).launch { this@MainActivity.doClear() }
    }

    private fun selectPlatformFolder(platform: PlatformConfig) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, platform.modRequestCode)
    }

    private fun doPatch() {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        CoroutineScope(Main).launch { patchBtn.isEnabled = false }
        val pairs = buildPatchIO() ?: run {
            logFunction(getString(R.string.NO_MOD_DOC))
            CoroutineScope(Main).launch { patchBtn.isEnabled = true }
            return
        }
        runPatcher(this, pairs, 1) { s -> logFunction(s) }
        CoroutineScope(Main).launch { patchBtn.isEnabled = true }
    }

    private fun doClear() {
        val clearBtn = findViewById<Button>(R.id.button_clear)
        CoroutineScope(Main).launch { clearBtn.isEnabled = false }
        val pairs = buildPatchIO() ?: run {
            logFunction(getString(R.string.NO_MOD_DOC))
            CoroutineScope(Main).launch { clearBtn.isEnabled = true }
            return
        }
        runPatcher(this, pairs, 2) { s -> logFunction(s) }
        CoroutineScope(Main).launch { clearBtn.isEnabled = true }
    }

    private fun buildPatchIO(): List<Pair<ModSource, PatchPlatform>>? {
        val allPlatforms = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)

        val pairs: List<Pair<ModSource, PatchPlatform>> = when {
            mShizukuShell.isReady() -> {
                allPlatforms.mapNotNull { platform ->
                    val switch = platform.switch ?: return@mapNotNull null
                    val gamePath = platform.legacyPath ?: return@mapNotNull null
                    var path = platform.modFolder.trimEnd('/')
                    if (path.isEmpty()) return@mapNotNull null
                    if (!mShizukuShell.checkDirExist(path) && mShizukuShell.checkDirExist("/storage/emulated/0/$path"))
                        path = "/storage/emulated/0/$path"
                    if (!mShizukuShell.checkDirExist(path)) return@mapNotNull null
                    Pair(ShellModSource(path, mShizukuShell), ShellPlatform(switch, gamePath, mShizukuShell))
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                allPlatforms.mapNotNull { platform ->
                    val switch = platform.switch ?: return@mapNotNull null
                    val mod = platform.modDoc ?: return@mapNotNull null
                    val doc = platform.document ?: return@mapNotNull null
                    val shared = findMatchedDoc13(doc) ?: return@mapNotNull null
                    Pair(DocumentModSource(mod), DocumentPlatform(switch, this, shared))
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val data = dataDoc ?: return null
                allPlatforms.mapNotNull { platform ->
                    val switch = platform.switch ?: return@mapNotNull null
                    val mod = platform.modDoc ?: return@mapNotNull null
                    val path = platform.legacyPath ?: return@mapNotNull null
                    val shared = findMatchedDoc(path, data) ?: return@mapNotNull null
                    Pair(DocumentModSource(mod), DocumentPlatform(switch, this, shared))
                }
            }

            else -> {
                allPlatforms.mapNotNull { platform ->
                    val switch = platform.switch ?: return@mapNotNull null
                    val path = platform.legacyPath ?: return@mapNotNull null
                    val modPath = platform.modFolder
                    if (modPath.isEmpty()) return@mapNotNull null
                    Pair(FileModSource(modPath, storages), FilePlatform(switch, path, storages))
                }
            }
        }

        return pairs.takeIf { it.isNotEmpty() }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (granted) recheckShizuku()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            this.checkPermission(false)
        }
        else if (requestCode == SHIZUKU_CODE) {
            recheckShizuku()
        }
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
        val shizukuCard = findViewById<View>(R.id.card_shizuku)
        val shizukuLabel = findViewById<TextView>(R.id.tip_text_shizuku)
        val safCard = findViewById<View>(R.id.card_saf)
        val platformButtons = platformRepository.getPlatformsForVersion(Build.VERSION.SDK_INT)
            .mapNotNull { it.grantButton }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !mShizukuShell.isSupported()) {
            shizukuCard.visibility = View.GONE
        } else {
            shizukuCard.visibility = View.VISIBLE
            safCard.visibility = View.GONE
            platformButtons.forEach { it.visibility = View.GONE }

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