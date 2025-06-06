/*
 * --------------------------------------------------------------------------------
 * <copyright company="Aspose">
 *   Copyright (c) 2025 Aspose.BarCode for Cloud
 * </copyright>
 * <summary>
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 * </summary>
 * --------------------------------------------------------------------------------
 */

package com.aspose.barcode.cloud.demo_app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aspose.barcode.cloud.ApiClient
import com.aspose.barcode.cloud.ApiException
import com.aspose.barcode.cloud.api.GenerateApi
import com.aspose.barcode.cloud.api.ScanApi
import com.aspose.barcode.cloud.model.BarcodeImageFormat
import com.aspose.barcode.cloud.model.BarcodeResponseList
import com.aspose.barcode.cloud.model.EncodeBarcodeType
import com.aspose.barcode.cloud.model.EncodeDataType
import com.aspose.barcode.cloud.requests.GenerateRequestWrapper
import com.aspose.barcode.cloud.requests.ScanMultipartRequestWrapper
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor


class MainActivity : AppCompatActivity() {
    companion object {
        const val PERMISSION_REQUEST_CALLBACK_CODE = 1
        const val ACTION_GET_CONTENT_CALLBACK_CODE = 2
        const val ACTION_IMAGE_CAPTURE_CALLBACK_CODE = 3

        private fun imageSize(width: Int, height: Int, maxSize: Int = 384): Size {
            val ratio = width.toFloat() / height
            if (ratio > 1) {
                // width > height
                // use width
                if (width < maxSize) {
                    // do not resize
                    return Size(width, height)
                }

                val newHeight = floor(maxSize / ratio).toInt()
                return Size(maxSize, newHeight)
            }
            // width <= height
            // use height
            if (height < maxSize) {
                // do not resize
                return Size(width, height)
            }
            val newWidth = floor(maxSize * ratio).toInt()
            return Size(newWidth, maxSize)
        }

        private fun reduceBitmapSize(image: Bitmap): Bitmap {
            val newSize = imageSize(image.width, image.height)
            return Bitmap.createScaledBitmap(image, newSize.width, newSize.height, true)
        }
    }

    private lateinit var barcodeTypeSpinner: Spinner
    private lateinit var barcodeTextEdit: EditText
    private lateinit var barcodeImgView: ImageView

    private lateinit var scanApi: ScanApi
    private lateinit var generateApi: GenerateApi
    private val encodeTypes = EncodeBarcodeType.values().map { it.toString() }.sorted()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val client = ApiClient(
            "Client Id from https://dashboard.aspose.cloud/applications",
            "Client Secret from https://dashboard.aspose.cloud/applications"
        )

        client.readTimeout = 60_000

        generateApi = GenerateApi(client)
        scanApi = ScanApi(client)

        barcodeTypeSpinner = findViewById(R.id.typeSpinner)
        populateBarcodeTypesSpinner()

        barcodeTextEdit = findViewById(R.id.editText)
        barcodeImgView = findViewById(R.id.imageView)
    }

    private fun showErrorMessage(error: String) {
        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_LONG).show()
    }

    private fun populateBarcodeTypesSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, encodeTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        barcodeTypeSpinner.adapter = adapter
        barcodeTypeSpinner.setSelection(encodeTypes.indexOf("QR"))
    }

    private fun requestPermissionAndPickFile(context: Activity) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pickFile()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CALLBACK_CODE
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CALLBACK_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                    pickFile()
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.
                    showErrorMessage("Permission to read image denied")
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ACTION_GET_CONTENT_CALLBACK_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val bytes = contentResolver.openInputStream(data?.data!!)!!.readBytes()
                    val bmpImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    recognizeBarcode(bmpImage)
                }
            }

            ACTION_IMAGE_CAPTURE_CALLBACK_CODE -> {
                if (resultCode == RESULT_OK) {
                    val bmpImage = data?.extras?.get("data") as Bitmap
                    recognizeBarcode(bmpImage)
                }
            }

            else -> {
                showErrorMessage("No file selected")
            }
        }
    }


    private fun recognizeBarcode(image: Bitmap) {
        try {
            val smallerBmp = reduceBitmapSize(image)

            barcodeImgView.setImageBitmap(smallerBmp)
            startRecognizeAnimation()

            val tmpFile = File.createTempFile("barcode", null)

            FileOutputStream(tmpFile).use { output ->
                smallerBmp.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            val apiRequest = ScanMultipartRequestWrapper(tmpFile);

            Thread {
                try {
                    val recognized = scanApi.scanMultipart(apiRequest)

                    runOnUiThread {
                        stopRecognizeAnimation()
                        if (recognized.barcodes.isEmpty()) {
                            barcodeTextEdit.setText("")
                            showErrorMessage("No barcode detected")
                            return@runOnUiThread
                        }
                        val firstBarcode = recognized.barcodes.first()
                        barcodeTextEdit.setText(firstBarcode.barcodeValue)
                        val index = encodeTypes.indexOf(firstBarcode.type)
                        barcodeTypeSpinner.setSelection(index, true)
                    }
                } catch (e: ApiException) {
                    runOnUiThread {
                        stopRecognizeAnimation()

                        var message = e.message + ": " + e.details
                        if (e.httpCode == 0) {
                            message = "Check ClientId and ClientSecret in ApiClient $message"
                        }
                        showErrorMessage(message)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        stopRecognizeAnimation()
                        showErrorMessage("Exception: " + e.message)
                    }
                }
            }.start()

        } catch (e: java.lang.Exception) {
            showErrorMessage(e.message!!)
        }
    }

    private fun startRecognizeAnimation() {
        val rotation = AnimationUtils.loadAnimation(this, R.anim.rotate)
        rotation.fillAfter = true
        barcodeImgView.startAnimation(rotation)
    }

    private fun stopRecognizeAnimation() {
        barcodeImgView.clearAnimation()
    }

    fun onBtnGenerateClick(@Suppress("UNUSED_PARAMETER") view: View) {

        val type: EncodeBarcodeType = EncodeBarcodeType.fromValue(barcodeTypeSpinner.selectedItem.toString())

       val genRequest = GenerateRequestWrapper(
                        type, barcodeTextEdit.text.toString());

        genRequest.imageFormat = BarcodeImageFormat.PNG;
        genRequest.imageHeight = barcodeImgView.measuredHeight.toFloat()
        genRequest.imageWidth = barcodeImgView.measuredWidth.toFloat()


        Thread {
            try {
                val generated: File? = generateApi.generate(genRequest);
                runOnUiThread {
                    val bitmap = BitmapFactory.decodeFile(generated!!.absolutePath)
                    barcodeImgView.setImageBitmap(bitmap)
                }

            } catch (e: ApiException) {
                runOnUiThread {
                    var message = e.message + ": " + e.details
                    if (e.httpCode == 0) {
                        message = "Check ClientId and ClientSecret in ApiClient $message"
                    }
                    showErrorMessage(message)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showErrorMessage("Exception: " + e.message)
                }
            }
        }.start()
    }

    fun onBtnTakePhotoClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, ACTION_IMAGE_CAPTURE_CALLBACK_CODE)
        }
    }

    fun onBtnSelectImageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        requestPermissionAndPickFile(this)
    }

    private fun pickFile() {
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT)
        getContentIntent.type = "image/*"
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(
                Intent.createChooser(getContentIntent, "Select an Image to Recognize"),
                ACTION_GET_CONTENT_CALLBACK_CODE
            )
        } catch (ex: java.lang.Exception) {
            showErrorMessage("Unable to start file selector")
        }

    }
}
