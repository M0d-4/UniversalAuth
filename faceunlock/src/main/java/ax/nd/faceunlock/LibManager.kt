package ax.nd.faceunlock

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class RequiredLib(
    val name: String,
    val armeabi_v7a: String? = null, // Honestly not sure if they need hard float, so maybe they are just armeabi?
    val arm64_v8a: String? = null
) {
    fun hashForCurrentAbi(): String? {
        val abis = Build.SUPPORTED_ABIS
        if("arm64-v8a" in abis) {
            return arm64_v8a
        }
//        if("armeabi-v7a" in abis) {
//            return armeabi_v7a
//        }
        return null
    }
}

data class LibraryState(
    val library: RequiredLib,
    val valid: Boolean
)

object LibManager {
    const val LIB_DIR = "facelibs"
    const val HASH_TYPE = "SHA-512"
    private val TAG = LibManager::class.simpleName

    // ORDER MATTERS!!! The libraries will be loaded in this order by updateLibraryData()
    // Some of the libraries depend on each other so they must be loaded in the correct order
    // TODO: arm64_v8a hashes are null (verification skipped) pending extraction from
    //       Moto Face Unlock 02.01.616 arm64-v8a split APK. Update once the split is available.
    val requiredLibraries = listOf(
        RequiredLib("libmegface.so", armeabi_v7a = null, arm64_v8a = null),
        RequiredLib("libFaceDetectCA.so", armeabi_v7a = null, arm64_v8a = null),
        RequiredLib("libMegviiUnlock.so", armeabi_v7a = null, arm64_v8a = null),
        RequiredLib("libMegviiUnlock-jni-1.2.so", armeabi_v7a = null, arm64_v8a = null),
    )

    val librariesData = MutableStateFlow<List<LibraryState>>(emptyList())
    val libsLoaded = AtomicBoolean(false)
    val libLoadError = MutableStateFlow<Throwable?>(null)

    fun init(context: Context) {
        // It's IO but we do it on main thread as it's pretty important
        updateLibraryData(context)
    }

    fun updateLibraryData(context: Context) {
        val newStatus = requiredLibraries.map {
            LibraryState(
                library = it,
                valid = getLibFile(context, it).exists()
            )
        }
        librariesData.value = newStatus
        // Load libs if they all valid
        if (newStatus.all { it.valid }) {
            try {
                for(lib in newStatus) {
                    System.load(getLibFile(context, lib.library).absolutePath)
                }
                libsLoaded.set(true)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load native libraries!", t)
                libLoadError.value = t
            }
        }
    }

    fun getLibFile(context: Context, lib: RequiredLib, temp: Boolean = false): File {
        val fileDir = context.filesDir
        val libsDir = File(fileDir, LIB_DIR)
        val fname = if(temp) {
            "${lib.name}.tmp"
        } else lib.name
        return File(libsDir, fname)
    }
}