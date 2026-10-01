package com.gonzalogiulio.pockettimer

import android.content.Context
import android.content.res.AssetFileDescriptor

object SoundCatalog {
    const val NONE = "none"
    const val BUILTIN = "builtin"

    private const val SOUNDS_DIR = "sounds"
    private val SUPPORTED_EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a", "aac")

    data class SoundOption(val id: String, val label: String)

    fun list(context: Context): List<SoundOption> {
        val assetSounds = context.assets.list(SOUNDS_DIR)
            ?.filter { it.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS }
            ?.sorted()
            .orEmpty()

        return buildList {
            add(SoundOption(NONE, "None (silent)"))
            add(SoundOption(BUILTIN, "Beep (built-in)"))
            assetSounds.forEach { add(SoundOption(it, prettify(it))) }
        }
    }

    fun resolve(context: Context, soundId: String): AssetFileDescriptor? = when (soundId) {
        NONE -> null
        BUILTIN -> context.resources.openRawResourceFd(R.raw.beep)
        else -> try {
            context.assets.openFd("$SOUNDS_DIR/$soundId")
        } catch (_: Exception) {
            context.resources.openRawResourceFd(R.raw.beep)
        }
    }

    private fun prettify(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
