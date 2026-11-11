# Controle Samsung

Controle remoto **completo e original** para Smart TVs Samsung (Tizen, 2016 em diante), feito em Kotlin + Jetpack Compose.
Sem anúncios, sem login, sem nuvem: o app só conversa com a TV pela rede local.

## Recursos
- Controle: ligar/desligar, fonte, menu/configurações, setas + OK, voltar, início, sair, volume, mudo, canal, guia, info, mídia (play/pause/avançar/retroceder).
- Aba Teclas: números, lista de canais, mídia (stop/gravar), teclas coloridas, Smart Hub, Tools, HDMI, legendas, teletexto, proporção, timer.
- Teclado: abre sozinho no celular quando a TV pede texto (`ms.remote.imeStart`); o texto é enviado em Base64 (`SendInputString`). Também há botão manual.
- Apps: Netflix, YouTube, Prime Video, Disney+, Max, Globoplay, Spotify, Apple TV, Paramount+ e outros; lista os apps instalados na TV quando ela informa.
- Conexão: busca por SSDP + mDNS + varredura da sub-rede, ou IP manual. `wss://IP:8002` (com fallback `ws://IP:8001`), token salvo e reconexão automática.
- Wake-on-LAN (MAC lido de `http://IP:8001/api/v2/`).
- Modo touchpad (arrastar = setas, toque = OK) e vibração nos botões.
- Mensagens de erro claras em português (TV não encontrada, recusada, tempo esgotado, acesso negado...).

## Segurança
- O certificado autoassinado da TV só é aceito para IP literal de rede local (10.x, 172.16–31.x, 192.168.x), travado ao IP informado. Nunca resolve DNS nem aceita hosts públicos.

## Build
```
./gradlew testDebugUnitTest assembleRelease
```
A assinatura lê `/workspace/controle-samsung-keystore/keystore.properties` (fora do repositório).

## Testes
- `SamsungProtocolTest`: montagem/leitura de mensagens (teclas, texto Base64, lançar app, WoL, eventos).
- `LocalNetTest`: IP local, broadcast, mensagens de erro, escolha de ID de app.
- `UiSmokeTest` (Robolectric): todas as telas compõem sem crash.
- `tools/faketv/fake.py` + `FAKETV=1`: simula uma TV (REST + WebSocket 8001/8002) para teste ponta a ponta do cliente.

## Limitações honestas
- **Não foi testado em uma TV real** (só contra um simulador de protocolo).
- Modelos de 2016–2017 podem aceitar só `ws://8001`; de 2018+ exigem `wss://8002` com aprovação na TV.
- A lista de apps instalados e alguns IDs de app variam por modelo/ano; se um atalho não abrir, o app avisa.
- Ligar a TV pela rede (Wake-on-LAN) depende da opção "Ligar com Wi-Fi/Ethernet" da TV e do modelo.
- O teclado automático depende de a TV enviar `ms.remote.imeStart` (nem todo campo de texto/app envia).
- Projeto independente, não oficial; sem marcas ou logos da Samsung.
