# Harvester Browser ↔ Vista Stream: integração Android v1

## Âmbito

O Vista Stream é o player **externo oficial** do Harvester Browser no Android. A integração é independente da Android TV/launcher; a futura unificação num só app fica para outra etapa.

Fluxo: Harvester deteta uma fonte **já identificada** pelo utilizador → oferece *Reproduzir no Vista Stream* → Android abre `HarvesterPlaybackActivity` → Media3 reproduz diretamente pela Internet → Vista guarda o link no histórico **Vídeos do Harvester** → utilizador pode continuar/retomar sem voltar ao navegador.

Não se descarrega o filme inteiro. O player aceita meios que o Media3 consiga decodificar: ficheiros diretos e playlists compatíveis, nomeadamente MP4, WebM, MKV, HLS (`.m3u8`) e MPEG-DASH (`.mpd`). O formato real, codecs, token temporário, DRM e controlo do servidor determinam se toca.

## Contrato público

- **Ação:** `com.vistastream.tv.action.PLAY`
- **Atividade:** `com.ivor.openstream.harvester.HarvesterPlaybackActivity`
- **Pacote APK das builds de desenvolvimento:** `com.vistastream.tv.debug`
- **Pacote do projeto principal sem suffix debug:** `com.vistastream.tv`

| Chave Intent (String) | Obrigatório? | Utilização |
| --- | --- | --- |
| `com.vistastream.tv.extra.URL` | Sim | URL `https://` ou `http://` de um vídeo/manifesto; `content://` com permissão temporária |
| `com.vistastream.tv.extra.TITLE` | Opcional | Nome para player e biblioteca |
| `com.vistastream.tv.extra.MIME_TYPE` | Opcional | Formato real: `video/mp4`, `video/x-matroska`, `application/vnd.apple.mpegurl`, `application/dash+xml` |
| `com.vistastream.tv.extra.REFERER` | Opcional | Cabeçalho `Referer` se a origem exigir |
| `com.vistastream.tv.extra.USER_AGENT` | Opcional | Cabeçalho `User-Agent` |
| `com.vistastream.tv.extra.PAGE_URL` | Opcional | Página original; apenas metadado |
| `com.vistastream.tv.extra.SUBTITLE_URL` | Opcional | URL de legendas `https://` ou `http://` |
| `com.vistastream.tv.extra.SUBTITLE_LANGUAGE` | Opcional | `pt-BR`, `pt-PT`, `en` etc. |

Exemplo para o projeto Harvester Browser (Kotlin):

```kotlin
fun openInVistaStream(
    context: Context,
    streamUrl: String,
    title: String,
    pageUrl: String? = null,
    mimeType: String? = null,
    subtitleUrl: String? = null,
) {
    val packages = listOf("com.vistastream.tv.debug", "com.vistastream.tv")
    val pm = context.packageManager
    val target = packages.firstOrNull { name ->
        runCatching { pm.getPackageInfo(name, 0) }.isSuccess
    } ?: return // Mostrar "Instalar Vista Stream" ou fallback para player local

    val intent = Intent("com.vistastream.tv.action.PLAY").apply {
        setClassName(target, "com.ivor.openstream.harvester.HarvesterPlaybackActivity")
        putExtra("com.vistastream.tv.extra.URL", streamUrl)
        putExtra("com.vistastream.tv.extra.TITLE", title)
        putExtra("com.vistastream.tv.extra.MIME_TYPE", mimeType)
        putExtra("com.vistastream.tv.extra.PAGE_URL", pageUrl)
        putExtra("com.vistastream.tv.extra.REFERER", pageUrl)
        putExtra("com.vistastream.tv.extra.SUBTITLE_URL", subtitleUrl)
        putExtra("com.vistastream.tv.extra.SUBTITLE_LANGUAGE", "pt-BR")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Avisar que o Vista não está instalado ou abrir player interno.
    }
}
```

**Android 11+ (visibilidade de apps):** para usar `getPackageInfo` com um pacote externo, acrescentar ao `AndroidManifest.xml` do **Harvester**, fora de `<application>`:

```xml
<queries>
    <package android:name="com.vistastream.tv.debug"/>
    <package android:name="com.vistastream.tv"/>
</queries>
```

O Harvester só deve enviar **o stream identificado**, não o URL genérico da página Web. Se houver várias fontes, mostrar uma escolha antes de abrir o Vista. Se não encontrar link direto, manter a opção do seu WebView/player próprio; o Vista não faz scraping nem desbloqueia DRM por si só.

## Outras formas de abertura

O Vista também regista `ACTION_VIEW` para URIs `http/https/content` com MIME de vídeo, e `ACTION_SEND` de vídeo. A ação específica PLAY é preferida porque transporta título, Referer e metadados corretamente e não regista o player como interceptador de todas as páginas da Web.

Ao enviar `content://`, o Harvester deve passar `FLAG_GRANT_READ_URI_PERMISSION`. URLs `file://`, `javascript:` e páginas genéricas de partilha de texto não são aceites; isto evita abrir HTML como se fosse vídeo.

## Segurança e limitações

- Nenhum token de conta do navegador é partilhado. O Vista aceita apenas `Referer` e `User-Agent` por Intent, não cookies, Authorization, nem executar JavaScript da página original.
- Links guardados podem expirar; o Harvester deverá oferecer reabrir a página para resolver um novo URL se necessário.
- Os dados de biblioteca ficam em `SharedPreferences` na instalação do Vista, não no Harvester e não num servidor remoto.
- O progresso de reprodução é guardado pelo mesmo player Media3 usado no catálogo.
- Não há comunicação remota, conta partilhada, sincronização bidirecional nem Chromecast nesta primeira integração entre apps.
- Não é necessária nenhuma API de catálogo nem chave TMDB para receber streams.

## Teste manual

1. Instalar a APK do Vista e selecionar um MP4 público que possa ser reproduzido legitimamente.
2. Enviar um Intent PLAY a partir de outra app; o Vista deve iniciar horizontalmente com o título correto.
3. Pausar, selecionar legendas/qualidade (quando houver faixas), regressar à biblioteca e abrir o cartão de vídeo.
4. Confirmar progresso, remover cartão, testar HLS, DASH, título sem MIME e link expirado.
5. Confirmar que URL de página HTML genérica e esquemas inválidos são recusados e não são guardados.
