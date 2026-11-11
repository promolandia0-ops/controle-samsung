package br.geracaoai.controlesamsung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.geracaoai.controlesamsung.net.ConnState
import br.geracaoai.controlesamsung.ui.AppTheme
import br.geracaoai.controlesamsung.ui.AppsPage
import br.geracaoai.controlesamsung.ui.C
import br.geracaoai.controlesamsung.ui.ConnectScreen
import br.geracaoai.controlesamsung.ui.ControlPage
import br.geracaoai.controlesamsung.ui.Haptics
import br.geracaoai.controlesamsung.ui.KeyboardOverlay
import br.geracaoai.controlesamsung.ui.KeysPage
import br.geracaoai.controlesamsung.ui.SettingsPage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { Root(vm) } }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val ctx = LocalContext.current
    val state by vm.client.state.collectAsStateWithLifecycle()
    val tvs by vm.tvs.collectAsStateWithLifecycle()
    val discovering by vm.discovering.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val poweringOn by vm.poweringOn.collectAsStateWithLifecycle()
    val kb by vm.keyboard.collectAsStateWithLifecycle()
    val apps by vm.client.installedApps.collectAsStateWithLifecycle()
    val launching by vm.launching.collectAsStateWithLifecycle()
    val hapticsOn by vm.haptics.collectAsStateWithLifecycle()
    val autoKb by vm.autoKeyboard.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = remember { Haptics(ctx) }
    haptics.enabled = hapticsOn
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.messages.collect { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) vm.onForeground() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val inMain = state is ConnState.Connected || state is ConnState.Reconnecting
    Box(Modifier.fillMaxSize().background(C.bg)) {
        if (!inMain) {
            Scaffold(containerColor = C.bg, snackbarHost = { SnackbarHost(snack) }) { pad ->
                Box(Modifier.padding(pad)) {
                    ConnectScreen(
                        state = state, tvs = tvs, discovering = discovering, phase = phase, poweringOn = poweringOn,
                        savedIp = vm.savedIp, savedName = vm.savedName,
                        onSearch = vm::startDiscovery, onConnectTv = vm::connectTo, onConnectIp = vm::connect,
                        onPowerOn = vm::powerOn, onForget = vm::forget,
                    )
                }
            }
        } else {
            val ip = (state as? ConnState.Connected)?.ip ?: vm.savedIp
            Scaffold(
                containerColor = C.bg,
                snackbarHost = { SnackbarHost(snack) },
                topBar = {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (state is ConnState.Connected) C.ok else C.warn))
                        Text("  " + (vm.savedName ?: "TV Samsung"), color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (state is ConnState.Reconnecting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.warn)
                            Text("  Reconectando…", color = C.warn, fontSize = 13.sp)
                        } else Text(ip.orEmpty(), color = C.textDim, fontSize = 13.sp)
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = C.surface) {
                        val items = listOf("Controle" to Icons.Filled.SettingsRemote, "Teclas" to Icons.Filled.Dialpad, "Apps" to Icons.Filled.Apps, "Ajustes" to Icons.Filled.Tune)
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Color_White, selectedTextColor = C.text, indicatorColor = C.accent, unselectedIconColor = C.textDim, unselectedTextColor = C.textDim),
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    val tick = { haptics.tick() }
                    when (tab) {
                        0 -> ControlPage(send = vm::key, onKeyboard = vm::openKeyboard, onPower = vm::power, tick = tick)
                        1 -> KeysPage(send = vm::key, tick = tick)
                        2 -> AppsPage(apps, launching, vm::launch, vm::launchInstalled, vm.client::refreshApps, tick)
                        else -> SettingsPage(
                            tvName = vm.savedName, ip = ip, secure = (state as? ConnState.Connected)?.secure,
                            haptics = hapticsOn, autoKeyboard = autoKb, poweringOn = poweringOn,
                            onHaptics = vm::setHaptics, onAutoKeyboard = vm::setAutoKeyboard,
                            onPowerOn = { vm.powerOn(ip) }, onReconnect = { ip?.let(vm::connect) },
                            onDisconnect = vm::disconnect, onForget = vm::forget,
                        )
                    }
                }
            }
            kb?.let { KeyboardOverlay(it, onText = vm::sendText, onEnter = vm::enter, onClose = vm::closeKeyboard) }
        }
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
