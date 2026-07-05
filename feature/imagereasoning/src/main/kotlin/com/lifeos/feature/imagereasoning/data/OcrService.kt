package com.lifeos.feature.imagereasoning.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    /** ML Kit text blocks in reading order — the board parser's card candidates. */
    val blocks: List<String>,
    val barcodes: List<String>,
)

/** On-device OCR + barcode via ML Kit (§Module 11); nothing leaves the phone. */
@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun analyze(imageUri: Uri): OcrResult {
        val image = InputImage.fromFilePath(context, imageUri)

        val text = suspendCancellableCoroutine { continuation ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }

        val barcodes = try {
            suspendCancellableCoroutine { continuation ->
                BarcodeScanning.getClient()
                    .process(image)
                    .addOnSuccessListener { result ->
                        continuation.resume(result.mapNotNull { it.rawValue })
                    }
                    .addOnFailureListener { continuation.resume(emptyList()) }
            }
        } catch (e: Exception) {
            emptyList()
        }

        return OcrResult(
            fullText = text.text,
            blocks = text.textBlocks.map { it.text },
            barcodes = barcodes,
        )
    }
}
