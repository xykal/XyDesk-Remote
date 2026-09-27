package id.xydesk.remote.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ikon XyDesk — set garis 24dp, stroke 1.7, ujung bulat.
 *
 * Digambar sendiri (bukan set bawaan OS/Material) supaya bobot garis dan
 * bentuknya konsisten dengan tipografi app. Semua ikon stroke-only, jadi
 * `Icon(tint = ...)` mewarnainya seragam.
 */
private fun xyIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

private fun xySolid(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White), pathBuilder = block)
    }.build()

object XyIcons {

    /** Monitor: perangkat remote. */
    val Monitor: ImageVector = xyIcon("XyMonitor") {
        moveTo(2.5f, 4.5f); lineTo(21.5f, 4.5f); lineTo(21.5f, 16f); lineTo(2.5f, 16f); close()
        moveTo(9f, 20.5f); lineTo(15f, 20.5f)
        moveTo(12f, 16f); lineTo(12f, 20.5f)
    }

    /** Cursor: penunjuk mouse. */
    val Cursor: ImageVector = xySolid("XyCursor") {
        moveTo(6f, 3f); lineTo(6f, 18.5f); lineTo(10.2f, 14.6f); lineTo(13.1f, 20.8f)
        lineTo(15.6f, 19.6f); lineTo(12.8f, 13.6f); lineTo(18.4f, 13.1f); close()
    }

    val Keyboard: ImageVector = xyIcon("XyKeyboard") {
        moveTo(2.5f, 6.5f); lineTo(21.5f, 6.5f); lineTo(21.5f, 17.5f); lineTo(2.5f, 17.5f); close()
        moveTo(6f, 10f); lineTo(6.01f, 10f)
        moveTo(9.5f, 10f); lineTo(9.51f, 10f)
        moveTo(13f, 10f); lineTo(13.01f, 10f)
        moveTo(16.5f, 10f); lineTo(16.51f, 10f)
        moveTo(8f, 14f); lineTo(16f, 14f)
    }

    val Mouse: ImageVector = xyIcon("XyMouse") {
        moveTo(12f, 3f)
        curveTo(8.7f, 3f, 6.5f, 5.5f, 6.5f, 9f)
        lineTo(6.5f, 15f)
        curveTo(6.5f, 18.5f, 8.7f, 21f, 12f, 21f)
        curveTo(15.3f, 21f, 17.5f, 18.5f, 17.5f, 15f)
        lineTo(17.5f, 9f)
        curveTo(17.5f, 5.5f, 15.3f, 3f, 12f, 3f)
        close()
        moveTo(12f, 3f); lineTo(12f, 9f)
    }

    val ScrollUp: ImageVector = xyIcon("XyScrollUp") {
        moveTo(7f, 14f); lineTo(12f, 8.5f); lineTo(17f, 14f)
        moveTo(7f, 19f); lineTo(17f, 19f)
    }

    val ScrollDown: ImageVector = xyIcon("XyScrollDown") {
        moveTo(7f, 10f); lineTo(12f, 15.5f); lineTo(17f, 10f)
        moveTo(7f, 5f); lineTo(17f, 5f)
    }

    val Swap: ImageVector = xyIcon("XySwap") {
        moveTo(4f, 9f); lineTo(18f, 9f); moveTo(15f, 6f); lineTo(18f, 9f); lineTo(15f, 12f)
        moveTo(20f, 15f); lineTo(6f, 15f); moveTo(9f, 12f); lineTo(6f, 15f); lineTo(9f, 18f)
    }

    val Shot: ImageVector = xyIcon("XyShot") {
        moveTo(3f, 7.5f); lineTo(8f, 7.5f); lineTo(9.5f, 5f); lineTo(14.5f, 5f)
        lineTo(16f, 7.5f); lineTo(21f, 7.5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(12f, 10.5f); lineTo(12f, 16f)
        moveTo(9.5f, 13.2f); lineTo(12f, 10.5f); lineTo(14.5f, 13.2f)
    }

    val Fit: ImageVector = xyIcon("XyFit") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }

    val Rotate: ImageVector = xyIcon("XyRotate") {
        moveTo(20f, 12f)
        curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
        curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
        curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
        lineTo(16f, 4f)
        moveTo(13.5f, 1.5f); lineTo(16.2f, 4f); lineTo(13.5f, 6.5f)
    }

    val Power: ImageVector = xyIcon("XyPower") {
        moveTo(12f, 3.5f); lineTo(12f, 11f)
        moveTo(17.5f, 6.2f)
        curveTo(19.6f, 8f, 20.8f, 10.5f, 20.5f, 13.4f)
        curveTo(20.1f, 17.6f, 16.5f, 20.6f, 12.3f, 20.5f)
        curveTo(8.1f, 20.4f, 4.7f, 17.1f, 4.5f, 13f)
        curveTo(4.4f, 10.2f, 5.6f, 7.9f, 7.6f, 6.2f)
    }

    val Plus: ImageVector = xyIcon("XyPlus") {
        moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Close: ImageVector = xyIcon("XyClose") {
        moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Check: ImageVector = xyIcon("XyCheck") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
    }

    val ChevronLeft: ImageVector = xyIcon("XyChevronLeft") {
        moveTo(14.5f, 5f); lineTo(8f, 12f); lineTo(14.5f, 19f)
    }

    val ChevronRight: ImageVector = xyIcon("XyChevronRight") {
        moveTo(9.5f, 5f); lineTo(16f, 12f); lineTo(9.5f, 19f)
    }

    val ChevronDown: ImageVector = xyIcon("XyChevronDown") {
        moveTo(5f, 9f); lineTo(12f, 15.5f); lineTo(19f, 9f)
    }

    val Sliders: ImageVector = xyIcon("XySliders") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 17f); lineTo(20f, 17f)
        moveTo(9f, 7f); lineTo(9f, 7.01f)
        moveTo(15f, 12f); lineTo(15f, 12.01f)
        moveTo(11f, 17f); lineTo(11f, 17.01f)
    }

    val Lock: ImageVector = xyIcon("XyLock") {
        moveTo(5.5f, 10.5f); lineTo(18.5f, 10.5f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
        moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f)
        curveTo(8.5f, 4.7f, 10.2f, 3.5f, 12f, 3.5f)
        curveTo(13.8f, 3.5f, 15.5f, 4.7f, 15.5f, 7.5f)
        lineTo(15.5f, 10.5f)
    }

    val Info: ImageVector = xyIcon("XyInfo") {
        moveTo(12f, 3.5f)
        curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
        curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
        curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
        curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
        close()
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.6f); lineTo(12f, 7.61f)
    }

    val Trash: ImageVector = xyIcon("XyTrash") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.6f, 20f); lineTo(16.4f, 20f); lineTo(17.5f, 7f)
        moveTo(10.5f, 11f); lineTo(10.5f, 16.5f)
        moveTo(13.5f, 11f); lineTo(13.5f, 16.5f)
    }

    val Wifi: ImageVector = xyIcon("XyWifi") {
        moveTo(3f, 9.2f)
        curveTo(8.2f, 4.4f, 15.8f, 4.4f, 21f, 9.2f)
        moveTo(6.2f, 12.8f)
        curveTo(9.4f, 9.9f, 14.6f, 9.9f, 17.8f, 12.8f)
        moveTo(9.4f, 16.4f)
        curveTo(10.9f, 15.1f, 13.1f, 15.1f, 14.6f, 16.4f)
        moveTo(12f, 19.6f); lineTo(12f, 19.61f)
    }

    val Volume: ImageVector = xyIcon("XyVolume") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f)
        lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15f, 9f); moveTo(15f, 9f)
        curveTo(16.6f, 10.6f, 16.6f, 13.4f, 15f, 15f)
    }

    val Mic: ImageVector = xyIcon("XyMic") {
        moveTo(9f, 6f)
        curveTo(9f, 4.3f, 10.3f, 3f, 12f, 3f)
        curveTo(13.7f, 3f, 15f, 4.3f, 15f, 6f)
        lineTo(15f, 11f)
        curveTo(15f, 12.7f, 13.7f, 14f, 12f, 14f)
        curveTo(10.3f, 14f, 9f, 12.7f, 9f, 11f)
        close()
        moveTo(6f, 11.5f)
        curveTo(6f, 15.1f, 8.7f, 17.5f, 12f, 17.5f)
        curveTo(15.3f, 17.5f, 18f, 15.1f, 18f, 11.5f)
        moveTo(12f, 17.5f); lineTo(12f, 21f)
    }

    val Folder: ImageVector = xyIcon("XyFolder") {
        moveTo(3f, 6.5f); lineTo(9.5f, 6.5f); lineTo(11.5f, 9f); lineTo(21f, 9f)
        lineTo(21f, 18.5f); lineTo(3f, 18.5f); close()
    }

    val Clip: ImageVector = xyIcon("XyClip") {
        moveTo(8.5f, 6f); lineTo(15.5f, 6f); lineTo(15.5f, 9f); lineTo(8.5f, 9f); close()
        moveTo(10f, 6f); lineTo(10f, 3.5f); lineTo(14f, 3.5f); lineTo(14f, 6f)
        moveTo(6.5f, 9f); lineTo(6.5f, 20.5f); lineTo(17.5f, 20.5f); lineTo(17.5f, 9f)
    }

    val Gear: ImageVector = xyIcon("XyGear") {
        moveTo(12f, 8.6f)
        curveTo(13.9f, 8.6f, 15.4f, 10.1f, 15.4f, 12f)
        curveTo(15.4f, 13.9f, 13.9f, 15.4f, 12f, 15.4f)
        curveTo(10.1f, 15.4f, 8.6f, 13.9f, 8.6f, 12f)
        curveTo(8.6f, 10.1f, 10.1f, 8.6f, 12f, 8.6f)
        close()
        moveTo(12f, 2.8f); lineTo(13.3f, 5.4f); moveTo(12f, 21.2f); lineTo(10.7f, 18.6f)
        moveTo(4.6f, 7.4f); lineTo(7.4f, 7.9f); moveTo(16.6f, 16.1f); lineTo(19.4f, 16.6f)
        moveTo(4.6f, 16.6f); lineTo(7.4f, 16.1f); moveTo(16.6f, 7.9f); lineTo(19.4f, 7.4f)
    }

    val Menu: ImageVector = xyIcon("XyMenu") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 12f); lineTo(14f, 12f)
        moveTo(4f, 17f); lineTo(20f, 17f)
    }

    val Grid: ImageVector = xyIcon("XyGrid") {
        moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
    }
}
