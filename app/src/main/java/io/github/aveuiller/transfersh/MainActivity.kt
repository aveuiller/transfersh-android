package io.github.aveuiller.transfersh

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View

/**
 * Transfer.sh main activity. Will handle retrieval and upload
 * of the file.
 *
 * The methods starting with 'do' are bound to the UI components
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    /**
     * Action when upload button clicked.
     *
     * @param view: The clicked view.
     */
    fun doUploadMedia(view: View) {
        // TODO
    }

    /**
     * Action when local media selection button clicked.
     *
     * @param view: The clicked view.
     */
    fun doSelectMedia(view: View) {
        val intent = Intent()
            .setType("*/*")
            .setAction(Intent.ACTION_GET_CONTENT)

        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_file)), 111)

    }

    /**
     * Action when media capture button clicked.
     *
     * @param view: The clicked view.
     */
    fun doCaptureMedia(view: View) {
        // TODO
    }

    /**
     * Action when link copy button clicked.
     *
     * @param view: The clicked view.
     */   fun doCopyLink(view: View) {
        // TODO
    }

    /**
     * Store the set upload URL to local settings.
     */
    private fun doStoreUrl() {
        // TODO
    }
}
