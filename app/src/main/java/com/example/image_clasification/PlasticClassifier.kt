package com.example.image_clasification

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale
import com.example.image_clasification.ml.TrainedModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class PlasticClassifier (private val context: Context) {

    // Nama kelas sesuai dengan output model
    private val classLabels = arrayOf("HDPE", "LDPE", "NotPlastic", "OTHER", "PET", "PP", "PS", "PVC")

    fun classifyImage(bitmap: Bitmap): ClassificationResult {
        try {
            Log.d("PlasticClassifier", "Memulai klasifikasi gambar")

            val model = TrainedModel.newInstance(context)

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)

            val resizedBitmap = bitmap.scale(224, 224)
            val byteBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4) // 4 bytes per float
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(224 * 224)
            resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val pixel = intValues[i * 224 + j]
                    byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                    byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                    byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            // Dapatkan hasil
            val results = outputFeature0.floatArray
            Log.d("PlasticClassifier", "Hasil raw: ${results.contentToString()}")

            // Temukan kelas dengan probabilitas tertinggi
            var maxIndex = 0
            var maxValue = results[0]
            for (i in results.indices) {
                Log.d("PlasticClassifier", "Kelas ${classLabels[i]}: ${results[i]}")
                if (results[i] > maxValue) {
                    maxValue = results[i]
                    maxIndex = i
                }
            }

            // Tutup model
            model.close()

            Log.d("PlasticClassifier", "Klasifikasi selesai: ${classLabels[maxIndex]} (${maxValue})")
            return ClassificationResult(
                className = classLabels[maxIndex],
                confidence = maxValue,
                allProbabilities = results.toList()
            )
        } catch (e : Exception) {
            Log.e("PlasticClassifier", "Error klasifikasi", e)
            return ClassificationResult(
                className = "Error",
                confidence = 0.0f,
                allProbabilities = List(8) { 0.0f }
            )
        }
    }

    // Data class untuk hasil klasifikasi
    data class ClassificationResult(
        val className: String,
        val confidence: Float,
        val allProbabilities: List<Float>
    )
}