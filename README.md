# Vista Stream TV

App Android de filmes, séries e animes para uso com fontes autorizadas e bibliotecas pessoais.

**Base visual e arquitetura:** [OpenStream](https://github.com/Ivorisnoob/OpenStream), MIT, © 2026 Ivor.
**Código Go para estudo/adaptação:** [GoAnime](https://github.com/alvarorichard/GoAnime), MIT, © 2023 Krone.

O workflow `.github/workflows/android-tv-release.yml` faz a importação literal, com versões fixadas, de OpenStream e GoAnime para `vendor/`, personaliza uma cópia do aplicativo OpenStream com a identidade Vista Stream e tenta gerar um APK universal em **GitHub Releases**.

## Fontes e instalação manual

O motor importado do OpenStream suporta fontes/extensões por repositórios JSON e add-ons compatíveis com Stremio, configuráveis dentro da app. Não executa nativamente pacotes CloudStream `.cs3` nem executáveis Go. Fontes IPTV M3U locais e playlists para reprodução direta constituem um adaptador separado ainda em desenvolvimento.

O catálogo que uma extensão anuncia não comprova o direito de distribuição dos vídeos, nem a presença de dublagem/legendas PT-BR ou ausência de anúncios. Usa apenas fontes para as quais tens autorização.

## Entrega

A página de instalação é [Releases](https://github.com/yuregabriell/Vista-Stream-TV/releases). O APK só aparece depois de **build bem-sucedida**. Se falhar, consulta [Actions](https://github.com/yuregabriell/Vista-Stream-TV/actions).

## Documentação

Consulta [COMPATIBILITY.md](COMPATIBILITY.md) e [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Nota: esta versão é uma integração inicial literal baseada no projeto Android do OpenStream. O código GoAnime é vendorizado, mas o executável Go **não corre dentro do Android**. O repositório
`GoAnime · Fontes PT-BR` é agora registado como diretório atualizável, com os adaptadores Android
experimentais AnimeFire e Goyabu (ativação manual). Não é uma conversão completa dos scrapers GoAnime.

## Importação e exportação em lote

Em **Extensões → Instaladas** ou **Extensões → Repositórios**, usa **Exportar em lote** para
guardar `vistastream-extensoes.json`. O ficheiro inclui URLs de repositórios personalizados,
extensões instaladas, estados ativados/desativados e ordem de pesquisa. **Importar em lote**
restaura o backup sem apagar outras extensões e mostra entradas que não foi possível restaurar.

O JSON não contém código executável nem vídeos. **Atenção:** URLs Stremio personalizados podem
incluir chaves de serviços; trata o ficheiro como privado e não o publiques no GitHub.
O diretório GoAnime é fornecido em:
`https://raw.githubusercontent.com/yuregabriell/Vista-Stream-TV/main/extensions/goanime-catalog.json`.
