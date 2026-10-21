package io.github.usmandurrany.tether.demo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's line icons, drawn on a 24-unit grid. Kept in code so the app
 * needs no icon library; `Icon()` tints them like any Material icon.
 */
object AppIcons {
    val Chat by lazy { icon("Chat") { roundRect(3f, 4f, 18f, 13f, 4.5f); poly(8f, 17f, 7.5f, 21f, 12.5f, 17f) } }
    val Server by lazy {
        icon("Server", dots = { circle(7.5f, 7.5f, 1.1f); circle(7.5f, 16.5f, 1.1f) }) {
            roundRect(3.5f, 4f, 17f, 7f, 2.5f); roundRect(3.5f, 13f, 17f, 7f, 2.5f); line(11f, 7.5f, 17f, 7.5f); line(11f, 16.5f, 17f, 16.5f)
        }
    }
    val Log by lazy { icon("Log") { roundRect(3f, 4f, 18f, 16f, 3f); poly(7f, 9.5f, 10f, 12f, 7f, 14.5f); line(12.5f, 15f, 17f, 15f) } }
    val Plus by lazy { icon("Plus") { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) } }
    val Attach by lazy {
        ImageVector.Builder("Attach", 24.dp, 24.dp, 24f, 24f).apply {
            group(rotate = 40f, pivotX = 12f, pivotY = 12f) {
                path(stroke = SolidColor(Color.Black), strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                    moveTo(15f, 8f); lineTo(15f, 16.5f)
                    arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9f, y1 = 16.5f)
                    lineTo(9f, 6.5f)
                    arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 6.5f)
                    lineTo(12f, 15f)
                }
            }
        }.build()
    }
    val Send by lazy {
        ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3.5f, 11.2f); lineTo(20.5f, 3.5f); lineTo(14f, 20.5f); lineTo(11f, 13f); close() }
        }.build()
    }
    val Search by lazy { icon("Search") { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) } }
    val More by lazy { icon("More", dots = { circle(6f, 12f, 1.6f); circle(12f, 12f, 1.6f); circle(18f, 12f, 1.6f) }) {} }
    val Back by lazy { icon("Back") { poly(14.5f, 5.5f, 8f, 12f, 14.5f, 18.5f) } }
    val Photo by lazy { icon("Photo") { roundRect(3f, 5f, 18f, 14f, 3f); circle(8.5f, 9.5f, 1.5f); poly(3.5f, 17f, 9.5f, 12f, 13.5f, 15.5f, 16.5f, 13f, 20.5f, 16.5f) } }
    val Video by lazy { icon("Video") { roundRect(3f, 6.5f, 12.5f, 11f, 3f); poly(15.5f, 10.5f, 21f, 7.5f, 21f, 16.5f, 15.5f, 13.5f) } }
    val Pin by lazy {
        icon("Pin") {
            moveTo(12f, 21f)
            quadTo(5.5f, 14.5f, 5.5f, 10f)
            arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18.5f, y1 = 10f)
            quadTo(18.5f, 14.5f, 12f, 21f)
            circle(12f, 10f, 2.3f)
        }
    }
    val Data by lazy {
        icon("Data") {
            poly(9f, 4.5f, 7.5f, 4.5f, 6.5f, 5.5f, 6.5f, 10.5f, 4.5f, 12f, 6.5f, 13.5f, 6.5f, 18.5f, 7.5f, 19.5f, 9f, 19.5f)
            poly(15f, 4.5f, 16.5f, 4.5f, 17.5f, 5.5f, 17.5f, 10.5f, 19.5f, 12f, 17.5f, 13.5f, 17.5f, 18.5f, 16.5f, 19.5f, 15f, 19.5f)
        }
    }
    val Moon by lazy {
        icon("Moon") {
            moveTo(12f, 3.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 20.5f, y1 = 12f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3.5f)
            close()
        }
    }
    val Sun by lazy {
        icon("Sun") {
            circle(12f, 12f, 4f)
            for (i in 0 until 8) {
                val a = Math.PI / 4 * i
                line((12 + 7 * Math.cos(a)).toFloat(), (12 + 7 * Math.sin(a)).toFloat(), (12 + 9.5 * Math.cos(a)).toFloat(), (12 + 9.5 * Math.sin(a)).toFloat())
            }
        }
    }
    val LogOut by lazy { icon("LogOut") { poly(10f, 4.5f, 5f, 4.5f, 5f, 19.5f, 10f, 19.5f); line(10f, 12f, 20f, 12f); poly(16f, 8f, 20f, 12f, 16f, 16f) } }
    val Power by lazy {
        icon("Power") {
            moveTo(7.2f, 6.5f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 16.8f, y1 = 6.5f)
            line(12f, 3.5f, 12f, 11f)
        }
    }
    val User by lazy {
        icon("User") {
            circle(12f, 8.5f, 4f)
            moveTo(4.5f, 20f); arcTo(7.5f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19.5f, y1 = 20f)
        }
    }
    val Clock by lazy { icon("Clock") { circle(12f, 12f, 8.5f); poly(12f, 7.5f, 12f, 12f, 15f, 14f) } }
    val ChevronDown by lazy { icon("ChevronDown") { poly(6f, 9.5f, 12f, 15.5f, 18f, 9.5f) } }
    val ChevronRight by lazy { icon("ChevronRight") { poly(9.5f, 6f, 15.5f, 12f, 9.5f, 18f) } }
    val Check by lazy { icon("Check") { poly(5f, 12.5f, 10f, 17.5f, 19f, 7f) } }
    val Close by lazy { icon("Close") { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) } }
    val Folder by lazy { icon("Folder") { poly(3.5f, 6.5f, 3.5f, 18.5f, 20.5f, 18.5f, 20.5f, 8.5f, 11.5f, 8.5f, 9.5f, 5.5f, 3.5f, 5.5f, 3.5f, 6.5f) } }
    val Refresh by lazy {
        icon("Refresh") {
            moveTo(19.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 16.5f, y1 = 6f)
            poly(17f, 2.5f, 16.5f, 6f, 13f, 6.5f)
        }
    }
    val Copy by lazy { icon("Copy") { roundRect(8f, 8f, 12f, 12f, 2.5f); poly(16f, 8f, 16f, 5.5f, 14.5f, 4f, 5.5f, 4f, 4f, 5.5f, 4f, 14.5f, 5.5f, 16f, 8f, 16f) } }
    val Trash by lazy { icon("Trash") { line(4f, 6.5f, 20f, 6.5f); poly(9.5f, 6.5f, 9.5f, 4f, 14.5f, 4f, 14.5f, 6.5f); poly(6f, 6.5f, 7f, 20f, 17f, 20f, 18f, 6.5f) } }
    val Edit by lazy { icon("Edit") { poly(4f, 20f, 4.5f, 16f, 15.5f, 5f, 19f, 8.5f, 8f, 19.5f, 4f, 20f); line(13f, 7.5f, 16.5f, 11f) } }
    val Eye by lazy {
        icon("Eye") {
            moveTo(2.5f, 12f); quadTo(12f, 2f, 21.5f, 12f); quadTo(12f, 22f, 2.5f, 12f); close()
            circle(12f, 12f, 3f)
        }
    }
    val EyeOff by lazy {
        icon("EyeOff") {
            moveTo(2.5f, 12f); quadTo(12f, 2f, 21.5f, 12f); quadTo(12f, 22f, 2.5f, 12f); close()
            line(4f, 4f, 20f, 20f)
        }
    }
    val Info by lazy { icon("Info", dots = { circle(12f, 8f, 1.1f) }) { circle(12f, 12f, 8.5f); line(12f, 11f, 12f, 16.5f) } }

    private const val W = 1.8f

    private fun icon(name: String, dots: (PathBuilder.() -> Unit)? = null, strokes: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = strokes)
            if (dots != null) path(fill = SolidColor(Color.Black), pathBuilder = dots)
        }.build()

    private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1); lineTo(x2, y2)
    }

    private fun PathBuilder.poly(vararg p: Float) {
        moveTo(p[0], p[1])
        for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y); arcTo(r, r, 0f, false, true, x + w, y + r)
        lineTo(x + w, y + h - r); arcTo(r, r, 0f, false, true, x + w - r, y + h)
        lineTo(x + r, y + h); arcTo(r, r, 0f, false, true, x, y + h - r)
        lineTo(x, y + r); arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }
}
