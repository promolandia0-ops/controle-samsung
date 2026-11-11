package br.geracaoai.controlesamsung.data

import androidx.compose.ui.graphics.Color
import br.geracaoai.controlesamsung.protocol.InstalledApp

/**
 * Atalho de app de streaming. IDs conhecidos do Tizen (mudam conforme ano/modelo da TV):
 * o app tenta primeiro o ID real da lista de apps instalados (por nome) e depois cada ID conhecido.
 * Cores e iniciais são nossas (nada de logos/marcas).
 */
data class StreamingApp(
    val key: String,
    val label: String,
    val ids: List<String>,
    val keywords: List<String>,
    val color: Color,
    val initials: String,
)

object StreamingApps {
    val all: List<StreamingApp> = listOf(
        StreamingApp("netflix", "Netflix", listOf("11101200001", "3201907018807"), listOf("netflix"), Color(0xFFB81D24), "N"),
        StreamingApp("youtube", "YouTube", listOf("111299001912"), listOf("youtube"), Color(0xFFE02B20), "▶"),
        StreamingApp("prime", "Prime Video", listOf("3201512006785", "3201910019365"), listOf("prime video", "primevideo"), Color(0xFF1399FF), "P"),
        StreamingApp("disney", "Disney+", listOf("3201901017640", "3202009021709", "3202204027038"), listOf("disney"), Color(0xFF113CCF), "D+"),
        StreamingApp("max", "Max", listOf("3202301029760", "3201601007230"), listOf("max", "hbo"), Color(0xFF5822B4), "M"),
        StreamingApp("globoplay", "Globoplay", listOf("3201908019022"), listOf("globoplay"), Color(0xFFEE4B2B), "G"),
        StreamingApp("spotify", "Spotify", listOf("3201606009684"), listOf("spotify"), Color(0xFF1DB954), "S"),
        StreamingApp("appletv", "Apple TV", listOf("3201807016597"), listOf("apple tv"), Color(0xFF3A3A3C), "tv"),
        StreamingApp("paramount", "Paramount+", listOf("3201710014981", "3202110025305"), listOf("paramount"), Color(0xFF0064FF), "P+"),
        StreamingApp("telecine", "Telecine", listOf("3201604009182"), listOf("telecine"), Color(0xFFD62828), "T"),
        StreamingApp("pluto", "Pluto TV", listOf("3201808016802"), listOf("pluto"), Color(0xFFF2C300), "Pl"),
        StreamingApp("tiktok", "TikTok", listOf("3202008021577"), listOf("tiktok"), Color(0xFF111111), "Tk"),
        StreamingApp("twitch", "Twitch", listOf("3202203026841"), listOf("twitch"), Color(0xFF9146FF), "Tw"),
        StreamingApp("deezer", "Deezer", listOf("3201608010191"), listOf("deezer"), Color(0xFF8E3FE0), "Dz"),
        StreamingApp("plex", "Plex", listOf("3201512006963"), listOf("plex"), Color(0xFFE5A00D), "Px"),
        StreamingApp("browser", "Internet", listOf("org.tizen.browser", "3201907018784", "3202010022079"), listOf("internet", "browser"), Color(0xFF2F6FED), "@"),
    )

    /** Procura o app instalado que corresponde ao atalho (por ID conhecido, depois por nome). */
    fun match(app: StreamingApp, installed: List<InstalledApp>): InstalledApp? {
        installed.firstOrNull { it.appId in app.ids }?.let { return it }
        return installed.firstOrNull { inst ->
            val n = inst.name.lowercase()
            app.keywords.any { k -> n == k || n.startsWith(k) || (k.length > 4 && n.contains(k)) }
        }
    }

    /** Ordem de tentativas de ID: o ID instalado (se achado) primeiro, depois os conhecidos. */
    fun launchOrder(app: StreamingApp, installed: List<InstalledApp>): List<String> {
        val found = match(app, installed)?.appId
        return (listOfNotNull(found) + app.ids).distinct()
    }
}
