package io.github.kyujin0911.pounter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

class PhotoStore(private val context: Context) {
    private val directory = File(context.filesDir,"photos").apply { mkdirs() }
    fun file(name: String): File {
        require(name == File(name).name && name.endsWith(".jpg"))
        return File(directory,name)
    }
    suspend fun import(uri: Uri): Triple<String,Int,Int> = withContext(Dispatchers.IO) {
        val raw=File.createTempFile("import-",".source",context.cacheDir)
        var normalized: Bitmap? = null
        var decoded: Bitmap? = null
        var destination: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output ->
                    val buffer=ByteArray(8192); var total=0L
                    while(true) {
                        val n=input.read(buffer); if(n<0) break
                        total+=n; if(total>50L*1024*1024) throw IOException("사진 파일은 50MB 이하여야 합니다.")
                        output.write(buffer,0,n)
                    }
                }
            } ?: throw IOException("사진을 읽을 수 없습니다. 다시 선택해주세요.")
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(raw.path,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0) throw IOException("지원하지 않는 사진입니다.")
            var sample=1
            while(max(bounds.outWidth,bounds.outHeight)/sample>3000) sample*=2
            decoded=BitmapFactory.decodeFile(raw.path,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 })
                ?: throw IOException("사진을 열 수 없습니다.")
            val exif=ExifInterface(raw)
            val matrix=Matrix().apply {
                if(exif.isFlipped) postScale(-1f,1f)
                if(exif.rotationDegrees!=0) postRotate(exif.rotationDegrees.toFloat())
            }
            normalized=Bitmap.createBitmap(decoded!!,0,0,decoded!!.width,decoded!!.height,matrix,true)
            val name="${UUID.randomUUID()}.jpg"
            destination=file(name)
            destination!!.outputStream().use { out ->
                if(!normalized!!.compress(Bitmap.CompressFormat.JPEG,95,out)) throw IOException("사진 저장에 실패했습니다.")
            }
            Triple(name,normalized!!.width,normalized!!.height)
        } catch(e: Exception) { destination?.delete(); throw e }
        finally {
            if(uri.authority=="${context.packageName}.files" && uri.pathSegments.firstOrNull()=="captures") {
                uri.lastPathSegment?.takeIf { it==File(it).name }?.let { File(context.cacheDir,"captures/$it").delete() }
            }
            raw.delete(); if(normalized !== decoded) normalized?.recycle(); decoded?.recycle() }
    }
    suspend fun bitmap(name: String, maxSide: Int = 2048): Bitmap = withContext(Dispatchers.IO) {
        val path=file(name).path
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        var sample=1
        while(max(bounds.outWidth,bounds.outHeight)/sample>maxSide) sample*=2
        BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 })
            ?: throw IOException("저장된 사진을 읽을 수 없습니다.")
    }
    fun removeOrphans(referenced: Set<String>) {
        directory.listFiles()?.filter { it.extension=="jpg" && it.name !in referenced }?.forEach { delete(it.name) }
    }
    fun delete(name: String) { val f=file(name); if(f.exists() && !f.delete()) throw IOException("사진 삭제에 실패했습니다. 다시 시도해주세요.") }
}
