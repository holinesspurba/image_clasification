package com.example.image_clasification

import android.os.Build
import android.Manifest
import android.R.attr.text
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.image_clasification.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var classifier: PlasticClassifier? = null

    private var latestClassName: String? = null

    // ActivityResultLauncher untuk memilih gambar dari galeri
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                binding.imageView.setImageBitmap(bitmap)
                classifyImage(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ActivityResultLauncher untuk mengambil gambar dari kamera
    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                binding.imageView.setImageBitmap(it)
                classifyImage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeClassifier()

        // Set click listeners
        binding.selectImageButton.setOnClickListener {
            checkPermissionAndSelectImage()
        }

        binding.captureImageButton.setOnClickListener {
            checkPermissionAndCaptureImage()
        }

        binding.infoFab.setOnClickListener {
            latestClassName?.let { plasticType ->
                // Buat query pencarian
                val searchQuery = "plastic $plasticType recycling"

                // Buat intent untuk pencarian web
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, searchQuery)
                }

                // Cek apakah ada aplikasi yang dapat menangani intent
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    // Fallback ke browser dengan URL Google
                    val browserIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(searchQuery)}"))
                    startActivity(browserIntent)
                }
            } ?: run {
                // Jika belum ada hasil klasifikasi
                Toast.makeText(this, "Silakan klasifikasikan gambar terlebih dahulu",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeClassifier() {
        try {
            classifier = PlasticClassifier(this)
            Log.d("MainActivity", "Classifier initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Classifier initialization failed", e)
            Toast.makeText(
                this,
                "Error loading plastic classification model: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            // Optionally disable classification functionality
            binding.selectImageButton.isEnabled = false
            binding.captureImageButton.isEnabled = false
        }
    }

    private fun checkPermissionAndSelectImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Untuk Android 13 (API 33) ke atas
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_STORAGE_PERMISSION
                )
            } else {
                selectImageLauncher.launch("image/*")
            }
        } else {
            // Untuk Android 12 (API 32) ke bawah
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            } else {
                selectImageLauncher.launch("image/*")
            }
        }
    }

    private fun checkPermissionAndCaptureImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureImageLauncher.launch(cameraIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectImageLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    captureImageLauncher.launch(cameraIntent)
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun classifyImage(bitmap: Bitmap) {

        Log.d("MainActivity", "Classifying image: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

        // Safe call with let to ensure classifier is initialized
        classifier?.let { classifierInstance ->
            binding.resultTypeTextView.text = "Klasifikasi sedang berlangsung..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Ensure the bitmap isn't recycled before classification
                    if (bitmap.isRecycled){
                        withContext(Dispatchers.Main) {
                            binding.resultTypeTextView.text = "Error: Bitmap sudah di-recycle"
                            return@withContext
                        }
                        return@launch
                    }

                    // Create a copy of the bitmap to prevent issues if the original is recycled
                    val safeBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                    val result = classifierInstance.classifyImage(safeBitmap)

                    // Log the classification result
                    Log.d("MainActivity", "Classification complete: ${result.className} (${result.confidence})")

                    withContext(Dispatchers.Main) {
                        // Menampilkan hasil klasifikasi
                        if (!isFinishing && !isDestroyed){

                            val recyclingBadge = findViewById<CardView>(R.id.recyclingBadge)

                            val isRecyclable = when (result.className) {
                                "HDPE", "PET",   -> "Bisa Daur Ulang"
                                "PVC", "LDPE", "OTHER" -> "Tidak Bisa Daur Ulang"
                                "PS", "PP"  -> "Bisa, Cek Panduan Daur Ulang"
                                "NotPlastic" -> "Bukan Plastik"
                                else -> "Unknown"
                            }

                            val backgroundColor = when (isRecyclable) {
                                "Bisa Daur Ulang" -> ContextCompat.getColor(this@MainActivity, R.color.recyclableColor) // hijau
                                "Tidak Bisa Daur Ulang" -> ContextCompat.getColor(this@MainActivity, R.color.nonRecyclableColor) // merah
                                "Bisa, Cek Panduan Daur Ulang" -> ContextCompat.getColor(this@MainActivity, R.color.maybeRecyclableColor) // kuning/oranye
                                "Bukan Plastik" -> ContextCompat.getColor(this@MainActivity, R.color.notPlasticColor) // abu-abu
                                else -> ContextCompat.getColor(this as Context, R.color.unknownColor) // abu-abu muda
                            }

                            val aplicationRecycle = when (result.className) {
                                "PET" -> "- Serat (Pakaian, Karpet)\n" +
                                        "- Film (balon, kemasan, lembaran termal, perekat)\n" +
                                        "- Botol (minuman soda, air)\n"

                                "HDPE" -> "- Wadah non-pangan (deterjen, shampoo, kondisioner, dan botol oli motor)\n" +
                                        "- Pipa\n" +
                                        "- Ember\n"+
                                        "- Pot bunga\n"

                                "PVC" -> "- Kemasan, binder, lantai decking\n" +
                                        "- Panel dinding, talang air, film\n" +
                                        "- Ubin lantai, kerucut lalu lintas\n" +
                                        "- Peralatan listrik, selang\n"

                                "LDPE" -> "- Amplop, kantong plastik sampah\n" +
                                        "- Talang air, film kemasan makanan\n" +
                                        "- Tas belanja, tempat pengomposan\n" +
                                        "- Kantong dry cleaning, tempat sampah\n"

                                "PP" -> "- Kotak baterai mobil, lampu sinyal\n" +
                                        "- Sapu, corong oli, sikat, pengikis es\n" +
                                        "- Botol bumbu, wadah margarin, wadah yogurt\n"

                                "PS" -> "- Termometer, pelat sakelar lampu\n" +
                                        "- Isolasi termal, wadah telur, penggaris\n" +
                                        "- Bingkai plat, kemasan busa\n" +
                                        "- Wadah makanan take-out, peralatan makan sekali pakai\n"

                                "OTHER" -> "- Computer-cases, iPod, galon air\n" +
                                        "- Kacamata plastik, benang nilon\n" +
                                        "- Dan alat elektronik\n"

                                else -> "Unknown"
                            }

                            val probabilitasBuilder = StringBuilder()

                            val classLabels = arrayOf("HDPE", "LDPE", "NotPlastic", "OTHER", "PET", "PP", "PS", "PVC")
                            for (i in result.allProbabilities.indices) {
                                if (i < classLabels.size) {
                                    probabilitasBuilder.append("${classLabels[i]}: ${String.format("%.2f", result.allProbabilities[i] * 100)}%\n")
                                }
                            }

                            latestClassName = result.className
                            binding.recyclingBadge.setCardBackgroundColor(backgroundColor)

                            binding.resultTypeTextView.text = result.className
                            binding.resultConfidenceTextView.text = "${String.format("%.2f", result.confidence * 100)}%"
                            binding.recyclingStatusTextView.text = isRecyclable
                            binding.probabilitasTextView.text = probabilitasBuilder.toString()
                            binding.example1TextView.text = aplicationRecycle
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Classification error", e)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            binding.resultTypeTextView.text = "Error klasifikasi: ${e.message}"
                            Toast.makeText(this@MainActivity, "Error klasifikasi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } ?: run {
            // Classifier is null
            Log.e("MainActivity", "Classifier is null")
            binding.resultTypeTextView.text = "Error: Classifier belum diinisialisasi"
            Toast.makeText(
                this,
                "Classifier not initialized. Please restart the app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
        private const val REQUEST_CAMERA_PERMISSION = 101
    }
}