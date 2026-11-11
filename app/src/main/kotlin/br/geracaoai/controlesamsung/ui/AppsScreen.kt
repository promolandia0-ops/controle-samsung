package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.geracaoai.controlesamsung.data.StreamingApp
import br.geracaoai.controlesamsung.data.StreamingApps
import br.geracaoai.controlesamsung.protocol.InstalledApp

@Composable
fun AppsPage(
    installed: List<InstalledApp>?, launching: String?,
    onLaunch: (StreamingApp) -> Unit, onLaunchInstalled: (InstalledApp) -> Unit, onRefresh: () -> Unit, tick: () -> Unit,
) {
    val matchedIds = installed?.let { list -> StreamingApps.all.mapNotNull { StreamingApps.match(it, list)?.appId }.toSet() } ?: emptySet()
    val others = installed?.filter { it.appId !in matchedIds }?.sortedBy { it.name.lowercase() } ?: emptyList()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(3) }) {
            Text(
                if (launching != null) "Abrindo $launching…" else "Toque para abrir direto na TV",
                color = C.textDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(StreamingApps.all, key = { it.key }) { app ->
            val isInstalled = installed == null || app.key in StreamingApps.all.filter { a -> StreamingApps.match(a, installed) != null }.map { it.key }
            AppTile(app, dim = !isInstalled) { tick(); onLaunch(app) }
        }
        item(span = { GridItemSpan(3) }) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Outros apps instalados na TV")
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClickLabel = "Atualizar lista de apps") { onRefresh() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Refresh, null, tint = C.textDim)
                }
            }
        }
        if (installed == null) {
            item(span = { GridItemSpan(3) }) {
                Text("Esta TV ainda não enviou a lista de apps (alguns modelos não enviam). Os atalhos acima usam IDs conhecidos e funcionam mesmo assim, se o app estiver instalado.", color = C.textDim, fontSize = 13.sp)
            }
        } else if (others.isEmpty()) {
            item(span = { GridItemSpan(3) }) { Text("Nenhum outro app listado.", color = C.textDim, fontSize = 13.sp) }
        } else {
            items(others, key = { it.appId }) { a ->
                Box(
                    Modifier.aspectRatio(1.35f).clip(RoundedCornerShape(18.dp)).background(C.surface).clickable { tick(); onLaunchInstalled(a) }.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(a.name, color = C.text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
        item(span = { GridItemSpan(3) }) { Box(Modifier.size(12.dp)) }
    }
}

@Composable
private fun AppTile(app: StreamingApp, dim: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClickLabel = "Abrir ${app.label}") { onClick() }.alpha(if (dim) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(20.dp)).background(app.color), contentAlignment = Alignment.Center) {
            Text(app.initials, color = androidx.compose.ui.graphics.Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text(app.label, color = C.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
