# Catálogo e fontes — Vista Stream TV

## Catálogo de títulos
A Home e pesquisa de filmes/séries/animes são baseadas em TMDB. **TMDB não fornece vídeos**. Por padrão este fork não distribui um segredo/API key de terceiros. Para criar uma chave TMDB v3 gratuita (uso não comercial, com atribuição), acede a https://www.themoviedb.org/settings/api, regista uma chave na tua conta e cola no ecrã «Catálogo indisponível». A chave v4 (bearer) não é aceite neste formulário. Não partilhes tokens pessoais em repositórios.

## Loja de extensões
A app inclui as extensões declarativas OpenStream e uma loja com pesquisa, filtragem por compatibilidade, versões, instalação/remoção, e adição manual de URLs de repositórios. Em Android TV, usa «Fontes» no menu lateral e «Adicionar fonte» para introduzir o URL com teclado do sistema.

A app permite importar **metadados** de repositórios CloudStream (campos `pluginLists`, `internalName`, `tvTypes`). A biblioteca exibe esses plugins assinalados como **incompatíveis**. Não executa nem instala binários `.cs3`/`.jar`. Um verdadeiro adaptador CloudStream exige implementar e testar o ABI/plugin runtime, compatibilizar dependências e analisar licenças e segurança antes de executar código de terceiros.

- OpenStream: catálogo incluído e atualizações do repositório.
- Stremio: inserir URL `https://.../manifest.json` de add-on que forneça streams diretos e seja tecnicamente compatível.
- CloudStream oficial: https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json — dados de plugins, não binários executáveis.
- CloudStream comunidade: https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/repo.json — apenas metadados de plugins.
- SaimoPlayer / GoAnime: código Swift/Go não é carregável como plugin Kotlin; precisa de adaptação; a existência de fontes públicas não implica direitos sobre vídeos.

Uma loja não consegue indexar literalmente todas as extensões da Internet com garantias de funcionamento. A pesquisa cobre os repositórios importados e os catálogos que estes publicam, atualizados quando o utilizador sincroniza.

## Direitos e qualidade
A app não hospeda filmes, séries ou animes; não contorna DRM, autenticação ou anúncios exigidos pelos fornecedores. Filtros de idioma PT-BR e indicações de dublagem dependem das informações da fonte; os títulos podem não estar disponíveis em Portugal. Evita distribuir fontes que disponibilizem obras sem autorização.

## Builds
Releases publicam APKs de desenvolvimento depois de compilações bem-sucedidas. Cada compilação usa uma assinatura temporária, de modo que novas versões podem exigir reinstalação até existir uma chave persistente.
