package com.liyue.mctoolbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.liyue.mctoolbox.R

/** 像素字体：Zpix（OFL），11px 网格，中英文都能对齐到方块。 */
val PixelFont = FontFamily(Font(R.font.zpix, weight = FontWeight.Normal))

/**
 * 像素风排版：全部走 PixelFont，字号收到 4 的倍数附近，
 * 让字落在点阵格上，不用负字距（点阵字体一旦被拉近就会糊）。
 */
val LiYueTypography = Typography().let { base ->
    fun TextStyle.px(weight: FontWeight = FontWeight.Normal, size: Int = 0, line: Int = 0) =
        copy(
            fontFamily = PixelFont,
            fontWeight = weight,
            letterSpacing = 0.sp,
            fontSize = if (size > 0) size.sp else fontSize,
            lineHeight = if (line > 0) line.sp else lineHeight,
        )

    base.copy(
        displaySmall = base.displaySmall.px(FontWeight.Normal, 34, 40),
        headlineLarge = base.headlineLarge.px(FontWeight.Normal, 30, 36),
        headlineMedium = base.headlineMedium.px(FontWeight.Normal, 24, 32),
        headlineSmall = base.headlineSmall.px(FontWeight.Normal, 20, 28),
        titleLarge = base.titleLarge.px(FontWeight.Normal, 18, 26),
        titleMedium = base.titleMedium.px(FontWeight.Normal, 16, 24),
        titleSmall = base.titleSmall.px(FontWeight.Normal, 14, 22),
        bodyLarge = base.bodyLarge.px(FontWeight.Normal, 14, 24),
        bodyMedium = base.bodyMedium.px(FontWeight.Normal, 13, 22),
        bodySmall = base.bodySmall.px(FontWeight.Normal, 12, 20),
        labelLarge = base.labelLarge.px(FontWeight.Normal, 13, 20),
        labelMedium = base.labelMedium.px(FontWeight.Normal, 12, 18),
        labelSmall = base.labelSmall.px(FontWeight.Normal, 11, 16),
    )
}

/** 种子、坐标、版本号：同样是像素字体，只是不参与排版缩放。 */
val MonoStyle: TextStyle = TextStyle(
    fontFamily = PixelFont,
    fontSize = 13.sp,
    letterSpacing = 0.sp,
)
