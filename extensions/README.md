# Catálogo Vivo de Addons Vista

Este JSON é consumido por **VistaStream** diretamente do GitHub; não precisa recompilar a APK para acrescentar, corrigir ou retirar entradas.

- Ficheiro: [vista-catalog.json](./vista-catalog.json)
- URL de leitura: `https://raw.githubusercontent.com/yuregabriell/Vista-Stream-TV/main/extensions/vista-catalog.json`
- O app consulta o catálogo ao abrir a loja, permite atualização manual e verifica alterações enquanto a página estiver visível.
- O ficheiro é **um catálogo de metadados**, não contém vídeo, APK, código executável, passwords ou tokens.
- Uma entrada só funciona como fonte se declarar um engine já suportado pelo app; um manifesto Stremio de `stream` com URL HTTPS pode usar `type: "stremio"`.
- A instalação/ativação é uma decisão do utilizador. Uma fonte disponível na loja não significa que tenha streams reproduzíveis.

## Exemplo de entrada

```json
{
  "id": "exemplo-documental",
  "name": "Exemplo de addon",
  "description": "Fonte documental pública hipotética — substituir por dados verificados.",
  "version": "1.0.0",
  "versionCode": 1,
  "language": "pt-PT",
  "contentTypes": ["movie", "series"],
  "titleExamples": ["Exemplo de título"],
  "manifestUrl": "https://example.org/manifest.json",
  "tags": ["stremio", "documentary"],
  "authors": ["Autor verificado"],
  "homepage": "https://example.org",
  "installedByDefault": false,
  "engine": {
    "type": "stremio",
    "endpoint": "https://example.org",
    "priority": 40
  }
}
```

Esse exemplo é apenas documentação e **não** foi inserido na lista efetiva.

## Revisão antes de publicar

Confirmar o URL HTTPS, manifesto (recursos/tipos/ID), direitos ou condições de utilização, idioma declarado, conteúdo realmente suportado, endpoints de configuração, e que não existam chaves pessoais ou URLs de sessão. Alterações ficam no histórico de commits do GitHub; se o servidor falhar, o VistaStream retém o último catálogo válido em disco.

## Limites

A atualização é feita por consultas periódicas (não por ligação push em tempo real): novas entradas tornam-se visíveis ao abrir a loja, ao tocar em Atualizar ou na próxima consulta periódica, sujeitas a rede e cache do GitHub. Addons com recursos `catalog`/`meta` sem `stream` aparecem apenas como referência: essa loja não os torna players compatíveis magicamente.
