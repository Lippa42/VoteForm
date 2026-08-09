package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.model.MediaAsset
import it.marcolipparini.sfide.engine.model.MediaConfig
import it.marcolipparini.sfide.engine.samples.SampleRooms
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AssetResolverTest {

    @Test
    fun `serve i byte del file locale con il content type corretto`() {
        val tmp = File.createTempFile("asset", ".png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)); deleteOnExit() }
        val room = SampleRooms.quizTournament().copy(
            media = MediaConfig(assets = listOf(MediaAsset(id = "a1", localPath = tmp.absolutePath))),
        )
        val resolver = RoomAssetResolver(room)

        val data = resolver.open("a1")
        assertNotNull(data)
        assertEquals("image/png", data!!.contentType)
        assertEquals(4, data.bytes.size)

        assertNull(resolver.open("inesistente"))
    }
}
