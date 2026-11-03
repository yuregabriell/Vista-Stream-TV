# VistaStream — catálogo e reprodução (2026-11-03)

## Fluxo principal

1. **Catálogo:** TMDB e AniList alimentam a Home e as páginas de títulos já existentes. O marketplace contém manifestos Stremio; catálogos de add-ons precisam ainda de importação explícita e reconciliada por ID.
2. **Identidade:** o VistaStream associa TMDB/IMDb ao filme, série, temporada e episódio; os fornecedores nunca devem substituir silenciosamente um episódio diferente.
3. **Fontes instaladas:** cada add-on opt-in pode devolver HTTP(S), HLS, DASH, URL de página, `infoHash` ou ficheiros de legenda. O catálogo remoto não instala add-ons por conta própria.
4. **Resolução HTTP:** Media3 reproduz URLs diretos. `externalUrl` só vira vídeo quando o caminho é explícito (`.mp4`, `.m3u8`, etc), não em páginas web.
5. **Resolução torrent:** `infoHash` é entregue à camada P2P selecionada:
   - **Motor nativo Vista (experimental):** adaptador Java/Kotlin baseado em Nuvio Engine 0.1.6 (GPLv3), que inicia um servidor HTTP Range apenas em `127.0.0.1` e entrega o stream ao Media3. *Não* envia esse endereço diretamente ao Chromecast.
   - **TorrServer:** motor externo no aparelho ou na rede (opcional), compatível com a reprodução/Chromecast quando acessível pela TV.
6. **Player VistaStream:** Media3 com áudio, qualidade, legendas e as integrações de Cast existentes. O motor nativo não contorna DRM e não fornece mídia que não esteja legalmente disponível.

## Limites da versão experimental

- Os dois motores ficam opt-in e nunca são iniciados pela simples instalação da extensão.
- Para evitar sobrecarga, o adaptador prepara no máximo **um** torrent nativo por pesquisa de add-on, com timeout de 16 segundos. Fontes restantes não são bloqueadas por um motor sem peers.
- O Nuvio Engine trabalha com ficheiros selecionados por `fileIdx` Stremio ou `behaviorHints.filename`, suportando seleção de episódio.
- O motor nativo é uma implementação upstream **ainda experimental**. Esta versão contém apenas um adaptador Vista, não um reescrito independentemente do código C++. Implementar um motor integralmente novo é um projeto maior, requer testes de rede, cache, sequenciamento, permissões e licenças.
- Cast de torrents nativos exige um proxy HTTP LAN com autenticação e controles de sessão. Um URL de loopback não é acessível pelo Chromecast.
- Integração dos catálogos Stremio na Home e nas abas Filmes/Séries/Animes não está concluída; será necessário normalizar IMDb/TMDB/AniList/Kitsu e evitar duplicados.
- A presença de um manifesto online não garante vídeo, peers, idioma de áudio ou autorização de distribuição.

## Verificação da integração

O ficheiro `scripts/prepare-native-engine.sh` descarrega a release Android 0.1.6 com SHA-256 fixado; executar antes de compilar localmente. O CI faz isto automaticamente. Não armazenar chaves, cookies, torrents privados nem dados de sessão nos manifestos.

A distribuição do APK com Nuvio Engine está sob GPLv3, enquanto os componentes MIT anteriores mantêm as suas licenças; consultar `LICENSE` e `THIRD_PARTY_NOTICES.md`.

## Limpeza automática por vídeo (build seguinte à 393)

Ao parar a sessão na TV ou fechar o mini-player no telemóvel, o VistaStream espera o Media3 e o Cast soltarem a ligação, invoca `stopStream()` e `removeTorrent()` no Nuvio Engine e elimina apenas o diretório `app.cacheDir/vista-p2p-pieces/payload/<hash>` do torrent desativado. Os URLs HTTP loopback removidos são igualmente eliminados, um a um, das chaves do cache Media3. Ao mudar de fonte ou episódio, preserva-se o torrent ainda em reprodução; sair do ecrã no telemóvel com o mini-player ativo **não** elimina o stream.

A rotina nunca limpa a cache global, capas, metadados, legendas, histórico ou dados permanentes. Usa hashes validados e caminhos canónicos para impedir a eliminação de diretórios estranhos ao P2P. O teto de cache em disco continua a ser 128 MiB como proteção adicional. Não gere o cache de motores TorrServer externos. Se o Android matar abruptamente o processo sem executar o encerramento, a limpeza imediata não está garantida; a pasta continua na área de cache descartável do Android.
