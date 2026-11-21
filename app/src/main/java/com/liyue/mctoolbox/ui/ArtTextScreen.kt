package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liyue.mctoolbox.map.LiYueActionButton
import com.liyue.mctoolbox.map.PillChip

/**
 * 艺术字转换：把字母和数字换成 Unicode 里的各种字形，游戏内聊天与告示牌都能用。
 * 只替换 ASCII 字母数字，其它字符原样保留。
 */
private data class ArtStyle(
    val label: String,
    val transform: (Char) -> String?,
    /** 需要系统字体里有这些字形，否则预览会变成方块。 */
    val probe: String,
)

private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
private const val DIGITS = "0123456789"

private fun mapped(
    upper: String,
    lower: String,
    digits: String? = null,
): (Char) -> String? = { c ->
    when {
        c in 'A'..'Z' -> upper.getOrNull(c - 'A')?.toString()
        c in 'a'..'z' -> lower.getOrNull(c - 'a')?.toString()
        digits != null && c in '0'..'9' -> digits.getOrNull(c - '0')?.toString()
        else -> null
    }
}

private val SMALL_CAPS_UPPER = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"
private val CIRCLED_UPPER = "ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ"
private val CIRCLED_LOWER = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ"
private val SQUARED_UPPER = "🄰🄱🄲🄳🄴🄵🄶🄷🄸🄹🄺🄻🄼🄽🄾🄿🅀🅁🅂🅃🅄🅅🅆🅇🅈🅉"
private val FULLWIDTH_UPPER = "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ"
private val FULLWIDTH_LOWER = "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ"
private val FULLWIDTH_DIGITS = "０１２３４５６７８９"
private val BOLD_UPPER = "𝐀𝐁𝐂𝐃𝐄𝐅𝐆𝐇𝐈𝐉𝐊𝐋𝐌𝐍𝐎𝐏𝐐𝐑𝐒𝐓𝐔𝐕𝐖𝐗𝐘𝐙"
private val BOLD_LOWER = "𝐚𝐛𝐜𝐝𝐞𝐟𝐠𝐡𝐢𝐣𝐤𝐥𝐦𝐧𝐨𝐩𝐪𝐫𝐬𝐭𝐮𝐯𝐰𝐱𝐲𝐳"
private val BOLD_DIGITS = "𝟎𝟏𝟐𝟑𝟒𝟓𝟔𝟕𝟖𝟗"
private val ITALIC_UPPER = "𝐴𝐵𝐶𝐷𝐸𝐹𝐺𝐻𝐼𝐽𝐾𝐿𝑀𝑁𝑂𝑃𝑄𝑅𝑆𝑇𝑈𝑉𝑊𝑋𝑌𝑍"
private val ITALIC_LOWER = "𝑎𝑏𝑐𝑑𝑒𝑓𝑔ℎ𝑖𝑗𝑘𝑙𝑚𝑛𝑜𝑝𝑞𝑟𝑠𝑡𝑢𝑣𝑤𝑥𝑦𝑧"
private val SCRIPT_UPPER = "𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵"
private val SCRIPT_LOWER = "𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏"
private val MONO_UPPER = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉"
private val MONO_LOWER = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣"
private val MONO_DIGITS = "𝟶𝟷𝟸𝟹𝟺𝟻𝟼𝟽𝟾𝟿"
private val DOUBLE_UPPER = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
private val DOUBLE_LOWER = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"
private val DOUBLE_DIGITS = "𝟘𝟙𝟚𝟛𝟜𝟝𝟞𝟟𝟠𝟡"

private val ART_STYLES = listOf(
    ArtStyle("全角", { c ->
        when {
            c in 'A'..'Z' -> FULLWIDTH_UPPER[c - 'A'].toString()
            c in 'a'..'z' -> FULLWIDTH_LOWER[c - 'a'].toString()
            c in '0'..'9' -> FULLWIDTH_DIGITS[c - '0'].toString()
            c == ' ' -> "　"
            else -> null
        }
    }, "ＡＢＣ０１２"),
    ArtStyle("小体大写", mapped(SMALL_CAPS_UPPER, SMALL_CAPS_UPPER), "ᴀʙᴄᴅ"),
    ArtStyle("圈号", mapped(CIRCLED_UPPER, CIRCLED_LOWER, "⓪①②③④⑤⑥⑦⑧⑨"), "Ⓐⓐ①"),
    ArtStyle("罗马数字", { c ->
        when (c) {
            '1' -> "Ⅰ"; '2' -> "Ⅱ"; '3' -> "Ⅲ"; '4' -> "Ⅳ"; '5' -> "Ⅴ"
            '6' -> "Ⅵ"; '7' -> "Ⅶ"; '8' -> "Ⅷ"; '9' -> "Ⅸ"; '0' -> "〇"
            else -> null
        }
    }, "ⅠⅡⅢ"),
    ArtStyle("带点字母", { c ->
        when {
            c in 'A'..'Z' -> String(Character.toChars(0x1F1E6 + (c - 'A')))
            else -> null
        }
    }, "🇦"),
    ArtStyle("方框", mapped(SQUARED_UPPER, SQUARED_UPPER), "🄰🄱"),
    ArtStyle("粗体", mapped(BOLD_UPPER, BOLD_LOWER, BOLD_DIGITS), "𝐀𝐁"),
    ArtStyle("斜体", mapped(ITALIC_UPPER, ITALIC_LOWER), "𝐴𝐵"),
    ArtStyle("手写体", mapped(SCRIPT_UPPER, SCRIPT_LOWER), "𝒜ℬ"),
    ArtStyle("等宽", mapped(MONO_UPPER, MONO_LOWER, MONO_DIGITS), "𝙰𝙱"),
    ArtStyle("空心", mapped(DOUBLE_UPPER, DOUBLE_LOWER, DOUBLE_DIGITS), "𝔸𝔹"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtTextScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("LiYue 0.63") }
    // 每种风格在本机字体里是否有字形；缺字的预览会变成方块，提前标出来。
    val supported = remember {
        ART_STYLES.map { hasGlyphs(it.probe) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("艺术字转换") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "只替换字母和数字，中文与符号原样保留。带「缺字」的风格在当前系统字体里没有字形，" +
                    "预览会显示成方块，复制到游戏里也未必正常。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(0.dp)),
                placeholder = { Text("输入要转换的文字") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(0.dp),
            )

            Text(
                text = "全部风格（共 ${ART_STYLES.size} 种）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ART_STYLES.forEachIndexed { index, style ->
                val ok = supported[index]
                val output = remember(input, index) {
                    buildString { input.forEach { c -> append(style.transform(c) ?: c.toString()) } }
                }
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (!ok) {
                                Text(
                                    text = "缺字",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                            }
                            Text(
                                text = "复制",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .clickable(enabled = input.isNotBlank()) {
                                        val clip = context.getSystemService(ClipboardManager::class.java)
                                        clip?.setPrimaryClip(ClipData.newPlainText(output, output))
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            text = output.ifBlank { "（先在上面输入文字）" },
                            style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
                            color = if (ok) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("兼容性说明", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Java 版默认字体只有拉丁、希腊、西里尔和中文，数学字母（粗体/斜体/手写/等宽/空心/方框）在游戏里通常显示成方块；" +
                            "基岩版字体覆盖面广一些。想稳妥就用「全角」和「小体大写」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Java 版里按 F3+T 可以刷新字体缓存，粘贴后才看得到效果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

/** 逐个码点问系统字体有没有字形；缺一个就认为这种风格不可靠。 */
private fun hasGlyphs(probe: String): Boolean {
    val paint = android.graphics.Paint()
    paint.typeface = android.graphics.Typeface.DEFAULT
    var i = 0
    while (i < probe.length) {
        val cp = probe.codePointAt(i)
        if (!paint.hasGlyph(String(Character.toChars(cp)))) return false
        i += Character.charCount(cp)
    }
    return true
}
