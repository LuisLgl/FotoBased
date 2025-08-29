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

    // Códigos numéricos para identificar os pedidos de câmera e permissão.
    private val CAMERA_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002
    // Variável para guardar o endereço (Uri) da foto tirada.
    private var currentPhotoUri: Uri? = null

    // Função executada quando a tela é criada. Ponto de partida do app.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pega a referência da ImageView do layout.
        val imageView = findViewById<ImageView>(R.id.previewImage)
        // Configura o "ouvinte de clique": o código aqui dentro será executado quando a imagem for tocada.
        imageView.setOnClickListener {
            // Verifica a permissão da câmera antes de abri-la.
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    // Função que verifica se a permissão da câmera já foi concedida.
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Função que exibe a janela de diálogo para pedir a permissão da câmera.
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
    }

    // Função chamada DEPOIS que o usuário responde ao pedido de permissão.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Se a permissão foi concedida, abre a câmera.
        if (requestCode == PERMISSION_REQUEST_CODE && (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            openCamera()
        }
    }

    // Função que prepara e inicia a câmera do celular.
    private fun openCamera() {
        // Cria a "intenção" de tirar uma foto.
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            // Cria um arquivo temporário e guarda seu endereço na variável 'currentPhotoUri'.
            val photoFileUri = createImageUri()
            currentPhotoUri = photoFileUri
            // Informa à câmera para salvar a foto nesse arquivo.
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoFileUri)
            // Inicia a câmera. O resultado será recebido em 'onActivityResult'.
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao criar arquivo de imagem", Toast.LENGTH_SHORT).show()
        }
    }

    // Função auxiliar que cria um arquivo temporário para a foto.
    private fun createImageUri(): Uri {
        val imageFile = File.createTempFile("temp_photo", ".jpg", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(this, "${packageName}.provider", imageFile)
    }

    // Função principal de trabalho, chamada DEPOIS que a câmera tira a foto e retorna ao app.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Pega as referências dos componentes visuais (Views) que vamos manipular.
        val imageView = findViewById<ImageView>(R.id.previewImage)
        val textStatus = findViewById<TextView>(R.id.imagePlaceholderText)
        val ipInput = findViewById<EditText>(R.id.serverIpInput)
        val portInput = findViewById<EditText>(R.id.serverPortInput)

        // Bloco principal: só executa se o resultado veio da câmera e se o usuário tirou uma foto com sucesso.
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            currentPhotoUri?.let { uri ->
                try {
                    // Carrega a imagem do arquivo para a memória como um objeto Bitmap.
                    val inputStream = contentResolver.openInputStream(uri)
                    var originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap == null) {
                        Toast.makeText(this, "Não foi possível ler a imagem", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Bloco de CORREÇÃO DE ORIENTAÇÃO da foto.
                    // Lê os metadados da imagem (EXIF) para descobrir se ela foi tirada de lado
                    // ou de cabeça para baixo e a rotaciona para a posição correta.
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

                    // Bloco de REDIMENSIONAMENTO da foto.
                    // Se a largura for maior que 1280 pixels, diminui para 1280, mantendo a proporção.
                    val scaledWidth = if (originalBitmap.width > 1280) 1280 else originalBitmap.width
                    val scaledHeight = (scaledWidth.toFloat() / originalBitmap.width * originalBitmap.height).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

                    // Mostra a imagem final (processada) na tela.
                    imageView.setImageBitmap(scaledBitmap)

                    // Bloco de PREPARAÇÃO PARA ENVIO.
                    // Comprime a imagem para o formato JPEG (qualidade 80) e a converte em um array de bytes.
                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val photoBytes = stream.toByteArray()

                    // Lê os valores de IP e Porta digitados pelo usuário.
                    val ipStr = ipInput.text.toString()
                    val portStr = portInput.text.toString()

                    // Verifica se os campos foram preenchidos antes de enviar.
                    if (ipStr.isNotEmpty() && portStr.isNotEmpty()) {
                        val portInt = portStr.toInt()
                        // Chama a função para enviar a foto pela rede.
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

    // Função responsável pelo ENVIO DA FOTO PELA REDE.
    private fun sendPhoto(ip: String, port: Int, photo: ByteArray, statusView: TextView) {
        // CRIA UMA THREAD EM SEGUNDO PLANO para executar a operação de rede.
        // Isso é ESSENCIAL para não travar a interface do usuário.
        thread {
            // Atualiza o status na tela para "Enviando...". Precisa ser na thread da UI.
            runOnUiThread { statusView.text = "Enviando, aguarde..." }

            try {
                // Cria a conexão de rede (Socket) com o servidor.
                // O .use {} garante que o socket será fechado automaticamente.
                Socket(ip, port).use { socket ->
                    val out = socket.getOutputStream()
                    // Prepara o cabeçalho de 4 bytes com o tamanho da imagem (nosso protocolo).
                    val sizeBytes = ByteBuffer.allocate(4).putInt(photo.size).array()
                    // Envia os 4 bytes com o tamanho da imagem.
                    out.write(sizeBytes)
                    // Envia os bytes da imagem em si.
                    out.write(photo)
                    // Garante que todos os dados foram enviados.
                    out.flush()
                }
                // Se tudo deu certo, atualiza o status para sucesso.
                runOnUiThread { statusView.text = "Foto enviada com sucesso!" }
            } catch (e: Exception) {
                e.printStackTrace()
                // Se deu erro, atualiza o status com a mensagem de erro.
                runOnUiThread { statusView.text = "Erro ao enviar: ${e.message}" }
            }
        }
    }
}