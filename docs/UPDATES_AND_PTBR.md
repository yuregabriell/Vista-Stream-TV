# Vista Stream — fontes PT-BR e atualizações

## AIOStreams PT-BR
O post https://www.reddit.com/r/StremioAddons/comments/1u9mp5t/ partilha uma configuração de AIOStreams, e não um manifesto universal pronto. Pode exigir API Debrid do utilizador.
Na app: Fontes → Repositórios → Configurar AIOStreams PT-BR. Depois de criar a configuração, colar o URL HTTPS manifest.json resultante na caixa de importação. Não partilhar tokens com terceiros.

## Fontes discutidas no Reddit
- Filmes e séries dublados: AIOStreams, CDFlix BR, FenixFlix.
- Animes: FrostStream, FenixFlix e listas de AniList para metadados.
- Legendas PT-BR: OpenSubtitles v3, Community Subtitles. Nem todo addon de legendas é executável pelo atual motor de fontes de vídeo do Vista.
Estas são referências da comunidade, sem garantias de disponibilidade, funcionamento ou licenças dos títulos. A app não contorna DRM.

## Atualizações pelo GitHub
A app consulta automaticamente até 100 Releases, incluindo prereleases, ordena pela etiqueta build.N e compara com o número incluído na APK. Ao existir uma nova versão, o utilizador aceita o download, recebe a APK e confirma manualmente no instalador Android. Não há instalação silenciosa.

## Assinatura estável necessária
Para atualizar sem desinstalar, duas APKs precisam do mesmo certificado. Builds antigas tinham assinatura temporária. Configurar uma keystore privada no GitHub → Settings → Secrets and variables → Actions:
- VISTA_SIGNING_KEYSTORE_BASE64 — ficheiro keystore codificado em base64.
- VISTA_KEYSTORE_PASSWORD — palavra-passe do ficheiro.
- VISTA_KEY_ALIAS — alias da chave.
- VISTA_KEY_PASSWORD — palavra-passe da chave.
O workflow usa estes segredos para assinar as próximas builds com a mesma chave. Não colocar a keystore ou passwords no repositório. Fazer backup local seguro da keystore. Uma migração inicial pode exigir desinstalar a build antiga e perder dados locais, pelo que é importante criar backup antes.
Sem esses segredos, o aviso e download funcionam, mas o Android pode recusar a instalação por cima da versão anterior por incompatibilidade de assinatura.