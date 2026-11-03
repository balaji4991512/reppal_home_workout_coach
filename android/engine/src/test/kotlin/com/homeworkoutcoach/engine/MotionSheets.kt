package com.homeworkoutcoach.engine

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws every motion as a contact sheet (a few moments of each), for checking the figures by eye. Only runs when the
 * MOTION_SHEETS environment variable names a folder to write into.
 */
object MotionSheets {
    private val lib = Library.default
    private const val SCALE = 1.1f
    private val cellW = ((Figure.MAX_X - Figure.MIN_X) * SCALE).toInt()
    private val cellH = ((Figure.MAX_Y - Figure.MIN_Y) * SCALE).toInt()
    private val moments = listOf(0f, 0.3f, 0.5f, 0.8f)

    fun draw(dir: File, ids: List<String> = Motions.all.keys.toList(), perSheet: Int = 8) {
        dir.mkdirs()
        ids.chunked(perSheet).forEachIndexed { sheet, chunk ->
            val img = BufferedImage(cellW * moments.size + 120, cellH * chunk.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, img.width, img.height)
            chunk.forEachIndexed { row, id ->
                val e = lib.exercise(id)
                val m = Motions.of(id) ?: return@forEachIndexed
                g.color = Color.BLACK
                g.font = Font("SansSerif", Font.PLAIN, 13)
                g.drawString(id, 6, row * cellH + 20)
                moments.forEachIndexed { col, t ->
                    val ox = 120 + col * cellW
                    val oy = row * cellH
                    g.color = Color(0xEE, 0xEE, 0xEE)
                    g.drawRect(ox, oy, cellW - 1, cellH - 1)
                    fun px(p: Pt) = Pair(ox + (p.x - Figure.MIN_X) * SCALE, oy + (Figure.MAX_Y - p.y) * SCALE)
                    // floor
                    g.color = Color(0xBB, 0xBB, 0xBB)
                    val (fx1, fy) = px(Pt(Figure.MIN_X, 0f))
                    val (fx2, _) = px(Pt(Figure.MAX_X, 0f))
                    g.draw(Line2D.Float(fx1, fy, fx2, fy))
                    for (s in Figure.shapes(m, m.poseAt(t), (e.primary + e.secondary).toSet())) {
                        g.color = when (val part = s.part) {
                            Part.Far -> Color(0xC9, 0xC6, 0xD6)
                            Part.Near, Part.Torso, Part.Head -> Color(0x8E, 0x8A, 0xA6)
                            Part.Prop -> Color(0xB8, 0xA0, 0x84)
                            Part.Dumbbell -> Color(0x33, 0x33, 0x33)
                            is Part.Worked -> if (part.muscle in e.primary) Color(0xE8, 0x5D, 0x4A) else Color(0xF4, 0xB0, 0x8E)
                        }
                        when (s) {
                            is Shape.Line -> {
                                g.stroke = BasicStroke(s.width * SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                                val (ax, ay) = px(s.a)
                                val (bx, by) = px(s.b)
                                g.draw(Line2D.Float(ax, ay, bx, by))
                            }
                            is Shape.Dot -> {
                                val (cx, cy) = px(s.c)
                                val r = s.r * SCALE
                                g.fill(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r))
                            }
                        }
                    }
                    g.stroke = BasicStroke(1f)
                }
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "motions-${sheet + 1}.png"))
        }
    }
}
