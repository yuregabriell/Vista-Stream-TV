# Integração BR: Harvester Browser + Vista Stream

## O que está efetivamente implementado neste build

O Vista Stream continua separado do Harvester Browser. O Harvester 2.7 envia
*somente metadados inferidos dos títulos que já apareceram no navegador*:
idioma indicado, dublado/legendado/multiáudio, resolução e temporada/episódio.
Não são emitidas requisições HTTP novas por este parser.

No Vista Stream, o diálogo de importação abre de forma automática a pesquisa
Cinemeta/AniList previamente implementada; **a associação e a gravação só
acontecem após confirmação do utilizador**. Também é possível corrigir
idioma, áudio e resolução antes de guardar. O resultado é uma fonte local
«Browser» no filme ou episódio selecionado (quando há uma fonte realmente
reproduzível e uma associação confirmada).

Estas informações são sugestões extraídas de texto: não garantem que a
faixa efetivamente existente seja dublada, legendada ou 1080p.

## Como funciona a compatibilidade com os repositórios externos

O Vista Stream já possui `ExtensionRepoClient`, `ExtensionIndexParser` e
`ExtensionProviderRegistry`. O parser consegue **importar os metadados
públicos** de uma lista CloudStream; o tipo de motor para ficheiros
`.cs3` / `.jar` é `UNSUPPORTED` e não os executa no Vista.

As seguintes referências foram verificadas em 24/10/2026:

| Fonte | Índice / código | Compatibilidade atual |
|---|---|---|
| LietRepo | https://raw.githubusercontent.com/lawlietbr/lietrepo/main/builds/repo.json | Diretório de metadados CloudStream; sem execução de CS3 |
| SaimuelRepo | https://raw.githubusercontent.com/saimuelbr/saimuelrepo/main/builds/repo.json | Diretório de metadados CloudStream; sem execução de CS3 |
| Yūzōnō | https://github.com/yuzono/anime-extensions/tree/master/src/pt | Código-fonte Aniyomi, exige adaptador próprio |
| GuIndex | https://github.com/GuickerZ/guindex | Referência de indexação Stremio PT-BR e nomes; não é extrator HTTP simples |

**A existência de plugins num índice não significa que estejam atualmente
operacionais, licenciados para redistribuição ou autorizados a reproduzir
qualquer obra.** No LietRepo existe referência à licença MIT no README, mas
o repositório verificado não continha um ficheiro LICENSE no tree principal;
confirmar os direitos de cada módulo antes de incorporar o seu código.

## Como portar um provedor para Vista Stream

1. Obter permissão e confirmar que o conteúdo/serviço é acessível de forma
   autorizada. Preferir APIs públicas e documentação do serviço.
2. Criar um adaptador de `StreamProvider.resolve(MediaIdentity)` que devolva
   `VideoServer` com `quality`, `audio`, `audioLanguage`, `subtitles`
   e `headers` necessários, **sem recolher cookies de terceiros**.
3. Registar um motor no `ExtensionProviderRegistry` e na
   `ExtensionEngineType`, com estado desativado até haver testes reais.
4. Respeitar limites de taxa, `robots.txt` quando aplicável e respostas
   de erro. Não varrer domínios nem descodificar DRM ou ultrapassar
   medidas de acesso.
5. Para séries/animes, guardar o identificador do catálogo e
   `season` / `episode` explicitamente. Evitar associar por nome sozinho.
6. Acrescentar testes unitários de idioma, episódio, links expirados e
   regressões antes de publicar uma nova extensão.

O Harvester Browser **não precisa de executar extensões CloudStream**.
O seu papel é observar a navegação do utilizador, oferecer um botão de
importação voluntário e enviar a fonte para o Vista Stream via
`com.vistastream.tv.action.IMPORT`.

## Pendentes

- Portagem **individual** de provedores com autorização e endpoints
  estáveis: não há nenhum provedor CloudStream BR executável neste build.
- Reprodutibilidade de fontes temporárias/assinadas em Google Cast.
- Validação funcional dos dois APKs num Android e num Chromecast reais.
- Migração persistente de metadados entre dispositivos: a biblioteca
  importada atualmente é local à instalação do Vista Stream.
