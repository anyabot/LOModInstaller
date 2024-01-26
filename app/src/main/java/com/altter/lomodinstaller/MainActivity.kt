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
    private val switches: HashMap<Switch, String> = HashMap()
    private val switches2: HashMap<Switch, DocumentFile?> = HashMap()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("GRANTED_URIS", Context.MODE_PRIVATE)
    }

    private var modFolder: String = ""
    private var modDoc: DocumentFile? = null
    private var dataDoc: DocumentFile? = null

    private var oneStoreDoc: DocumentFile? = null
    private var playStoreDoc: DocumentFile? = null
    private var playStoreJpDoc: DocumentFile? = null
    private var fanzaDoc: DocumentFile? = null

    private val storages: List<String> =
        File("/storage").listFiles()?.filter { it.name != "self" }?.map { it.name } ?: listOf()

    private val mShizukuShell: ShizukuShell = ShizukuShell(serviceCallback = this::recheckShizuku)
    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (granted) recheckShizuku()
    }

    private val REQUEST_PERMISSION_RESULT_LISTENER =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                grantResult
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //region Load Saved File Paths
        val tempModFolder = prefs.getString(PREF_MOD_FOLDER, null)
        if (tempModFolder != null) this.modFolder = tempModFolder
        val tempModURI = prefs.getString(PREF_MOD_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) try {
            this.modDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempModURI))
        }
        catch (e: Exception) {
            this.modDoc = null
            this.modFolder = ""
        }
        val tempDataURI = prefs.getString(PREF_DATA_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) try {
            this.dataDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempDataURI))
        }
        catch (e: Exception) {
            this.dataDoc = null
        }
        val oneStoreURI = prefs.getString(PREF_ONESTORE_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
            this.oneStoreDoc = DocumentFile.fromTreeUri(this, Uri.parse(oneStoreURI))
        }
        catch (e: Exception) {
            this.oneStoreDoc = null
        }
        val playStoreURI = prefs.getString(PREF_PLAYSTORE_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
            this.playStoreDoc = DocumentFile.fromTreeUri(this, Uri.parse(playStoreURI))
        }
        catch (e: Exception) {
            this.playStoreDoc = null
        }
        val playStoreJpURI = prefs.getString(PREF_PLAYSTORE_JP_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
            this.playStoreJpDoc = DocumentFile.fromTreeUri(this, Uri.parse(playStoreJpURI))
        }
        catch (e: Exception) {
            this.playStoreJpDoc = null
        }
        val fanzaURI = prefs.getString(PREF_FANZA_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
            this.fanzaDoc = DocumentFile.fromTreeUri(this, Uri.parse(fanzaURI))
        }
        catch (e: Exception) {
            this.fanzaDoc = null
        }
        //endregion

        this.checkPermission(true)
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        val onestoreSwitch = findViewById<Switch>(R.id.switch_filter_onestore)
        val playstoreSwitch = findViewById<Switch>(R.id.switch_filter_playstore)
        val playstoreJPSwitch = findViewById<Switch>(R.id.switch_filter_playstore_jp)
        val fanzaSwitch = findViewById<Switch>(R.id.switch_filter_fanza)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            switches2[onestoreSwitch] = this.oneStoreDoc
            switches2[playstoreSwitch] = this.playStoreDoc
            switches2[playstoreJPSwitch] = this.playStoreJpDoc
            switches2[fanzaSwitch] = this.fanzaDoc
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switches[onestoreSwitch] = "com.smartjoy.LastOrigin_C"
            switches[playstoreSwitch] = "com.smartjoy.LastOrigin_G"
            switches[playstoreJPSwitch] = "com.pig.laojp.aos"
            switches[fanzaSwitch] = "jp.co.fanzagames.lastorigin_r"
        }
        else {
            switches[onestoreSwitch] = "Android/data/com.smartjoy.LastOrigin_C"
            switches[playstoreSwitch] = "Android/data/com.smartjoy.LastOrigin_G"
            switches[playstoreJPSwitch] = "Android/data/com.pig.laojp.aos"
            switches[fanzaSwitch] = "Android/data/jp.co.fanzagames.lastorigin_r"
        }

        val label = findViewById<TextView>(R.id.tip_text)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            findViewById<Button>(R.id.button_grant).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_onestore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore_jp).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_fanza).visibility = View.GONE
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            label.setText(R.string.tip_saf_selected)

            findViewById<Button>(R.id.button_grant_onestore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore_jp).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_fanza).visibility = View.GONE
        } else {
            label.setText(R.string.tip_saf_selected)
            findViewById<Button>(R.id.button_grant).visibility = View.GONE
        }

        recheckShizuku()
        this.updateSwitches()
        if (this.modFolder == "") findViewById<TextView>(R.id.mod_text).text = ""
        else findViewById<TextView>(R.id.mod_text).text = String.format(this.getString(R.string.CURRENT_MOD_FOLDER), this.modFolder)
        findViewById<Button>(R.id.button_patch).setOnClickListener { this.beforePatch() }
        findViewById<Button>(R.id.button_folder).setOnClickListener { this.selectFolder() }
        findViewById<Button>(R.id.button_clear).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.CLEAR_TITLE)
                setMessage(R.string.CLEAR_MESSAGE)
                setPositiveButton(R.string.CLEAR_OK) { _, _ ->
                    beforeClear()
                }
            }
                .create()
                .show()
        }
        findViewById<Button>(R.id.button_grant).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        val sm = context.getSystemService(STORAGE_SERVICE) as StorageManager

                        val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()

                        val startSubDir = "Android%2Fdata"

                        var uri =
                            intent.getParcelableExtra<Uri>("android.provider.extra.INITIAL_URI")

                        var scheme = uri.toString()


                        scheme = scheme.replace("/root/", "/document/")

                        scheme += "%3A$startSubDir"

                        uri = Uri.parse(scheme)

                        intent.putExtra("android.provider.extra.INITIAL_URI", uri)

                        startActivityForResult(
                            intent,
                            44
                        )
                    }
                    else {
                        val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE").apply {
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

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
            }
                .create()
                .show()
        }
        findViewById<Button>(R.id.button_shizuku).setOnClickListener {
//            mShizukuShell.requestPermission(this, SHIZUKU_CODE)
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                requestPermissions(arrayOf(ShizukuProvider.PERMISSION), SHIZUKU_CODE)
            } else {
                Shizuku.requestPermission(SHIZUKU_CODE)
            }
        }
        findViewById<Button>(R.id.button_grant_onestore).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val sm = context.getSystemService(STORAGE_SERVICE) as StorageManager
                        newPermission("Android%2Fdata%2Fcom%2Esmartjoy%2ELastOrigin%5FC", 991, sm)
                    }
                }
            }
                .create()
                .show()
        }
        findViewById<Button>(R.id.button_grant_playstore).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val sm = context.getSystemService(STORAGE_SERVICE) as StorageManager
                        newPermission("Android%2Fdata%2Fcom%2Esmartjoy%2ELastOrigin%5FG", 992, sm)
                    }
                }
            }
                .create()
                .show()
        }
        findViewById<Button>(R.id.button_grant_playstore_jp).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val sm = context.getSystemService(STORAGE_SERVICE) as StorageManager
                        newPermission("Android%2Fdata%2Fcom%2Epig%2Elaojp%2Eaos", 993, sm)
                    }
                }
            }
                .create()
                .show()
        }
        findViewById<Button>(R.id.button_grant_fanza).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val sm = context.getSystemService(STORAGE_SERVICE) as StorageManager
                        newPermission("Android%2Fdata%2Fjp%2Eco%2Efanzagames%2Elastorigin%5Fr", 994, sm)
                    }
                }
            }
                .create()
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }
    // Granted 상황에 맞춰 사용 가능 갱신
    private fun updateSwitches() {
        if (mShizukuShell.isReady()) {
            val onestoreSwitch = findViewById<Switch>(R.id.switch_filter_onestore)
            val playstoreSwitch = findViewById<Switch>(R.id.switch_filter_playstore)
            val playstoreJPSwitch = findViewById<Switch>(R.id.switch_filter_playstore_jp)
            val fanzaSwitch = findViewById<Switch>(R.id.switch_filter_fanza)
            if (mShizukuShell.checkDirExist("/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_C")) {
                switches[onestoreSwitch] = "/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_C"
                onestoreSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                onestoreSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            if (mShizukuShell.checkDirExist("/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_G")) {
                switches[playstoreSwitch] = "/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_G"
                playstoreSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                playstoreSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            if (mShizukuShell.checkDirExist("/storage/emulated/0/Android/data/com.com.pig.laojp.aos")) {
                switches[playstoreJPSwitch] = "/storage/emulated/0/Android/data/com.pig.laojp.aos"
                playstoreJPSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                playstoreJPSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            if (mShizukuShell.checkDirExist("/storage/emulated/0/Android/data/jp.co.fanzagames.lastorigin_r")) {
                switches[fanzaSwitch] = "/storage/emulated/0/Android/data/jp.co.fanzagames.lastorigin_r"
                fanzaSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                fanzaSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for ((switch, doc) in switches2) {
                val usable = doc != null

                switch.apply {
                    isChecked = usable
                    isEnabled = usable
                }
            }
        }
        else {
            for ((switch, path) in switches) {
                val usable = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> this.dataDoc?.let {
                        findMatchedDoc(
                            path,
                            it
                        )
                    } != null
                    else -> File("/storage/emulated/0/$path/files/UnityCache/Shared/").exists()
                }

                switch.apply {
                    isChecked = usable
                    isEnabled = usable
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
        if (requestCode == 44 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc != null) {
                this.dataDoc = doc
                prefs.edit().putString(PREF_DATA_URI, doc.uri.toString()).apply()
            }

            this.updateSwitches()
        }
        else if (requestCode == 991 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc == null) log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { log(it) }
                if (doc.name == "com.smartjoy.LastOrigin_C") {
                    this.oneStoreDoc = doc
                    prefs.edit().putString(PREF_ONESTORE_URI, doc.uri.toString()).apply()
                    val switch = findViewById<Switch>(R.id.switch_filter_onestore)
                    switches2[switch] = this.oneStoreDoc
                    switch.apply {
                        isChecked = true
                        isEnabled = true
                    }
                }
                else {
                    log(this.getString(R.string.WRONG_FOLDER_13))
                    this.oneStoreDoc = null
                }
            }
        }
        else if (requestCode == 992 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc == null) log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { log(it) }
                if (doc.name == "com.smartjoy.LastOrigin_G") {
                    this.playStoreDoc = doc
                    prefs.edit().putString(PREF_PLAYSTORE_URI, doc.uri.toString()).apply()
                    val switch = findViewById<Switch>(R.id.switch_filter_playstore)
                    switches2[switch] = this.playStoreDoc
                    switch.apply {
                        isChecked = true
                        isEnabled = true
                    }
                }
                else {
                    log(this.getString(R.string.WRONG_FOLDER_13))
                    this.playStoreDoc = null
                }
            }
        }
        else if (requestCode == 993 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc == null) log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { log(it) }
                if (doc.name == "com.pig.laojp.aos") {
                    this.playStoreJpDoc = doc
                    prefs.edit().putString(PREF_PLAYSTORE_JP_URI, doc.uri.toString()).apply()
                    val switch = findViewById<Switch>(R.id.switch_filter_playstore_jp)
                    switches2[switch] = this.playStoreJpDoc
                    switch.apply {
                        isChecked = true
                        isEnabled = true
                    }
                }
                else {
                    log(this.getString(R.string.WRONG_FOLDER_13))
                    this.playStoreJpDoc = null
                }
            }
        }
        else if (requestCode == 994 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc == null) log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { log(it) }
                if (doc.name == "jp.co.fanzagames.lastorigin_r") {
                    this.fanzaDoc = doc
                    prefs.edit().putString(PREF_FANZA_URI, doc.uri.toString()).apply()
                    val switch = findViewById<Switch>(R.id.switch_filter_fanza)
                    switches2[switch] = this.fanzaDoc
                    switch.apply {
                        isChecked = true
                        isEnabled = true
                    }
                }
                else {
                    log(this.getString(R.string.WRONG_FOLDER_13))
                    this.fanzaDoc = null
                }
            }
        }
        else if (requestCode == 9999 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val uri: Uri? = data.data
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    val file = uri.path?.let { File(it) } ?: return
                    //                    uri.path?.let { Log(it) }

                    val doc = DocumentFile.fromTreeUri(this, uri)
                    if (doc != null) {
                        this.modDoc = doc
                        prefs.edit().putString(PREF_MOD_URI, doc.uri.toString()).apply()
                    }

                    val split = file.path.split(":").toTypedArray()

                    this.modFolder = split[1] + "/"
                    prefs.edit().putString(PREF_MOD_FOLDER, this.modFolder).apply()
                    findViewById<TextView>(R.id.mod_text).text = String.format(this.getString(R.string.CURRENT_MOD_FOLDER), this.modFolder)
//                    val f = File(this.modFolder)
                }
            }
        }

    }

    @SuppressLint("SetTextI18n")
    fun log(text: String) {
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
            runPatcherShizuku(this, this.modFolder, this.switches, 1, { s -> this.log(s) }, mShizukuShell)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runPatcherSAF13(this, this.modDoc, this.switches2, 1) { s -> this.log(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runPatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 1) { s -> this.log(s) }
        else
            runPatcher(this, this.storages, this.modFolder, this.switches, 1) { s -> this.log(s) }

        CoroutineScope(Main).launch { patchBtn.isEnabled = true }
    }

    private fun doClear() {
        val clearBtn = findViewById<Button>(R.id.button_clear)
        CoroutineScope(Main).launch { clearBtn.isEnabled = false }
        if (mShizukuShell.isReady()) {
            runPatcherShizuku(this, this.modFolder, this.switches, 2, { s -> this.log(s) }, mShizukuShell)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runPatcherSAF13(this, this.modDoc, this.switches2, 2) { s -> this.log(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runPatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 2) { s -> this.log(s) }
        else
            runPatcher(this, this.storages, this.modFolder, this.switches, 2) { s -> this.log(s) }

        CoroutineScope(Main).launch { clearBtn.isEnabled = true }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !mShizukuShell.isSupported()) {
            findViewById<Button>(R.id.button_shizuku).visibility = View.GONE
            findViewById<TextView>(R.id.tip_text_shizuku).visibility = View.GONE
        }
        else {
            findViewById<Button>(R.id.button_grant).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_onestore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_playstore_jp).visibility = View.GONE
            findViewById<Button>(R.id.button_grant_fanza).visibility = View.GONE
            findViewById<TextView>(R.id.tip_text).visibility = View.GONE
            val shizukuLabel = findViewById<TextView>(R.id.tip_text_shizuku)
            if (mShizukuShell.isReady()) {
                shizukuLabel.setText(R.string.tip_saf_selected)
                updateSwitches()
            } else shizukuLabel.visibility = View.GONE
        }
    }

    companion object {
        private const val SHIZUKU_CODE = 10023
        private const val PREF_MOD_FOLDER = "pref_mod_folder"
        private const val PREF_MOD_URI = "pref_mod_uri"
        private const val PREF_DATA_URI = "pref_data_uri"
        private const val PREF_ONESTORE_URI = "pref_onestore_uri"
        private const val PREF_PLAYSTORE_URI = "pref_playstore_uri"
        private const val PREF_PLAYSTORE_JP_URI = "pref_playstore_jp_uri"
        private const val PREF_FANZA_URI = "pref_fanza_uri"
    }
}