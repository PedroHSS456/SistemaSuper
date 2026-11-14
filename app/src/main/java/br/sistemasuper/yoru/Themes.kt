package br.sistemasuper.yoru

class Palette(
    val name: String,
    val skyTop: Int,
    val skyMid: Int,
    val skyBottom: Int,
    val accent: Int,
    val accent2: Int,
    val text: Int,
    val textDim: Int,
    val panel: Int,
    val building: Int,
    val buildingFar: Int,
    val window: Int
)

object Themes {
    private fun c(rgb: Int) = rgb or (0xFF shl 24)

    val all: List<Palette> = listOf(
        Palette(
            "Shibuya Chuvosa",
            c(0x0B0820), c(0x1B1340), c(0x3A1F5C),
            c(0x35F0D8), c(0xFF4FA3),
            c(0xF4F0FF), c(0xB8AEDB), c(0x15102E),
            c(0x0A0716), c(0x1A1236), c(0xFFD479)
        ),
        Palette(
            "Sakura Madrugada",
            c(0x1E1033), c(0x4A2457), c(0xC4607F),
            c(0xFFB7D5), c(0xFF7FA8),
            c(0xFFF4F8), c(0xE6BFD0), c(0x2A1330),
            c(0x180A22), c(0x40204F), c(0xFFE2A8)
        ),
        Palette(
            "Matcha Lo-fi",
            c(0x0C1D1A), c(0x16352F), c(0x3D6B54),
            c(0xB6F2A0), c(0xF2E6A0),
            c(0xF1FFF3), c(0xA9CDB5), c(0x0F2420),
            c(0x07130F), c(0x15332B), c(0xFFE59A)
        ),
        Palette(
            "Casa de Cha",
            c(0x23140F), c(0x5A3320), c(0xD9843F),
            c(0xFFC27A), c(0xFF8A5B),
            c(0xFFF5E6), c(0xE0C3A0), c(0x2E1A12),
            c(0x1A0E09), c(0x4A2A1C), c(0xFFD27A)
        ),
        Palette(
            "Gato Laranja",
            c(0x081426), c(0x10304A), c(0x1E5A7A),
            c(0xFF9A3D), c(0x4FE3FF),
            c(0xFFF7EE), c(0xB7CBD9), c(0x0B1C2E),
            c(0x050C16), c(0x0F2A40), c(0xFFC46B)
        )
    )

    fun current(): Palette = all[Prefs.theme.coerceIn(0, all.size - 1)]
}
