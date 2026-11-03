package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.FileKind
import org.drinkless.tdlib.TdApi

/** The downloadable part of a Telegram message, normalised across media types. */
data class TdMedia(
    val file: TdApi.File,
    val name: String,
    val mime: String,
    val kind: FileKind,
    val thumbnail: TdApi.File?
)

object TdMediaExtractor {
    fun extract(content: TdApi.MessageContent): TdMedia? = when (content) {
        is TdApi.MessageDocument -> content.document.let {
            TdMedia(it.document, it.fileName.orEmpty(), it.mimeType.orEmpty(), FileKind.DOCUMENT, it.thumbnail?.file)
        }
        is TdApi.MessageVideo -> content.video.let {
            TdMedia(it.video, it.fileName.orEmpty(), it.mimeType.orEmpty(), FileKind.VIDEO, it.thumbnail?.file)
        }
        is TdApi.MessageAudio -> content.audio.let {
            TdMedia(it.audio, it.fileName.orEmpty(), it.mimeType.orEmpty(), FileKind.AUDIO, it.albumCoverThumbnail?.file)
        }
        is TdApi.MessageAnimation -> content.animation.let {
            TdMedia(it.animation, it.fileName.orEmpty(), it.mimeType.orEmpty(), FileKind.ANIMATION, it.thumbnail?.file)
        }
        is TdApi.MessageVoiceNote -> content.voiceNote.let {
            TdMedia(it.voice, "", it.mimeType.orEmpty(), FileKind.VOICE, null)
        }
        is TdApi.MessageVideoNote -> content.videoNote.let {
            TdMedia(it.video, "", "video/mp4", FileKind.VIDEO, it.thumbnail?.file)
        }
        is TdApi.MessagePhoto -> {
            val sizes = content.photo.sizes
            val best = sizes.maxByOrNull { it.width.toLong() * it.height }
            val small = sizes.filter { it.width >= 200 }.minByOrNull { it.width.toLong() * it.height } ?: best
            if (best == null) null else TdMedia(best.photo, "", "image/jpeg", FileKind.IMAGE, small?.photo)
        }
        else -> null
    }
}
