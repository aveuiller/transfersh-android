package io.github.aveuiller.transfersh

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.support.annotation.IdRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException

/**
 * Transfer.sh main activity. Will handle retrieval and upload
 * of the file.
 *
 * The methods starting with 'do' are bound to the UI components
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val LOG_TAG: String = "Transfersh"
        private const val SHARE_PREFS_NAME: String = "transfersh_prefs"
        private const val PREF_UPLOAD_URL: String = "upload_url"

        private const val REQUEST_FILE_SELECTION: Int = 1
        private const val REQUEST_IMAGE_CAPTURE: Int = 2
        private const val PERMISSIONS_REQUEST_READ_STORAGE: Int = 3

        private const val DEFAULT_SERVER = "https://transfer.sh"

        private fun <ViewT : View> Activity.bindView(@IdRes idRes: Int): Lazy<ViewT> {
            return lazyUnsychronized {
                findViewById<ViewT>(idRes)
            }
        }

        private fun <T> lazyUnsychronized(initializer: () -> T): Lazy<T> =
            lazy(LazyThreadSafetyMode.NONE, initializer)
    }

    /**
     * Callback to an actual upload. We should receive the link holding the uploaded data.
     */
    private inner class UploadCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.w(LOG_TAG, "Unable to upload file!", e)
            runOnUiThread {
                Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }

        override fun onResponse(call: Call, response: Response) {
            uploadResultView.text = response.body?.string()
        }

    }

    private var contentToUpload: Uri? = null
    private var usesExternalStorage: Boolean = false
    private val urlEditableView: EditText by bindView(R.id.url_editable)
    private val uploadResultView: TextView by bindView(R.id.upload_result)
    private val imagePreview: ImageView by bindView(R.id.image_preview)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPreferences = getSharedPreferences(SHARE_PREFS_NAME, Context.MODE_PRIVATE)
        urlEditableView.setText(sharedPreferences.getString(PREF_UPLOAD_URL, DEFAULT_SERVER))

        val action: String? = intent?.action

        if (action == Intent.ACTION_SEND) {
            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                Log.i(LOG_TAG, "Opening file %s".format(it))
                this.setContentToUpload(it, true)
            }
        }
    }

    /**
     * Action when upload button clicked.
     *
     * @param view: The clicked view.
     */
    fun doUploadMedia(view: View) {
        // Check user's permission settings
        val selfPermission = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE)
        if (selfPermission != PackageManager.PERMISSION_GRANTED && this.usesExternalStorage) {
            Log.w(LOG_TAG, "We don't have permission to read storage, requesting")
            ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_READ_STORAGE)
        } else {
            uploadCurrentMedia()
        }
    }

    /**
     * Action when local media selection button clicked.
     *
     * @param view: The clicked view.
     */
    fun doSelectMedia(view: View) {
        Intent().setType("*/*").setAction(Intent.ACTION_PICK).addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            .also { selectMediaIntent ->
                selectMediaIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(
                        Intent.createChooser(selectMediaIntent, getString(R.string.select_file)),
                        REQUEST_FILE_SELECTION
                    )
                }
            }
    }

    /**
     * Action when media capture button clicked.
     *
     * @param view: The clicked view.
     */
    fun doCaptureMedia(view: View) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {

                // Create the File where the photo should go
                this.getImageFile().also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        applicationContext,
                        applicationContext.packageName + ".provider",
                        it
                    )
                    Log.i(LOG_TAG, "Using path: %s".format(photoURI.path))
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /**
     * Action when link copy button clicked.
     *
     * @param view: The clicked view.
     */
    fun doCopyLink(view: View) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText(getString(R.string.upload_result), uploadResultView.text)
        clipboard.primaryClip = data
        Toast.makeText(applicationContext, R.string.result_copied, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    Log.i(LOG_TAG, "Image captured")
                    val path: Uri? = Uri.fromFile(this.getImageFile())
                    this.setContentToUpload(path, false)
                }
                REQUEST_FILE_SELECTION -> {
                    Log.i(LOG_TAG, "File selected")
                    this.setContentToUpload(data?.data, true)
                }
                else -> Log.w(LOG_TAG, "Unable to find request %d".format(requestCode))
            }
        } else {
            Log.w(LOG_TAG, "Failure on activity result (req: %d, res: %d)".format(requestCode, resultCode))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_READ_STORAGE) {
            if (grantResults.lastOrNull() == PackageManager.PERMISSION_GRANTED) {
                uploadCurrentMedia()
            } else {
                Toast.makeText(applicationContext, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Actual upload of the set media file.
     */
    private fun uploadCurrentMedia() {
        storeUploadUrl()

        val service = TransferService(OkHttpClient(), urlEditableView.text.toString(), applicationContext)
        if (contentToUpload != null) {
            try {
                service.upload(contentToUpload!!, UploadCallback())
            } catch (ex: IllegalArgumentException) {
                Log.w("Unable to send data", ex)
                Toast.makeText(applicationContext, R.string.unable_to_send, Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.i(LOG_TAG, "No content to upload, skipping")
        }
    }

    /**
     * Store the set upload URL to local settings.
     */
    private fun storeUploadUrl() {
        val sharedPreferences = getSharedPreferences(SHARE_PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(PREF_UPLOAD_URL, this.urlEditableView.text.toString()).apply()
    }

    /**
     * Show a preview of the file to send.
     *
     * @param imageUri Uri to the file to upload.
     * @param usesExternalStorage true if the data is exported from another application or the sdcard,
     * False if the data is created by our own application
     */
    private fun setContentToUpload(imageUri: Uri?, usesExternalStorage: Boolean) {
        this.usesExternalStorage = usesExternalStorage
        this.contentToUpload = imageUri
        Log.i(LOG_TAG, "Setting image uri to %s".format(imageUri))

        // Reset the image
        imagePreview.setImageDrawable(null)
        if (imageUri != null) {
            try {
                imagePreview.setImageURI(imageUri)
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Unable to set image preview")
                // TODO: Add a 'no preview available' image
            }
        }
    }

    /**
     * Create a new File to the picture to upload.
     * @return The picture File instance.
     */
    private fun getImageFile(): File {
        return getFileStreamPath("last_picture.png")
    }

}
