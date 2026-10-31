# VistaStream Web — iPhone / Safari

Progressive Web App (PWA) do VistaStream, sem instalação IPA, App Store, Mac ou assinatura Apple.

## Usar no iPhone

1. Abrir a URL do site no **Safari**.
2. Tocar em **Partilhar → Adicionar ao ecrã principal**.
3. Selecionar **Abrir como aplicação** quando disponível.

A PWA é pública e gratuita, mas os **dados da biblioteca e as fontes ficam no armazenamento local do próprio iPhone**.

## Funcionalidades v0.2.0

- Interface reconstruída segundo o app Android: cor violeta Material 3, modo AMOLED, carrossel de posters no topo, seletor Filmes & Séries | Animes e navegação Fontes.
- Gestão de extensões Stremio HTTPS por `manifest.json`: Explorar, Instaladas e Repositórios, catálogos e descoberta de fontes por filme/episódio com seleção no ecrã do título.
- Filmes e séries: metadados públicos **Cinemeta** (o mesmo fallback de catálogo usado no VS Android) e **TVmaze** se necessário.
- Animes: metadados públicos **AniList**.
- Pesquisa, detalhes, episódios e temporadas (quando o serviço fornece).
- Favoritos, biblioteca local, histórico e posição de reprodução guardada no dispositivo.
- Player HTML5 com `playsinline`; Safari suporta normalmente MP4/H.264 e HLS compatíveis, **não** todos os formatos do Android.
- Importação do **Vista Source v1** `manifest.json` exportado do VistaBrowser ou VistaStream Android.
- Adição manual de endereços HTTPS de media autorizados.
- Exportação/importação de backup local.
- App shell offline via service worker (catálogo e stream exigem rede).

## Limitações importantes

- O catálogo contém **metadados, não vídeos**. Reproduz apenas URLs diretos autorizados e válidos adicionados pelo utilizador. Fontes com autenticação, DRM, cookies, CORS, HTTP misto ou formatos incompatíveis podem falhar em Safari.
- O **Vista ID cloud** e o emparelhamento Wi-Fi da app Android não estão integrados na v0.1.0. Não existe sincronização automática entre TV, Android e iPhone; usa temporariamente importação/exportação de fontes e backups.
- Não guardar senhas, cookies, chaves TMDB, tokens privados, ou manifests pessoais no repositório ou no GitHub Pages. **NUNCA** publicar ficheiros de fontes pessoais na pasta `web/`.
- O modo offline guarda apenas os ficheiros estáticos da aplicação, não vídeos nem dados pessoais.
- Os dados podem desaparecer ao limpar os dados do Safari; faz backup da biblioteca.

## Como publicar

O workflow `.github/workflows/vistastream-web-pages.yml` valida e publica `web/` via GitHub Pages ao atualizar `main`.
Na primeira publicação, escolher **Settings → Pages → Source: GitHub Actions**.

A URL padrão esperada é `https://yuregabriell.github.io/Vista-Stream-TV/` após a publicação bem-sucedida. Não assumir que está online até o workflow e a página confirmarem.

## Fontes de dados e licenças

- [Cinemeta](https://v3-cinemeta.strem.io/) — metadados públicos de filmes/séries.
- [AniList](https://anilist.co/) — API GraphQL de metadados de animes, sujeita às condições de utilização.
- [TVmaze](https://www.tvmaze.com/api) — fallback de séries; API licenciada CC BY-SA, com atribuição no rodapé.

Nenhum conteúdo audiovisual é hospedado pelo VistaStream Web. Usa apenas fontes para as quais tens autorização.
