# Catálogo Stremio a partir de fontes autorizadas

Este exemplo cria um **add-on Stremio estático** para a aba Explorar do VistaStream, sem hospedar código remoto ou descarregar ficheiros de vídeo. Pode transformar 1, 300 ou milhares de registos de um catálogo JSON em manifestos e respostas Stremio.

## Entrada

Cria um ficheiro `media.json` (UTF-8) com um array de filmes e séries. Exemplo de dados **ilustrativos** (a origem da fonte é fictícia; não é reproduzível):

```json
[
  {
    "id": "tt1234567",
    "type": "series",
    "name": "Série de demonstração",
    "poster": "https://example.org/poster.jpg",
    "rights": "Distribuição autorizada pelo titular",
    "permissionConfirmed": true,
    "episodes": [
      {
        "season": 1,
        "episode": 1,
        "name": "Episódio 1",
        "streams": [
          {
            "infoHash": "0123456789abcdef0123456789abcdef01234567",
            "fileIdx": 0,
            "name": "Fonte PT-BR",
            "language": "PT-BR"
          }
        ]
      }
    ]
  }
]
```

Alternativamente, `streams` aceita `url` HTTP(S) direto (MP4/HLS), em vez de `infoHash`. No torrent, `fileIdx` é zero-based segundo o protocolo Stremio. Se não houver `fileIdx`, o motor P2P deve escolher o ficheiro apropriado através dos metadados. **Não uses o hash fictício acima para testar reprodução.**

## Gerar os endpoints

```bash
node examples/stremio-torrent-catalog/build.mjs media.json dist/
```

O resultado inclui:

- `dist/manifest.json`
- `dist/catalog/series/vista-authorized-pt.json` e/ou `catalog/movie/...`
- `dist/meta/series/tt1234567.json`
- `dist/stream/series/tt1234567:1:1.json`

Hospeda a pasta `dist/` num servidor estático HTTPS ou GitHub Pages e importa `https://teu-dominio/manifest.json` em **VistaStream → Extensões → Adicionar repositório**. Para disponibilizar a extensão publicamente em Explorar, adiciona a URL e os metadados ao ficheiro `extensions/vista-catalog.json`, com `engine.type = stremio`.

## Como transformar uma listagem de 300 títulos

1. Obtém os metadados da fonte **com permissão** (API oficial, RSS permitido, exportação do próprio autor ou outro meio autorizado).
2. Normaliza os títulos e associa cada filme/episódio a IMDb `tt...` ou TMDB `tmdb:123`. Essa associação evita que a extensão devolva o episódio errado.
3. Guarda, para cada fonte, a URL HTTP(S) direta ou o `infoHash` válido, idioma, qualidade e opcionalmente `fileIdx`.
4. Confirma os direitos de distribuição, regista-os em `rights` e marca `permissionConfirmed: true` apenas quando verificados.
5. Gera o add-on, publica os ficheiros e testa `/stream/...` com os mesmos IDs que o VistaStream usa.

**Limitação atual:** instalar uma extensão adiciona *fontes* aos títulos já identificados no VistaStream; isso não injeta automaticamente os 300 títulos do `catalog` no ecrã inicial do aplicativo. Essa importação do catálogo Stremio para o catálogo Vista requer uma integração separada. O manifesto por si só não faz scraping, não obtém trackers e não inicia torrents.

## Transporte P2P

O VistaStream usa um TorrServer opt-in para converter `infoHash` em vídeo HTTP reproduzível no Media3. Um possível fallback nativo para estudo é `libtorrent4j`, mas requer implementação NDK, seleção sequencial dos blocos, gestão de espaço, um servidor HTTP Range local e testes por arquitetura Android. **Este exemplo não instala nem incorpora um segundo motor P2P.**
