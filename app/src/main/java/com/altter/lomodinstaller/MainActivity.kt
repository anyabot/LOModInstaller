package com.altter.lomodinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import rikka.shizuku.ShizukuProvider
import java.io.File


class MainActivity : AppCompatActivity() {
    private var mUserService: IUserService? = null
    private val switches: HashMap<Switch, String> = HashMap()
    private val switches2: HashMap<Switch, DocumentFile?> = HashMap()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("GRANTED_URIS", Context.MODE_PRIVATE)
    }

    private val SHIZUKU_CODE = 10023

    private val PREF_MOD_FOLDER = "pref_mod_folder"
    private val PREF_MOD_URI = "pref_mod_uri"
    private val PREF_DATA_URI = "pref_data_uri"
    private var modFolder: String = ""
    private var modDoc: DocumentFile? = null
    private var dataDoc: DocumentFile? = null

    private val PREF_ONESTORE_URI = "pref_onestore_uri"
    private val PREF_PLAYSTORE_URI = "pref_playstore_uri"
    private val PREF_PLAYSTORE_JP_URI = "pref_playstore_jp_uri"
    private val PREF_FANZA_URI = "pref_fanza_uri"
    private var oneStoreDoc: DocumentFile? = null
    private var playStoreDoc: DocumentFile? = null
    private var playStoreJpDoc: DocumentFile? = null
    private var fanzaDoc: DocumentFile? = null

    private var shizukuPermission = false
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            if (iBinder == null || !iBinder.pingBinder()) {
                return
            }
            mUserService = IUserService.Stub.asInterface(iBinder)
            RecheckShizuku()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    private val storages: List<String> =
        File("/storage").listFiles()?.filter { it.name != "self" }?.map { it.name } ?: listOf()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ensureUserService()
        val tempmodFolder = prefs.getString(PREF_MOD_FOLDER, null)
        if (tempmodFolder != null) this.modFolder = tempmodFolder
        val tempmodURI = prefs.getString(PREF_MOD_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) try {
            this.modDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempmodURI))
        }
        catch (e: Exception) {
            this.modDoc = null
            this.modFolder = ""
        }
        val tempdataURI = prefs.getString(PREF_DATA_URI, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) try {
            this.dataDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempdataURI))
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

        this.checkPermission(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            switches2[findViewById<Switch>(R.id.switch_filter_onestore)] = this.oneStoreDoc
            switches2[findViewById<Switch>(R.id.switch_filter_playstore)] = this.playStoreDoc
            switches2[findViewById<Switch>(R.id.switch_filter_playstore_jp)] = this.playStoreJpDoc
            switches2[findViewById<Switch>(R.id.switch_filter_fanza)] = this.fanzaDoc
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switches[findViewById<Switch>(R.id.switch_filter_onestore)] = "com.smartjoy.LastOrigin_C"
            switches[findViewById<Switch>(R.id.switch_filter_playstore)] = "com.smartjoy.LastOrigin_G"
            switches[findViewById<Switch>(R.id.switch_filter_playstore_jp)] = "com.pig.laojp.aos"
            switches[findViewById<Switch>(R.id.switch_filter_fanza)] = "jp.co.fanzagames.lastorigin_r"
        }
        else {
            switches[findViewById<Switch>(R.id.switch_filter_onestore)] = "Android/data/com.smartjoy.LastOrigin_C"
            switches[findViewById<Switch>(R.id.switch_filter_playstore)] = "Android/data/com.smartjoy.LastOrigin_G"
            switches[findViewById<Switch>(R.id.switch_filter_playstore_jp)] = "Android/data/com.pig.laojp.aos"
            switches[findViewById<Switch>(R.id.switch_filter_fanza)] = "Android/data/jp.co.fanzagames.lastorigin_r"
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

        RecheckShizuku()

        this.updateSwitches()
        if (this.modFolder == "") findViewById<TextView>(R.id.mod_text).text = this.getString(R.string.EMPTY_MOD_FOLDER)
        else findViewById<TextView>(R.id.mod_text).text = String.format(this.getString(R.string.CURRENT_MOD_FOLDER), this.modFolder)
        findViewById<Button>(R.id.button_patch).setOnClickListener { this.BeforePatch() }
        findViewById<Button>(R.id.button_folder).setOnClickListener { this.SelectFolder() }
        findViewById<Button>(R.id.button_clear).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.CLEAR_TITLE)
                setMessage(R.string.CLEAR_MESSAGE)
                setPositiveButton(R.string.CLEAR_OK) { _, _ ->
                    BeforeClear()
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

    // Granted 상황에 맞춰 사용 가능 갱신
    private fun updateSwitches() {
        if (ShizukuAvailable()) {
            var tempSwitch = findViewById<Switch>(R.id.switch_filter_onestore)
            if (!RunShizukuCommand("ls /storage/emulated/0/Android/data/com.smartjoy.LastOrigin_C").contains("No such file or directory")) {
                switches[tempSwitch] = "/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_C"
                tempSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                tempSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            tempSwitch = findViewById<Switch>(R.id.switch_filter_playstore)
            if (!RunShizukuCommand("ls /storage/emulated/0/Android/data/com.smartjoy.LastOrigin_G").contains("No such file or directory")) {
                switches[tempSwitch] = "/storage/emulated/0/Android/data/com.smartjoy.LastOrigin_G"
                tempSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                tempSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            tempSwitch = findViewById<Switch>(R.id.switch_filter_playstore_jp)
            if (!RunShizukuCommand("ls /storage/emulated/0/Android/data/com.com.pig.laojp.aos").contains("No such file or directory")) {
                switches[tempSwitch] = "/storage/emulated/0/Android/data/com.pig.laojp.aos"
                tempSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                tempSwitch.apply {
                    isChecked = false
                    isEnabled = false
                }
            }
            tempSwitch = findViewById<Switch>(R.id.switch_filter_fanza)
            if (!RunShizukuCommand("ls /storage/emulated/0/Android/data/jp.co.fanzagames.lastorigin_r").contains("No such file or directory")) {
                switches[tempSwitch] = "/storage/emulated/0/Android/data/jp.co.fanzagames.lastorigin_r"
                tempSwitch.apply {
                    isChecked = true
                    isEnabled = true
                }
            }
            else {
                tempSwitch.apply {
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
            if (doc == null) Log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { Log(it) }
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
                    Log(this.getString(R.string.WRONG_FOLDER_13))
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
            if (doc == null) Log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { Log(it) }
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
                    Log(this.getString(R.string.WRONG_FOLDER_13))
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
            if (doc == null) Log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { Log(it) }
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
                    Log(this.getString(R.string.WRONG_FOLDER_13))
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
            if (doc == null) Log(this.getString(R.string.FAIL_FOLDER_13))
            if (doc != null) {
                doc.name?.let { Log(it) }
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
                    Log(this.getString(R.string.WRONG_FOLDER_13))
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
    fun Log(text: String) {
        CoroutineScope(Main).launch {
            val el = findViewById<TextView>(R.id.textLog)
            el.text = text + "\n" + el.text.toString()
        }
    }

    private fun BeforePatch() {
        // 혹시 전처리가 필요하면 여기 추가하면 됨
        CoroutineScope(Default).launch { this@MainActivity.DoPatch() }
    }

    private fun BeforeClear() {
        // 혹시 전처리가 필요하면 여기 추가하면 됨
        CoroutineScope(Default).launch { this@MainActivity.DoClear() }
    }

    private fun SelectFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, 9999)
    }

    private fun DoPatch() {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        CoroutineScope(Main).launch { patchBtn.isEnabled = false }
        if (ShizukuAvailable()) {
            PatcherShizuku(this, this.modFolder, this.switches, 1, { s -> this.Log(s) }, { s -> this.RunShizukuCommand(s) })
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PatcherSAF13(this, this.modDoc, this.switches2, 1) { s -> this.Log(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            PatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 1) { s -> this.Log(s) }
        else
            Patcher(this, this.storages, this.modFolder, this.switches, 1) { s -> this.Log(s) }

        CoroutineScope(Main).launch { patchBtn.isEnabled = true }
    }

    private fun DoClear() {
        val clearBtn = findViewById<Button>(R.id.button_clear)
        CoroutineScope(Main).launch { clearBtn.isEnabled = false }
        if (ShizukuAvailable()) {
            PatcherShizuku(this, this.modFolder, this.switches, 2, { s -> this.Log(s) }, { s -> this.RunShizukuCommand(s) })
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PatcherSAF13(this, this.modDoc, this.switches2, 2) { s -> this.Log(s) }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            PatcherSAF(this, this.dataDoc, this.modDoc, this.switches, 2) { s -> this.Log(s) }
        else
            Patcher(this, this.storages, this.modFolder, this.switches, 2) { s -> this.Log(s) }

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
            RecheckShizuku()
        }
    }


    private fun checkPermission(need_request: Boolean) {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            permissions.any { p ->
                ContextCompat.checkSelfPermission(
                    this,
                    p
                ) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            patchBtn.isEnabled = false
            if (need_request)
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

    fun ensureUserService(): Boolean {
        if (mUserService != null) {
            return true
        }
        val mUserServiceArgs = UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                UserService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        Shizuku.bindUserService(mUserServiceArgs, mServiceConnection)
        return false
    }

    private fun RecheckShizuku() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !Shizuku.pingBinder()) {
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
            shizukuPermission = if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
            if (shizukuPermission) {
                shizukuLabel.setText(R.string.tip_saf_selected)
                updateSwitches()
            };
            else shizukuLabel.visibility = View.GONE;
        }
    }

    public fun ShizukuAvailable(): Boolean {
        return shizukuPermission;
    }

    public fun RunShizukuCommand(cmd: String): String {
        ensureUserService()
        val res = mUserService?.runShellCommand(cmd)
        return res.toString()

    }
}