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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import java.io.File


class MainActivity : AppCompatActivity() {
    private val switches: HashMap<Switch, String> = HashMap()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("GRANTED_URIS", Context.MODE_PRIVATE)
    }

    private val PREF_MOD_FOLDER = "pref_mod_folder"
    private val PREF_MOD_URI = "pref_mod_uri"
    private val PREF_DATA_URI = "pref_data_uri"
    private var modFolder: String = ""
    private var modDoc: DocumentFile? = null
    private var dataDoc: DocumentFile? = null

    private val storages: List<String> =
        File("/storage").listFiles()?.filter { it.name != "self" }?.map { it.name } ?: listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tempmodFolder = prefs.getString(PREF_MOD_FOLDER, null)
        if (tempmodFolder != null) this.modFolder = tempmodFolder
        val tempmodURI = prefs.getString(PREF_MOD_URI, null)
        if (tempmodFolder != null) try {
            this.modDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempmodURI))
            "".also { this.modFolder = it }
        }
        catch (e: Exception) {
            this.modDoc = null
        }
        val tempdataURI = prefs.getString(PREF_DATA_URI, null)
        if (tempmodFolder != null) try {
            this.dataDoc = DocumentFile.fromTreeUri(this, Uri.parse(tempdataURI))
        }
        catch (e: Exception) {
            this.dataDoc = null
        }

        this.checkPermission(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switches.put(
                findViewById<Switch>(R.id.switch_filter_onestore),
                "com.smartjoy.LastOrigin_C"
            )
            switches.put(
                findViewById<Switch>(R.id.switch_filter_playstore),
                "com.smartjoy.LastOrigin_G"
            )
            switches.put(
                findViewById<Switch>(R.id.switch_filter_playstore_jp),
                "com.pig.laojp.aos"
            )
        }
        else {
            switches.put(
                findViewById<Switch>(R.id.switch_filter_onestore),
                "Android/data/com.smartjoy.LastOrigin_C"
            )
            switches.put(
                findViewById<Switch>(R.id.switch_filter_playstore),
                "Android/data/com.smartjoy.LastOrigin_G"
            )
            switches.put(
                findViewById<Switch>(R.id.switch_filter_playstore_jp),
                "Android/data/com.pig.laojp.aos"
            )
        }

        val label = findViewById<TextView>(R.id.tip_text)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//            label.setText(R.string.tip_auto_selected)

            findViewById<Button>(R.id.button_grant).visibility = View.GONE
        } else
            label.setText(R.string.tip_saf_selected)

        this.updateSwitches()
        if (this.modFolder == "") findViewById<TextView>(R.id.mod_text).text = "Please Select Mod Folder"
        else findViewById<TextView>(R.id.mod_text).text = "Current Mod Folder: " + this.modFolder
        findViewById<Button>(R.id.button_patch).setOnClickListener { this.BeforePatch() }
        findViewById<Button>(R.id.button_folder).setOnClickListener { this.SelectFolder() }
        findViewById<Button>(R.id.button_grant).setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.GRANT_PERMISSION_TITLE)
                setMessage(R.string.GRANT_PERMISSION_MESSAGE)
                setPositiveButton(R.string.GRANT_PERMISSION_OK) { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log("Trying")

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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
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
                    }
                    startActivityForResult(intent, 44)
                }
            }
                .create()
                .show()
        }
    }

    // Granted 상황에 맞춰 사용 가능 갱신
    private fun updateSwitches() {
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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 44 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            uri.path?.let { Log(it) }
            val doc = DocumentFile.fromTreeUri(this, uri)
            if (doc != null) {
                this.dataDoc = doc
                prefs.edit().putString(PREF_DATA_URI, doc.uri.toString()).apply()
            }

            this.updateSwitches()
        }
        else if (requestCode == 9999 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val uri: Uri? = data.data
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    val file = File(uri.path)
                    uri.path?.let { Log(it) }

                    val doc = DocumentFile.fromTreeUri(this, uri)
                    if (doc != null) {
                        this.modDoc = doc
                        prefs.edit().putString(PREF_MOD_URI, doc.uri.toString()).apply()
                    }

                    val split = file.path.split(":").toTypedArray()

                    this.modFolder = split[1] + "/"
                    prefs.edit().putString(PREF_MOD_FOLDER, this.modFolder).apply()
                    findViewById<TextView>(R.id.mod_text).text = "Current Mod Folder: " + this.modFolder
                    val f = File(this.modFolder)
                }
            }
        }

    }

    private val androidTreeUri = DocumentsContract.buildTreeDocumentUri(
        "com.android.externalstorage.documents", "primary:Android"
    )

    private fun checkIfGotAccess(): Boolean {
        return contentResolver.persistedUriPermissions.indexOfFirst { uriPermission ->
            uriPermission.uri.equals(androidTreeUri) && uriPermission.isReadPermission && uriPermission.isWritePermission
        } >= 0
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

    private fun SelectFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Android/data"))
        startActivityForResult(i, 9999)
    }

    private fun DoPatch() {
        val patchBtn = findViewById<Button>(R.id.button_patch)
        CoroutineScope(Main).launch { patchBtn.isEnabled = false }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            PatcherSAF(this, this.dataDoc, this.modDoc, this.switches) { s -> this.Log(s) }
        else
            Patcher(this, this.storages, this.modFolder, this.switches) { s -> this.Log(s) }

        CoroutineScope(Main).launch { patchBtn.isEnabled = true }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0)
            this.checkPermission(false)
    }

    private fun checkPermission(need_request: Boolean) {
        val cleanBtn = findViewById<Button>(R.id.button_patch)

        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            permissions.any { p ->
                ContextCompat.checkSelfPermission(
                    this,
                    p
                ) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            cleanBtn.isEnabled = false
            if (need_request)
                this.requestPermission(permissions);
        } else
            cleanBtn.isEnabled = true

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
}