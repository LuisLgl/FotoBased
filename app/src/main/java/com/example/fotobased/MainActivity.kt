package com.example.fotobased

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    private var photoBytes: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pega referências dos elementos da tela
        val imageView = findViewById<ImageView>(R.id.previewImage)
        val textStatus = findViewById<TextView>(R.id.imagePlaceholderText)
        val ipInput = findViewById<EditText>(R.id.serverIpInput)

        // Clique na imagem para tirar foto
        imageView.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    // Verifica permissão
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openCamera()
            }
        }
    }

    // Abre a câmera usando Intent
    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val imageView = findViewById<ImageView>(R.id.previewImage)
        val textStatus = findViewById<TextView>(R.id.imagePlaceholderText)
        val ipInput = findViewById<EditText>(R.id.serverIpInput)

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val bitmap = data?.extras?.get("data") as Bitmap

            // Reduz largura para 1280 mantendo proporção
            val scaledWidth = if (bitmap.width > 1280) 1280 else bitmap.width
            val scaledHeight = (scaledWidth.toFloat() / bitmap.width * bitmap.height).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

            // Exibe pré-visualização
            imageView.setImageBitmap(scaledBitmap)

            // Converte para bytes JPEG (~80% qualidade)
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            photoBytes = stream.toByteArray()

            // Envia para servidor
            sendPhoto(ipInput.text.toString(), 5001, photoBytes!!, textStatus)
        }
    }

    // Envia foto via TCP socket
    private fun sendPhoto(ip: String, port: Int, photo: ByteArray, statusView: TextView) {
        Thread {
            try {
                val socket = Socket(ip, port)
                val out = socket.getOutputStream()

                // Envia tamanho da imagem (4 bytes big-endian)
                val sizeBytes = ByteBuffer.allocate(4).putInt(photo.size).array()
                out.write(sizeBytes)

                // Envia bytes da foto
                out.write(photo)
                out.flush()
                socket.close()

                runOnUiThread { statusView.text = "Foto enviada com sucesso!" }
            } catch (e: Exception) {
                runOnUiThread { statusView.text = "Erro ao enviar: ${e.message}" }
            }
        }.start()
    }
}
