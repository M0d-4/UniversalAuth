package ax.nd.faceunlock

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.binary.Hex
import java.io.*
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipInputStream
import kotlin.Exception

sealed interface CheckResult {
    val library: RequiredLib

    data class BadHash(override val library: RequiredLib, val tmpFile: File) : CheckResult
    data class FileError(override val library: RequiredLib, val error: Exception) : CheckResult
}

sealed interface DownloadStatus {
    data class Downloading(val importing: Boolean, val progress: Float = 0f, val progressMessage: String = "") : DownloadStatus
    object AskImport : DownloadStatus
    data class DownloadError(val importing: Boolean, val error: Exception?) : DownloadStatus
}

class ChooseLibsViewModel : ViewModel() {
    val checkingStatus = MutableStateFlow(false)
    val checkResult = MutableStateFlow<CheckResult?>(null)
    val downloadStatus = MutableStateFlow<DownloadStatus?>(null)
    private val okhttp = OkHttpClient()

    fun downloadLibs(context: Context, uri: Uri?) {
        if(downloadStatus.value !is DownloadStatus.Downloading) {
            downloadStatus.value = DownloadStatus.Downloading(uri != null, 0f, "Starting...")
            viewModelScope.launch {
                try {
                    downloadLibsInternal(context, uri)
                    downloadStatus.value = DownloadStatus.DownloadError(uri != null, null)
                } catch (e: Exception) {
                    downloadStatus.value = DownloadStatus.DownloadError(uri != null, e)
                }
            }
        }
    }

    private fun updateDownloadProgress(isImporting: Boolean, progress: Float, message: String) {
        downloadStatus.value = DownloadStatus.Downloading(isImporting, progress, message)
    }

    private fun downloadApk(): InputStream {
        val url = "$IPFS_GATEWAY/ipfs/$LIBS_CID"

        val req = Request.Builder()
            .url(url)
            .build()

        val response = okhttp.newCall(req).execute()
        val body = response.body ?: run {
            throw IOException("Response body is null!")
        }

        try {
            return body.byteStream()
        } catch (e: Exception) {
            body.close()
            throw e
        }
    }

    private fun downloadApkWithProgress(context: Context): InputStream {
        updateDownloadProgress(false, 0f, "Downloading Moto Face Unlock APK...")
        val url = "$IPFS_GATEWAY/ipfs/$LIBS_CID"

        val req = Request.Builder()
            .url(url)
            .build()

        val response = okhttp.newCall(req).execute()
        val body = response.body ?: run {
            throw IOException("Response body is null!")
        }

        try {
            val contentLength = body.contentLength()
            val bufferedBody = body.byteStream()
            
            // Wrap stream to track progress
            return if(contentLength > 0) {
                ProgressInputStream(bufferedBody, contentLength) { bytesRead, total ->
                    val percentage = (bytesRead.toFloat() / total.toFloat() * 100).toInt()
                    updateDownloadProgress(false, bytesRead.toFloat() / total.toFloat(), 
                        "Downloading: $percentage% (${bytesRead / (1024 * 1024)}/${total / (1024 * 1024)} MB)")
                }
            } else {
                bufferedBody
            }
        } catch (e: Exception) {
            body.close()
            throw e
        }
    }

    private fun openImportUri(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri) ?: run {
            throw NullPointerException("ContentProvider crashed!")
        }
    }

    private suspend fun downloadLibsInternal(context: Context, uri: Uri?) {
        withContext(Dispatchers.IO) {
            val inputStream = if(uri != null) {
                updateDownloadProgress(true, 0f, "Importing APK...")
                openImportUri(context, uri)
            } else {
                downloadApkWithProgress(context)
            }
            inputStream.buffered().use { resp ->
                updateDownloadProgress(uri != null, 0.5f, "Extracting libraries...")
                val zin = ZipInputStream(resp)
                var filesProcessed = 0
                val totalFiles = LibManager.requiredLibraries.size
                
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name
                    if (!name.startsWith("lib/arm64-v8a/")) {
                        continue
                    }
                    val fname = name.substringAfterLast('/')
                    val lib = LibManager.requiredLibraries.find { it.name == fname } ?: continue

                    val digest = try {
                        MessageDigest.getInstance(LibManager.HASH_TYPE)
                    } catch (e: NoSuchAlgorithmException) {
                        throw IOException("Missing hash type: ${LibManager.HASH_TYPE}", e)
                    }

                    val outFile = LibManager.getLibFile(context, lib, temp = true)
                    outFile.parentFile?.mkdirs()
                    DigestOutputStream(outFile.outputStream().buffered(), digest).use { ostream ->
                        zin.copyTo(ostream)
                    }

                    val hash = digest.digest()
                    val hex = Hex.encodeHexString(hash, true)

                    val targetHash = lib.hashForCurrentAbi() ?: run {
                        throw UnsupportedOperationException("This app cannot run on your device: unsupported ABI!")
                    }

                    if(hex == targetHash) {
                        val realFile = LibManager.getLibFile(context, lib)
                        realFile.parentFile?.mkdirs()
                        if(!outFile.renameTo(realFile)) {
                            throw IOException("Failed to rename temp file!")
                        }
                        filesProcessed++
                        val progress = 0.5f + (filesProcessed.toFloat() / totalFiles * 0.5f)
                        updateDownloadProgress(uri != null, progress, 
                            "Extracted $filesProcessed/$totalFiles libraries")
                        LibManager.updateLibraryData(context)
                        if(LibManager.libsLoaded.get()) {
                            break
                        }
                    } else {
                        throw IOException("Hash mismatch for $fname, maybe the download got corrupted?")
                    }
                }
            }
        }
    }
                }
            }
        }
    }

    fun addLib(context: Context, library: RequiredLib, uri: Uri) {
        if (!checkingStatus.value && checkResult.value == null) {
            checkingStatus.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    addLibInternal(context, library, uri)
                } finally {
                    checkingStatus.value = false
                }
            }
        }
    }

    fun saveBadHashLib(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = checkResult.value
            if (!checkingStatus.value && result is CheckResult.BadHash) {
                if(!result.tmpFile.renameTo(LibManager.getLibFile(context, result.library))) {
                    val exception = IOException("Failed to save library file!")
                    checkResult.value = CheckResult.FileError(result.library, exception)
                    Log.e(TAG, "Failed to save library file!", exception)
                    return@launch
                }
                // Valid, update library data
                LibManager.updateLibraryData(context)
            }
            clearCheckResult()
        }
    }

    fun setAskImport() {
        if(downloadStatus.value !is DownloadStatus.Downloading) {
            downloadStatus.value = DownloadStatus.AskImport
        }
    }

    fun clearDownloadResult() {
        if(downloadStatus.value !is DownloadStatus.Downloading) {
            downloadStatus.value = null
        }
    }

    fun clearCheckResult() {
        checkResult.value = null
    }

    private fun addLibInternal(context: Context, library: RequiredLib, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri) ?: run {
                val exception = NullPointerException("ContentProvider crashed!")
                checkResult.value = CheckResult.FileError(library, exception)
                Log.e(TAG, "Failed to open input stream!", exception)
                return
            }
        } catch (e: FileNotFoundException) {
            checkResult.value = CheckResult.FileError(library, e)
            Log.e(TAG, "Content provider reported file not found!", e)
            return
        }.use { inputStream ->
            val targetFile = LibManager.getLibFile(context, library, temp = true)
            targetFile.parentFile?.mkdirs()

            val digest = try {
                MessageDigest.getInstance(LibManager.HASH_TYPE)
            } catch (e: NoSuchAlgorithmException) {
                checkResult.value = CheckResult.FileError(library, e)
                Log.e(TAG, "Missing hash type: ${LibManager.HASH_TYPE}", e)
                return
            }
            val wrappedInputStream = DigestInputStream(inputStream, digest)
            try {
                targetFile.outputStream().use { outputStream ->
                    wrappedInputStream.copyTo(outputStream)
                }
            } catch(e: IOException) {
                checkResult.value = CheckResult.FileError(library, e)
                Log.e(TAG, "Failed to write library to: $targetFile!", e)
                return
            }

            val hash = digest.digest()
            val hex = Hex.encodeHexString(hash, true)

            val targetHash = library.hashForCurrentAbi() ?: run {
                val exception = UnsupportedOperationException("This app cannot run on your device: unsupported ABI!")
                checkResult.value = CheckResult.FileError(library, exception)
                Log.e(TAG, "App cannot run on device, ABI not supported!", exception)
                return
            }
            if(hex == targetHash) {
                if(!targetFile.renameTo(LibManager.getLibFile(context, library))) {
                    val exception = IOException("Failed to save library file!")
                    checkResult.value = CheckResult.FileError(library, exception)
                    Log.e(TAG, "Failed to save library file!", exception)
                    return
                }
                // Valid, update library data
            } else {
                checkResult.value = CheckResult.BadHash(library, tmpFile = targetFile)
            }
        }
    }

    companion object {
        private val TAG = ChooseLibsViewModel::class.simpleName

        private const val IPFS_GATEWAY = "https://cloudflare-ipfs.com"
        private const val LIBS_CID = "QmQNREjjXTQBDpd69gFqEreNi1dV91eSGQByqi5nXU3rBt"
    }
}

/**
 * ProgressInputStream wraps an InputStream and tracks read progress.
 * Invokes a callback with bytes read and total size for progress reporting.
 */
class ProgressInputStream(
    private val delegate: InputStream,
    private val totalSize: Long,
    private val onProgress: (bytesRead: Long, total: Long) -> Unit
) : InputStream() {
    private var bytesRead = 0L

    override fun read(): Int {
        val byte = delegate.read()
        if (byte != -1) {
            bytesRead++
            onProgress(bytesRead, totalSize)
        }
        return byte
    }

    override fun read(b: ByteArray): Int {
        val n = delegate.read(b)
        if (n > 0) {
            bytesRead += n
            onProgress(bytesRead, totalSize)
        }
        return n
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) {
            bytesRead += n
            onProgress(bytesRead, totalSize)
        }
        return n
    }

    override fun close() {
        delegate.close()
    }

    override fun available(): Int = delegate.available()

    override fun skip(n: Long): Long = delegate.skip(n)

    override fun markSupported(): Boolean = false
}