package com.projectassyifa.webviewtest



import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val multiple_files = true // allowing multiple file upload

    /*-- MAIN VARIABLES --*/
    var webView: WebView? = null
    private var cam_file_data: String? = null // for storing camera file information
    private var file_data // data/header received after file selection
            : ValueCallback<Uri?>? = null
    private var file_path // received file(s) temp. location
            : ValueCallback<Array<Uri>>? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        println("reqCode $requestCode")
        println(resultCode)
        println(intent)
        if (Build.VERSION.SDK_INT >= 21) {
            println("enter version 21")
//            var results: Array<Uri?>? = null
            var results: Array<Uri>? = null


            /*-- if file request cancelled; exited camera. we need to send null value to make future attempts workable --*/if (resultCode == RESULT_CANCELED) {
                if (requestCode == file_req_code) {
                    file_path!!.onReceiveValue(null)
                    return
                }
            }

            /*-- continue if response is positive --*/if (resultCode == RESULT_OK) {
                if (requestCode == file_req_code) {
                    if (null == file_path) {
                        return
                    }
                    var clipData: ClipData?
                    var stringData: String?
                    try {
                        clipData = intent!!.clipData
                        stringData = intent.dataString
                    } catch (e: Exception) {
                        clipData = null
                        stringData = null
                    }
                    if (clipData == null && stringData == null && cam_file_data != null) {
                        results = arrayOf(Uri.parse(cam_file_data))
                    } else {

                        //masih eror
                        if (clipData != null) { // checking if multiple files selected or not
                            val numSelectedFiles = clipData.itemCount
//                            results = arrayOfNulls(numSelectedFiles)
                            results = arrayOfNulls<Uri>(numSelectedFiles) as Array<Uri>
                            for (i in 0 until clipData.itemCount) {
                                results[i] = clipData.getItemAt(i).uri
                            }
                        } else {
                            results = arrayOf(Uri.parse(stringData))
                        }
                    }
                }
            }
            println("RESULT $results")
            file_path!!.onReceiveValue(results)
            file_path = null
        } else {
            if (requestCode == file_req_code) {
                if (null == file_data) return
                val result = if (intent == null || resultCode != RESULT_OK) null else intent.data
                file_data!!.onReceiveValue(result)
                file_data = null
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById<View>(R.id.wb_webView) as WebView
        assert(webView != null)
        val webSettings = webView!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.mixedContentMode = 0
            webView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else if (Build.VERSION.SDK_INT >= 19) {
            webView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        webView!!.webViewClient = Callback()
        webView!!.loadUrl(webview_url)
        webView!!.webChromeClient = object : WebChromeClient() {
            /*--
                openFileChooser is not a public Android API and has never been part of the SDK.
                handling input[type="file"] requests for android API 16+; I've removed support below API 21 as it was failing to work along with latest APIs.
                --*/
            /*    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                    file_data = uploadMsg;
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(file_type);
                    if (multiple_files) {
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(Intent.createChooser(i, "File Chooser"), file_req_code);
                }
            */
            /*-- handling input[type="file"] requests for android API 21+ --*/
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                return if (file_permission() && Build.VERSION.SDK_INT >= 21) {
                    file_path = filePathCallback
                    var takePictureIntent: Intent? = null
                    var takeVideoIntent: Intent? = null
                    var includeVideo = false
                    var includePhoto = false

                    /*-- checking the accept parameter to determine which intent(s) to include --*/paramCheck@ for (acceptTypes in fileChooserParams.acceptTypes) {
                        val splitTypes = acceptTypes.split(", ?+".toRegex()).toTypedArray() // although it's an array, it still seems to be the whole value; split it out into chunks so that we can detect multiple values
                        for (acceptType in splitTypes) {
                            when (acceptType) {
                                "*/*" -> {
                                    includePhoto = true
                                    includeVideo = true
                                    break@paramCheck
                                }
                                "image/*" -> includePhoto = true
                                "video/*" -> includeVideo = true
                            }
                        }
                    }
                    if (fileChooserParams.acceptTypes.size == 0) {   //no `accept` parameter was specified, allow both photo and video
                        includePhoto = true
                        includeVideo = true
                    }
                    if (includePhoto) {
                        takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if (takePictureIntent.resolveActivity(this@MainActivity.packageManager) != null) {
                            var photoFile: File? = null
                            try {
                                photoFile = create_image()
                                takePictureIntent.putExtra("PhotoPath", cam_file_data)
                            } catch (ex: IOException) {
                                Log.e(TAG, "Image file creation failed", ex)
                            }
                            if (photoFile != null) {
                                cam_file_data = "file:" + photoFile.absolutePath
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                            } else {
                                cam_file_data = null
                                takePictureIntent = null
                            }
                        }
                    }
                    if (includeVideo) {
                        takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        if (takeVideoIntent.resolveActivity(this@MainActivity.packageManager) != null) {
                            var videoFile: File? = null
                            try {
                                videoFile = create_video()
                            } catch (ex: IOException) {
                                Log.e(TAG, "Video file creation failed", ex)
                            }
                            if (videoFile != null) {
                                cam_file_data = "file:" + videoFile.absolutePath
                                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(videoFile))
                            } else {
                                cam_file_data = null
                                takeVideoIntent = null
                            }
                        }
                    }
                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                    contentSelectionIntent.type = file_type
                    if (multiple_files) {
                        contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    val intentArray: Array<Intent?>
                    intentArray = if (takePictureIntent != null && takeVideoIntent != null) {
                        arrayOf(takePictureIntent, takeVideoIntent)
                    } else takePictureIntent?.let { arrayOf(it) }
                            ?: (takeVideoIntent?.let { arrayOf(it) } ?: arrayOfNulls(0))
                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "File chooser")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    startActivityForResult(chooserIntent, file_req_code)
                    true
                } else {
                    false
                }
            }
        }
    }

    /*-- callback reporting if error occurs --*/
    inner class Callback : WebViewClient() {
        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            Toast.makeText(applicationContext, "Failed loading app!", Toast.LENGTH_SHORT).show()
        }
    }

    /*-- checking and asking for required file permissions --*/
    fun file_permission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA), 1)
            false
        } else {
            true
        }
    }

    /*-- creating new image file here --*/
    @Throws(IOException::class)
    private fun create_image(): File {
        @SuppressLint("SimpleDateFormat") val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    /*-- creating new video file here --*/
    @Throws(IOException::class)
    private fun create_video(): File {
        @SuppressLint("SimpleDateFormat") val file_name = SimpleDateFormat("yyyy_mm_ss").format(Date())
        val new_name = "file_" + file_name + "_"
        val sd_directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(new_name, ".3gp", sd_directory)

    }

    /*-- back/down key handling --*/
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView!!.canGoBack()) {
                    webView!!.goBack()
                } else {
                    finish()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        /*-- CUSTOMIZE --*/ /*-- you can customize these options for your convenience --*/
        private const val webview_url = "http://202.62.9.138/dashboard_android_fresh" // web address or local file location you want to open in webview
        private const val file_type = "image/*" // file types to be allowed for upload
        private val TAG = MainActivity::class.java.simpleName
        private const val file_req_code = 1
    }
}

