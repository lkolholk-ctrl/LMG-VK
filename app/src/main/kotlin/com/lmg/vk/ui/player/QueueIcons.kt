package com.lmg.vk.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val QueueInfinityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QueueInfinity",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 12f)
            curveTo(10.1f, 9.1f, 8.7f, 8f, 7.1f, 8f)
            arcToRelative(4f, 4f, 0f, false, false, 0f, 8f)
            curveTo(8.7f, 16f, 10.1f, 14.9f, 12f, 12f)
            curveTo(13.9f, 9.1f, 15.3f, 8f, 16.9f, 8f)
            arcToRelative(4f, 4f, 0f, false, true, 0f, 8f)
            curveTo(15.3f, 16f, 13.9f, 14.9f, 12f, 12f)
        }
    }.build()
}
