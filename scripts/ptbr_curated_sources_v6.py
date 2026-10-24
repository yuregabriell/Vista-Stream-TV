#!/usr/bin/env python3
"""Accept BR media labels from standalone Harvester and show them as curated
Browser sources in Vista Stream. Sites are never scraped by Vista.
"""
from pathlib import Path
root=Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
base=root/"harvester"

def one(text,old,new,label):
    n=text.count(old)
    if n!=1:raise RuntimeError(f"{label}: found {n} matches")
    return text.replace(old,new,1)

contract=base/"HarvesterContract.kt"
s=contract.read_text()
s=one(s,
    '    const val EXTRA_MATCH_SOURCE_ID = "com.vistastream.tv.extra.MATCH_CATALOG_ID"',
    '''    const val EXTRA_MATCH_SOURCE_ID = "com.vistastream.tv.extra.MATCH_CATALOG_ID"
    const val EXTRA_AUDIO_LANGUAGE = "com.vistastream.tv.extra.AUDIO_LANGUAGE"
    const val EXTRA_AUDIO_MODE = "com.vistastream.tv.extra.AUDIO_MODE"
    const val EXTRA_QUALITY = "com.vistastream.tv.extra.QUALITY"
    const val EXTRA_PROVIDER_LABEL = "com.vistastream.tv.extra.PROVIDER_LABEL"''',
    "public metadata protocol")
s=one(s,
    '''            matchedSourceId = intent.getStringExtra(EXTRA_MATCH_SOURCE_ID)?.take(150).orEmpty()''',
    '''            matchedSourceId = intent.getStringExtra(EXTRA_MATCH_SOURCE_ID)?.take(150).orEmpty(),
            audioLanguage = intent.getStringExtra(EXTRA_AUDIO_LANGUAGE)
                ?.take(15)?.takeIf { it.matches(Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,4})?")) }.orEmpty(),
            audioMode = intent.getStringExtra(EXTRA_AUDIO_MODE)
                ?.takeIf { it in setOf("dub","sub","multi","raw") }.orEmpty(),
            qualityLabel = intent.getStringExtra(EXTRA_QUALITY)
                ?.take(18)?.takeIf { it.matches(Regex("(?i)(2160|1440|1080|720|480|360)p|4k|hd")) }.orEmpty(),
            providerLabel = intent.getStringExtra(EXTRA_PROVIDER_LABEL)
                ?.take(90)?.takeIf { it.matches(Regex("[a-zA-Z0-9._-]+")) }.orEmpty()''',
    "parse vetted extras")
s=one(s,
    '''            .putExtra(EXTRA_MATCH_SOURCE_ID, media.matchedSourceId)''',
    '''            .putExtra(EXTRA_MATCH_SOURCE_ID, media.matchedSourceId)
            .putExtra(EXTRA_AUDIO_LANGUAGE, media.audioLanguage)
            .putExtra(EXTRA_AUDIO_MODE, media.audioMode)
            .putExtra(EXTRA_QUALITY, media.qualityLabel)
            .putExtra(EXTRA_PROVIDER_LABEL, media.providerLabel)''',
    "forward metadata")
s=one(s,
    '''    val matchedSourceId: String = ""''',
    '''    val matchedSourceId: String = "",
    val audioLanguage: String = "",
    val audioMode: String = "",
    val qualityLabel: String = "",
    val providerLabel: String = ""''',
    "serializable media fields")
contract.write_text(s)

store=base/"HarvesterLibraryStore.kt"
s=store.read_text()
s=one(s,
    '''            matchedSourceId = if(media.inCollection)media.matchedSourceId else earlier?.matchedSourceId.orEmpty(),''',
    '''            matchedSourceId = if(media.inCollection)media.matchedSourceId else earlier?.matchedSourceId.orEmpty(),
            audioLanguage = if(media.inCollection)media.audioLanguage else earlier?.audioLanguage.orEmpty(),
            audioMode = if(media.inCollection)media.audioMode else earlier?.audioMode.orEmpty(),
            qualityLabel = if(media.inCollection)media.qualityLabel else earlier?.qualityLabel.orEmpty(),
            providerLabel = if(media.inCollection)media.providerLabel else earlier?.providerLabel.orEmpty(),''',
    "persist curated source labels")
s=one(s,
    '''                quality = com.ivor.openstream.domain.model.StreamQuality.UNKNOWN,''',
    '''                quality = com.ivor.openstream.domain.model.StreamQuality.parse(item.qualityLabel),
                audio = when(item.audioMode) {
                    "dub" -> com.ivor.openstream.domain.model.StreamAudio.DUB
                    "sub" -> com.ivor.openstream.domain.model.StreamAudio.SUB
                    "multi" -> com.ivor.openstream.domain.model.StreamAudio.MULTI
                    "raw" -> com.ivor.openstream.domain.model.StreamAudio.RAW
                    else -> com.ivor.openstream.domain.model.StreamAudio.UNKNOWN
                },
                audioLanguage = item.audioLanguage.ifBlank { null },''',
    "resolve source with PT-BR audio and quality")
s=one(s,
    '''                name = "Browser · " + item.title,''',
    '''                name = listOf(
                    "Browser",
                    when(item.audioMode) {
                        "dub" -> "Dublado"
                        "sub" -> "Legendado"
                        "multi" -> "Multiáudio"
                        else -> null
                    },
                    item.audioLanguage.ifBlank { null },
                    item.qualityLabel.ifBlank { null }
                ).filterNotNull().joinToString(" · "),''',
    "source list labels")
store.write_text(s)

editor=base/"HarvesterImportActivity.kt"
s=editor.read_text()
key='''        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)'''
if key not in s:raise RuntimeError("metadata dialog marker missing")
s=s.replace(key,key+'''
        // All guesses are optional/editable; do not assume Portuguese dubbing
        // merely because the source website or title is Brazilian.
        val languages = arrayOf("Não identificado","Português (Brasil)","Inglês","Outro")
        val language = Spinner(this).apply {
            adapter=ArrayAdapter(this@HarvesterImportActivity,
                android.R.layout.simple_spinner_dropdown_item,languages)
            setSelection(when(original.audioLanguage.lowercase()) {
                "pt-br" -> 1
                "en","en-us","en-gb" -> 2
                else -> 0
            })
        }
        layout.addView(TextView(this).apply { text="Idioma do áudio (quando conhecido)" })
        layout.addView(language)
        val modes = arrayOf("Não identificado","Dublado","Legendado","Multiáudio","Original")
        val audioMode = Spinner(this).apply {
            adapter=ArrayAdapter(this@HarvesterImportActivity,
                android.R.layout.simple_spinner_dropdown_item,modes)
            setSelection(when(original.audioMode) {
                "dub"->1;"sub"->2;"multi"->3;"raw"->4;else->0
            })
        }
        layout.addView(TextView(this).apply { text="Versão da fonte" })
        layout.addView(audioMode)
        val quality=field("Qualidade (360p, 720p, 1080p, 4K…)",original.qualityLabel)
''',1)
old='''                inCollection=true,
                matchedTmdbId='''
if old not in s:raise RuntimeError("import save marker")
s=s.replace(old,'''                inCollection=true,
                audioLanguage=when(language.selectedItemPosition){
                    1->"pt-BR";2->"en";else->original.audioLanguage.takeIf{
                        language.selectedItemPosition==3
                    }.orEmpty()
                },
                audioMode=when(audioMode.selectedItemPosition){
                    1->"dub";2->"sub";3->"multi";4->"raw";else->""
                },
                qualityLabel=quality.text.toString().trim().take(18).takeIf {
                    it.matches(Regex("(?i)(2160|1440|1080|720|480|360)p|4k|hd"))
                }.orEmpty(),
                matchedTmdbId=''',1)
editor.write_text(s)
print("Vista v0.8: curated Browser source supports PT-BR audio, dubbing, quality and series episode hints.")
