package com.example.fotobased

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002
    private var currentPhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageView = findViewById<ImageView>(R.id.previewImage)
        imageView.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            openCamera()
        }
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val photoFileUri = createImageUri()
            currentPhotoUri = photoFileUri
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoFileUri)
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao criar arquivo de imagem", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageUri(): Uri {
        val imageFile = File.createTempFile("temp_photo", ".jpg", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(this, "${packageName}.provider", imageFile)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val imageView = findViewById<ImageView>(R.id.previewImage)
        val textStatus = findViewById<TextView>(R.id.imagePlaceholderText)
        val ipInput = findViewById<EditText>(R.id.serverIpInput)
        // 1. ADICIONADO: Pega a referência do novo campo da porta
        val portInput = findViewById<EditText>(R.id.serverPortInput)

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            currentPhotoUri?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    var originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap == null) {
                        Toast.makeText(this, "Não foi possível ler a imagem", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // ================== CORREÇÃO DE ORIENTAÇÃO ==================
                    val exifInputStream = contentResolver.openInputStream(uri)
                    val exifInterface = ExifInterface(exifInputStream!!)
                    val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                    val rotationInDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                    exifInputStream.close()

                    if (rotationInDegrees != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationInDegrees.toFloat())
                        originalBitmap = Bitmap.createBitmap(
                            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                        )
                    }
                    // ==================================================================

                    val scaledWidth = if (originalBitmap.width > 1280) 1280 else originalBitmap.width
                    val scaledHeight = (scaledWidth.toFloat() / originalBitmap.width * originalBitmap.height).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

                    imageView.setImageBitmap(scaledBitmap)

                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val photoBytes = stream.toByteArray()

                    // 2. MODIFICADO: Lê o valor da porta e o utiliza na função
                    val ipStr = ipInput.text.toString()
                    val portStr = portInput.text.toString()

                    if (ipStr.isNotEmpty() && portStr.isNotEmpty()) {
                        val portInt = portStr.toInt()
                        sendPhoto(ipStr, portInt, photoBytes, textStatus)
                    } else {
                        Toast.makeText(this, "Preencha o IP e a Porta", Toast.LENGTH_SHORT).show()
                        textStatus.text = "Falha: IP ou Porta não preenchidos."
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendPhoto(ip: String, port: Int, photo: ByteArray, statusView: TextView) {
        thread {
            // 1. ADICIONADO: Atualiza o status para "Enviando" ANTES de começar.
            runOnUiThread { statusView.text = "Enviando, aguarde..." }

            try {
                // 2. A operação de rede demorada acontece aqui...
                Socket(ip, port).use { socket ->
                    val out = socket.getOutputStream()
                    val sizeBytes = ByteBuffer.allocate(4).putInt(photo.size).array()
                    out.write(sizeBytes)
                    out.write(photo)
                    out.flush()
                }
                // 3. O status é atualizado para "sucesso" APÓS o fim da operação.
                runOnUiThread { statusView.text = "Foto enviada com sucesso!" }
            } catch (e: Exception) {
                e.printStackTrace()
                // 3. Ou o status é atualizado para "erro" se algo falhar.
                runOnUiThread { statusView.text = "Erro ao enviar: ${e.message}" }
            }
        }
    }
}