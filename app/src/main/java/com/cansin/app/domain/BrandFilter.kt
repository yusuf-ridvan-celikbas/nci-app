package com.cansin.app.domain

/**
 * P36: the app does not keep alcohol brand names (user decision; Turkish alcohol advertising rules + store policy).
 * Free text (Tab notes, own game questions) is cleaned: "Belfast Bira" → "Bira", "Yeni Rakı" → "Rakı".
 * This list is never shown to users. It is not complete; common words and first names are left out on purpose
 * (e.g. "Efe", "Kulüp", "Mercan", "Sevilen", "Altınbaş", "Amsterdam", "Marmara", "Corona", "Miller", "Tiger") to avoid wrecking normal text.
 */
object BrandFilter {
    /** Brand words removed wherever they appear as a whole word. */
    private val BRANDS = listOf(
        // Beer
        "Efes Pilsen", "Efes Malt", "Efes", "Tuborg", "Belfast", "Bomonti", "Carlsberg", "Heineken", "Guinness",
        "Beck's", "Becks", "Budweiser", "Stella Artois", "Leffe", "Hoegaarden", "Grimbergen", "Desperados", "Peroni",
        "Kozel", "Pilsner Urquell", "Asahi", "Sapporo", "Kirin", "Duvel", "Chimay", "Kronenbourg", "San Miguel",
        "Estrella Damm", "Amstel", "Paulaner", "Erdinger", "Weihenstephaner", "Franziskaner", "Warsteiner", "Bitburger",
        "Krombacher", "Löwenbräu", "Spaten", "Hofbräu", "Coors", "Carlsberg Special", "Gusta",
        // Rakı
        "Sarı Zeybek",
        // Wine
        "Kavaklıdere", "Doluca", "Vinkara", "Turasan", "Kayra Şarapları",
        // Spirits
        "Johnnie Walker", "Jack Daniel's", "Jack Daniels", "Chivas Regal", "Chivas", "Jameson", "Absolut", "Smirnoff",
        "Grey Goose", "Bacardi", "Captain Morgan", "Jägermeister", "Jagermeister", "Baileys", "Glenfiddich", "Ballantine's",
        "Ballantines", "J&B", "Havana Club", "Olmeca", "Jose Cuervo", "Hennessy", "Martell", "Rémy Martin", "Beefeater",
        "Bombay Sapphire", "Gordon's", "Tanqueray", "Hendrick's",
    )

    /** Brand words that are also ordinary words: removed only right before "rakı/rakısı". */
    private val RAKI_ONLY = listOf("Yeni", "Tekirdağ", "Beylerbeyi", "Kulüp", "Efe", "Mercan", "Burgaz", "İzmir", "Altınbaş")

    private val brandRegex: List<Regex> = BRANDS.sortedByDescending { it.length }.map { name ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name).replace(" ", "\\E\\s+\\Q") + "(?![\\p{L}\\p{N}])", setOf(RegexOption.IGNORE_CASE))
    }
    private val rakiRegex: List<Regex> = RAKI_ONLY.map { word ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?=\\s+rak[ıiIİ])", setOf(RegexOption.IGNORE_CASE))
    }

    /** Text with brand names removed and spaces tidied; may be empty if it was only a brand. */
    fun strip(text: String): String {
        var t = text
        brandRegex.forEach { t = it.replace(t, "") }
        rakiRegex.forEach { t = it.replace(t, "") }
        return t.replace(Regex("[ \\t]{2,}"), " ").replace(Regex("\\s+([,.;:!?])"), "$1").trim()
    }

    fun containsBrand(text: String): Boolean = strip(text) != text.replace(Regex("[ \\t]{2,}"), " ").trim()
}
