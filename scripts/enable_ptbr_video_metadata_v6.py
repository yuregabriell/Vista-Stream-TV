#!/usr/bin/env python3
"""Safe PT-BR video metadata integration in standalone Vista Stream.
Uses the preexisting (keyless) catalogue, approved manual import flow and
VideoServer fields. Never fetches/scrapes third-party streaming sites.
"""
from pathlib import Path

base=Path("vendor/openstream/app/src/main/java/com/ivor/openstream/harvester")
def once(content,old,new,label):
    n=content.count(old)
    if n!=1:
        raise RuntimeError(f"{label}: expected one match; got {n}")
    return content.replace(old,new,1)

contract=base/"HarvesterContract.kt"
s=contract.read_text()
s=once(s,
    '    const val EXTRA_MATCH_SOURCE_ID = "com.vistastream.tv.extra.MATCH_CATALOG_ID"',
    '''    const val EXTRA_MATCH_SOURCE_ID = "com.vistastream.tv.extra.MATCH_CATALOG_ID"
    const val EXTRA_AUDIO_LANGUAGE = "com.vistastream.tv.extra.AUDIO_LANGUAGE"
    const val EXTRA_AUDIO_MODE = "com.vistastream.tv.extra.AUDIO_MODE"
    const val EXTRA_QUALITY = "com.vistastream.tv.extra.QUALITY"
    const val EXTRA_PROVIDER_LABEL = "com.vistastream.tv.extra.PROVIDER_LABEL"''',
    "declare safe metadata protocol")
s=once(s,
    '''            matchedSourceId = intent.getStringExtra(EXTRA_MATCH_SOURCE_ID)?.take(150).orEmpty()''',
    '''            matchedSourceId = intent.getStringExtra(EXTRA_MATCH_SOURCE_ID)?.take(150).orEmpty(),
            audioLanguage = intent.getStringExtra(EXTRA_AUDIO_LANGUAGE)
                ?.takeIf { it in setOf("pt-BR", "pt-PT", "en", "ja", "es") }.orEmpty(),
            audioMode = intent.getStringExtra(EXTRA_AUDIO_MODE)
                ?.takeIf { it in setOf("dub", "sub", "multi") }.orEmpty(),
            sourceQuality = intent.getStringExtra(EXTRA_QUALITY)
                ?.takeIf { it in setOf("360p","480p","720p","1080p","1440p","2160p") }.orEmpty(),
            sourceProvider = intent.getStringExtra(EXTRA_PROVIDER_LABEL)
                ?.trim()?.take(90)
                ?.takeIf { it.matches(Regex("[a-zA-Z0-9.-]{1,90}")) }
                .orEmpty()''',
    "parse known metadata values only")
s=once(s,
    '''            .putExtra(EXTRA_MATCH_SOURCE_ID, media.matchedSourceId)''',
    '''            .putExtra(EXTRA_MATCH_SOURCE_ID, media.matchedSourceId)
            .putExtra(EXTRA_AUDIO_LANGUAGE, media.audioLanguage)
            .putExtra(EXTRA_AUDIO_MODE, media.audioMode)
            .putExtra(EXTRA_QUALITY, media.sourceQuality)
            .putExtra(EXTRA_PROVIDER_LABEL, media.sourceProvider)''',
    "forward all annotations")
s=once(s,
    '''    val matchedSourceId: String = ""''',
    '''    val matchedSourceId: String = "",
    val audioLanguage: String = "",
    val audioMode: String = "",
    val sourceQuality: String = "",
    val sourceProvider: String = ""''',
    "add backwards compatible serialized metadata")
contract.write_text(s)

store=base/"HarvesterLibraryStore.kt"
s=store.read_text()
s=once(s,
    '''            matchedSourceId = if(media.inCollection)media.matchedSourceId else earlier?.matchedSourceId.orEmpty(),''',
    '''            matchedSourceId = if(media.inCollection)media.matchedSourceId else earlier?.matchedSourceId.orEmpty(),
            audioLanguage = if(media.inCollection)media.audioLanguage else earlier?.audioLanguage.orEmpty(),
            audioMode = if(media.inCollection)media.audioMode else earlier?.audioMode.orEmpty(),
            sourceQuality = if(media.inCollection)media.sourceQuality else earlier?.sourceQuality.orEmpty(),
            sourceProvider = if(media.inCollection)media.sourceProvider else earlier?.sourceProvider.orEmpty(),''',
    "retain user-confirmed annotations")
s=once(s,
    '''                quality = com.ivor.openstream.domain.model.StreamQuality.UNKNOWN,''',
    '''                quality = com.ivor.openstream.domain.model.StreamQuality.parse(item.sourceQuality),
                audio = when(item.audioMode) {
                    "dub" -> com.ivor.openstream.domain.model.StreamAudio.DUB
                    "sub" -> com.ivor.openstream.domain.model.StreamAudio.SUB
                    "multi" -> com.ivor.openstream.domain.model.StreamAudio.MULTI
                    else -> com.ivor.openstream.domain.model.StreamAudio.UNKNOWN
                },
                audioLanguage = item.audioLanguage.takeIf { it.isNotEmpty() },''',
    "translate metadata to preexisting VideoServer fields")
s=once(s,
    '''                name = "Browser · " + item.title,''',
    '''                name = listOfNotNull(
                    "Browser",item.title,
                    item.sourceProvider.takeIf { it.isNotBlank() },
                    item.sourceQuality.takeIf { it.isNotBlank() },
                    when(item.audioMode) {
                        "dub" -> if(item.audioLanguage == "pt-BR") "PT-BR dublado" else "Dublado"
                        "sub" -> "Legendado"
                        "multi" -> "Multi-áudio"
                        else -> null
                    }
                ).joinToString(" · "),''',
    "display Browser and selected language/source")
store.write_text(s)

editor=base/"HarvesterImportActivity.kt"
s=editor.read_text()
s=once(s,
    '''        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)''',
    '''        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)
        layout.addView(TextView(this).apply {
            text="Informações da fonte (sugestões editáveis)"
            textSize=17f
            setPadding(0,18,0,6)
        })
        val audioLabels=arrayOf("Não informado","Dublado","Legendado","Multi-áudio")
        val audioModes=arrayOf("","dub","sub","multi")
        val audioSelector=Spinner(this).apply {
            adapter=ArrayAdapter(this@HarvesterImportActivity,
                android.R.layout.simple_spinner_dropdown_item,audioLabels)
            setSelection(audioModes.indexOf(original.audioMode).coerceAtLeast(0))
        }
        layout.addView(audioSelector)
        val languageLabels=arrayOf("Não informado","Português do Brasil","Português de Portugal",
                                   "Inglês","Japonês","Espanhol")
        val languageCodes=arrayOf("","pt-BR","pt-PT","en","ja","es")
        val languageSelector=Spinner(this).apply {
            adapter=ArrayAdapter(this@HarvesterImportActivity,
                android.R.layout.simple_spinner_dropdown_item,languageLabels)
            setSelection(languageCodes.indexOf(original.audioLanguage).coerceAtLeast(0))
        }
        layout.addView(languageSelector)
        val qualityLabels=arrayOf("Automática","360p","480p","720p","1080p","1440p","2160p")
        val qualitySelector=Spinner(this).apply {
            adapter=ArrayAdapter(this@HarvesterImportActivity,
                android.R.layout.simple_spinner_dropdown_item,qualityLabels)
            setSelection(qualityLabels.indexOf(original.sourceQuality).coerceAtLeast(0))
        }
        layout.addView(qualitySelector)
        val sourceDomain=field("Origem da fonte (opcional)",original.sourceProvider)
''',
    "source annotations user editor")
s=once(s,
    '''                poster=cover,synopsis=synopsis.text.toString().trim().take(2500),''',
    '''                poster=cover,synopsis=synopsis.text.toString().trim().take(2500),
                audioLanguage=languageCodes[languageSelector.selectedItemPosition],
                audioMode=audioModes[audioSelector.selectedItemPosition],
                sourceQuality=qualityLabels[qualitySelector.selectedItemPosition]
                    .takeUnless { it == "Automática" }.orEmpty(),
                sourceProvider=sourceDomain.text.toString().trim().take(90)
                    .takeIf { it.matches(Regex("[a-zA-Z0-9.-]{0,90}")) }.orEmpty(),''',
    "save edited source annotations")
s=once(s,
    '''                .putExtra(HarvesterContract.EXTRA_MATCH_SOURCE_ID,updated.matchedSourceId))''',
    '''                .putExtra(HarvesterContract.EXTRA_MATCH_SOURCE_ID,updated.matchedSourceId)
                .putExtra(HarvesterContract.EXTRA_AUDIO_LANGUAGE,updated.audioLanguage)
                .putExtra(HarvesterContract.EXTRA_AUDIO_MODE,updated.audioMode)
                .putExtra(HarvesterContract.EXTRA_QUALITY,updated.sourceQuality))''',
    "return annotations after confirmed import")
editor.write_text(s)

# Explicit invariants: title matching, no automatic library writes, browser
# remains a separate application, playback never silently saves.
play=base/"HarvesterPlaybackActivity.kt"
assert 'library.save(video)' not in play.read_text(), "Do not restore autosave"
assert 'if (!media.inCollection) return@synchronized' in store.read_text()
assert 'KeylessCatalogClient' in editor.read_text()
assert not (Path("vendor/openstream/app/src/main/java/com/harvester/browser/MainActivity.java")).exists()
print("Vista PT-BR metadata bridge installed with user edit/consent and existing Browser source provider.")
