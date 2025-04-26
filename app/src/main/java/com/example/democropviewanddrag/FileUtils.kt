import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileUtils {


    /**
     * Get system services
     */
    inline fun <reified T> getSystemService(context: Context): T? =
        ContextCompat.getSystemService(context, T::class.java)

    private fun getMemory(context: Context): ActivityManager.MemoryInfo {
        val memInfo = ActivityManager.MemoryInfo()
        getSystemService<ActivityManager>(context)?.getMemoryInfo(memInfo)
        return memInfo
    }
    private fun BUFFER_SIZE(context: Context): Int {
        return if (memoryByGB(context) > 2)
            DEFAULT_BUFFER_SIZE
        else 1024 * 4
    }

    fun memoryByGB(context: Context): Double {
        return getMemory(context).totalMem / 1073741824.0
    }
    suspend fun createImageUri(activity : Activity, timeRetry : Int? = null): Uri? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"

        val countTime = timeRetry ?: 0
        if (countTime > 1) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            activity.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Không thể tạo Uri trên Q+")
        } else {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                delay(500)
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    99
                )
            }

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!picturesDir.exists()) picturesDir.mkdirs()

            val imageFile = File(picturesDir, filename)
            withContext(Dispatchers.IO) {
                try {
                    FileOutputStream(imageFile).close()
                }catch (e: Exception){
                    createImageUri(activity, countTime + 1)
                    e.printStackTrace()
                }
            }

            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",  // đảm bảo phải khớp với manifest
                imageFile
            )
        }
    }

    fun copyImageFileFromUri(context : Context, uriString: Uri): File? {
        try {
            val file = File(context.filesDir, "${System.currentTimeMillis()}.png")
            if (file.exists()) file.delete()
            val outputStream = FileOutputStream(file)
            context.contentResolver.openInputStream(uriString)?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output, BUFFER_SIZE(context))
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


}