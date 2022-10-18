package com.github.jing332.tts_server_android.utils

import java.io.File
import java.io.FileOutputStream

object FileUtils {
    fun fileExists(file: File): Boolean {
        runCatching {
            if (file.isFile)
                return file.exists()
        }
        return false
    }

    fun fileExists(filePath: String): Boolean {
        return fileExists(File(filePath))
    }

    fun saveFile(path: String, data: ByteArray): Boolean {
        try {
            val file = File(path)
            if (!fileExists(file)) {
                if (file.parentFile?.exists() == false) /* 文件夹不存在则创建 */
                    file.parentFile?.mkdirs()
            }
            val fos = FileOutputStream(file)
            fos.write(data)
            fos.flush()
            fos.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}