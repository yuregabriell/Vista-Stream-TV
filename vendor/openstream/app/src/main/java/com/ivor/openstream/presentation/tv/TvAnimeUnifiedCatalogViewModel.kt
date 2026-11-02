package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.catalog.AniListEditorialClient
import com.ivor.openstream.data.catalog.AnimeEditorialFeed
import com.ivor.openstream.data.catalog.AnimeEditorialItem
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The TV Anime section and the general TV search share TMDB identities.
 * AniList media IDs do not resolve to TV episodes or the main stream sources.
 * This dedicated TV feed uses TMDB discover/search, but retains anime rails.
 */
data class TvAnimeUnifiedState(
    val sections: Map<String, List<KeylessTitle>> = emptyMap(),
    val editorialRails: Map<String, List<KeylessTitle>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val browseItems: List<KeylessTitle> = emptyList(),
    val browseType: String = "anime",
    val browseGenre: String = "",
    val browseQuery: String = "",
    val browseLoading: Boolean = false,
    val browseError: String? = null,
    val browseHasMore: Boolean = true
)

@HiltViewModel
class TvAnimeUnifiedCatalogViewModel @Inject constructor(
    private val tmdb: TmdbApi,
    private val aniList: AniListEditorialClient
) : ViewModel() {
    private val _state = MutableStateFlow(TvAnimeUnifiedState())
    val state: StateFlow<TvAnimeUnifiedState> = _state
    private var refreshedAt = 0L
    private var refreshJob: Job? = null
    private var browseJob: Job? = null
    private var browsePage = 0
    private var browseHasMore = true
    // Back navigation retains the exact anime card/rail rather than
    // returning focus to the featured hero or the sidebar.
    var lastFocusedAnimeId: String? = null
        private set
    var lastFocusedRail: String? = null
        private set
    fun rememberFocusedAnime(id: String, rail: String) {
        lastFocusedAnimeId = id
        lastFocusedRail = rail
    }
    fun clearFocusedAnime() {
        lastFocusedAnimeId = null
        lastFocusedRail = null
    }
    private var editorialGenres: Map<String, List<KeylessTitle>> = emptyMap()
    private var resolvedEditorial: List<Pair<AnimeEditorialItem, KeylessTitle>> = emptyList()

    init { refresh() }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - refreshedAt > 3L * 60 * 60 * 1_000L &&
            refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                // Trending and genre rails are ranked server-side on TMDB. AniList
                // may inspire their names, but must never provide a different ID.
                // Seven small requests every 3h; cover art and details use the
                // same canonical IDs as the global TV search.
                val feeds: Map<String, List<KeylessTitle>> = coroutineScope {
                    listOf(
                        async { "Em alta no mundo dos animes" to trending() },
                        async { "Novidades e estreias" to discover("tv", 1, sort = "first_air_date.desc", recent = true) },
                        async { "Ação e aventura" to discover("tv", 1, "Action") },
                        async { "Fantasia e outros mundos" to discover("tv", 1, "Fantasy") },
                        async { "Romance e emoções" to discover("tv", 1, "Romance") },
                        async { "Filmes de anime" to discover("movie", 1) },
                        async { "_filmes_anime_2" to discover("movie", 2) },
                        async { "_filmes_anime_3" to discover("movie", 3) },
                        async { "_mais_populares" to discover("tv", 1) },
                        async { "_mais_populares_2" to discover("tv", 2) }
                    ).mapNotNull { task -> runCatching { task.await() }.getOrNull() }
                        .toMap()
                }
                val collection = feeds.values.flatten().distinctBy { it.sourceId }
                if (collection.isNotEmpty()) {
                    refreshedAt = System.currentTimeMillis()
                    // Keep existing rails if a partial network failure happened.
                    val rails = linkedMapOf<String, List<KeylessTitle>>()
                    for (name in listOf(
                        "Em alta no mundo dos animes", "Novidades e estreias",
                        "Ação e aventura", "Fantasia e outros mundos",
                        "Romance e emoções", "Filmes de anime"
                    )) {
                        val found = feeds[name].orEmpty()
                        rails[name] = if (found.isNotEmpty()) found
                            else _state.value.editorialRails[name].orEmpty()
                    }
                    // TMDB weekly trending is GLOBAL and often contains only
                    // one or two Japanese animations after filtering. Backfill
                    // with popularity-ranked Japanese animation, not with
                    // unrelated trending live-action titles. Preserve true
                    // weekly trending at the front of this rail.
                    // Multiple movie pages ensure the cinema rail is populated
                    // beyond the few AniList movies that happen to match.
                    val movieCatalog = (feeds["Filmes de anime"].orEmpty() +
                        feeds["_filmes_anime_2"].orEmpty() +
                        feeds["_filmes_anime_3"].orEmpty())
                        .distinctBy { it.sourceId }
                    if (movieCatalog.isNotEmpty()) rails["Filmes de anime"] = movieCatalog
                    val popular = (feeds["_mais_populares"].orEmpty() +
                        feeds["_mais_populares_2"].orEmpty())
                        .distinctBy { it.sourceId }
                    val trendingAnime = (feeds["Em alta no mundo dos animes"].orEmpty() +
                        popular + feeds["Filmes de anime"].orEmpty())
                        .distinctBy { it.sourceId }.take(36)
                    if (trendingAnime.isNotEmpty())
                        rails["Em alta no mundo dos animes"] = trendingAnime
                    _state.value = _state.value.copy(
                        sections = mapOf("anime" to (collection + popular).distinctBy { it.sourceId }),
                        editorialRails = rails,
                        loading = false, error = null
                    )
                    // Display the canonical TMDB feed immediately. AniList
                    // overlays only order/genre after identity-safe matching.
                    runCatching { applyEditorial(collection + popular) }
                } else {
                    _state.value = _state.value.copy(loading = false,
                        error = "Não foi possível carregar o catálogo de anime do TMDB.")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false,
                    error = "Falha ao carregar o catálogo de anime.")
            }
        }
    }

    /**
     * AniList supplies editorial order and genuine anime genres, but never
     * supplies artwork, episode details or a playback ID. Every card below is
     * an existing or safely resolved canonical TMDB animation record.
     */
    private fun previousMovieRail(): List<KeylessTitle> =
        _state.value.editorialRails["Filmes de anime"].orEmpty()

    private suspend fun applyEditorial(base: List<KeylessTitle>) {
        val feed = aniList.get() ?: return
        val index = base.distinctBy { it.sourceId }.toMutableList()
        val ranked = (feed.trending + feed.popular + feed.newReleases)
            .distinctBy { it.anilistId }
        val resolved = linkedMapOf<Int, KeylessTitle>()
        val aliases = linkedMapOf<String, KeylessTitle>()
        fun indexTitle(title: KeylessTitle) {
            aliases["${title.format}:${editorialKey(title.title)}"] = title
            title.alternateTitle?.let {
                aliases["${title.format}:${editorialKey(it)}"] = title
            }
        }
        index.forEach(::indexTitle)

        fun lookup(item: AnimeEditorialItem): KeylessTitle? {
            val format = if (item.isMovie) "MOVIE" else "TV"
            return item.names.firstNotNullOfOrNull { name ->
                aliases["$format:${editorialKey(name)}"]
            }
        }

        // Resolve only a modest number of missing top-ranked AniList records
        // using the same TMDB search as general search. 3 concurrent max.
        // Exact title + Japanese original language + animation genre required.
        val targets = ranked.filter { lookup(it) == null }
            .take(18)
        val gate = Semaphore(3)
        val discovered = coroutineScope {
            targets.map { item ->
                async {
                    gate.withPermit { item to resolveEditorialTitle(item) }
                }
            }.mapNotNull { task ->
                runCatching { task.await() }.getOrNull()
            }
        }
        discovered.forEach { (_, title) ->
            if (title != null) {
                index.add(title)
                indexTitle(title)
            }
        }
        ranked.forEach { item -> lookup(item)?.let { resolved[item.anilistId] = it } }
        if (resolved.isEmpty()) return

        fun list(items: List<AnimeEditorialItem>): List<KeylessTitle> =
            items.mapNotNull { resolved[it.anilistId] }
                .distinctBy { it.sourceId }.take(36)
        val ranking = (feed.trending + feed.popular).distinctBy { it.anilistId }
        val trends = list(ranking)
        val premieres = list(feed.newReleases)
        val editorials = ranked.mapNotNull { item ->
            resolved[item.anilistId]?.let { item to it }
        }
        resolvedEditorial = editorials
        val genres = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Romance", "Sci-Fi", "Mystery", "Horror", "Slice of Life",
            "Sports", "Supernatural", "Music"
        )
        editorialGenres = genres.associateWith { genre ->
            editorials.filter { (source, _) ->
                source.genres.any { it.equals(genre, ignoreCase = true) }
            }.map { it.second }.distinctBy { it.sourceId }
        }
        val orderedRails = linkedMapOf<String, List<KeylessTitle>>()
        if (trends.isNotEmpty()) orderedRails["Em alta no mundo dos animes"] = trends
        if (premieres.isNotEmpty()) orderedRails["Novidades e estreias"] = premieres
        fun genreRail(label: String, vararg names: String) {
            val items = editorials.filter { (anime, _) ->
                anime.genres.any { group -> names.any { it.equals(group, true) } }
            }.map { it.second }.distinctBy { it.sourceId }.take(36)
            if (items.isNotEmpty()) orderedRails[label] = items
        }
        genreRail("Ação e aventura", "Action", "Adventure")
        genreRail("Fantasia e outros mundos", "Fantasy", "Supernatural")
        genreRail("Romance e emoções", "Romance", "Drama", "Slice of Life")
        genreRail("Comédia", "Comedy")
        genreRail("Desporto", "Sports")
        val movieOrder = list(ranked.filter { it.isMovie })
        val existingMovies = previousMovieRail()
        val allMovies = (movieOrder + existingMovies)
            .filter { it.format == "MOVIE" }
            .distinctBy { it.sourceId }
            .take(48)
        if (allMovies.isNotEmpty()) orderedRails["Filmes de anime"] = allMovies

        val previous = _state.value
        val combined = previous.editorialRails.toMutableMap()
        combined.putAll(orderedRails)
        val all = (index + previous.sections["anime"].orEmpty()).distinctBy { it.sourceId }
        _state.value = previous.copy(
            sections = previous.sections + ("anime" to all),
            editorialRails = combined
        )
        // When a genre was already selected, refresh it in-place without
        // replacing the route, focus owner or playback state.
        val current = _state.value
        if (current.browseQuery.isBlank() && current.browseGenre.isNotBlank()) {
            editorialGenres[current.browseGenre]?.takeIf { it.isNotEmpty() }?.let { matches ->
                _state.value = current.copy(
                    browseItems = matches, browseLoading = false,
                    browseHasMore = false
                )
            }
        }
    }

    private fun editorialKey(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private suspend fun resolveEditorialTitle(anime: AnimeEditorialItem): KeylessTitle? {
        val type = if (anime.isMovie) "movie" else "tv"
        val names = anime.names.filter { it.isNotBlank() }.take(3)
        val acceptable = anime.names.map(::editorialKey).toSet()
        for (name in names) {
            val results = try {
                if (anime.isMovie) tmdb.searchMovieForArtwork(name).results
                else tmdb.searchTvForArtwork(name).results
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { continue }
            for (candidate in results) {
                if (editorialKey(candidate.name) !in acceptable) continue
                if (candidate.originalLanguage != "ja" ||
                    16 !in candidate.genreIds.orEmpty()) continue
                val item = toTitle(candidate, type) ?: continue
                // The same TMDB title is always routed as that title, not as a
                // season or another show. Anime posters and streams remain TMDB.
                return item
            }
        }
        return null
    }

    private suspend fun trending(): List<KeylessTitle> = coroutineScope {
        val shows = async {
            tmdb.getTrendingAnime("week").results.mapNotNull { toTitle(it, "tv") }
        }
        val films = async {
            tmdb.getTrendingAnimeMovies("week").results.mapNotNull { toTitle(it, "movie") }
        }
        // Some weeks have very few anime movies in global trends.
        (runCatching { shows.await() }.getOrDefault(emptyList()) +
            runCatching { films.await() }.getOrDefault(emptyList()))
            .distinctBy { it.sourceId }.take(36)
    }

    fun browse(type: String, query: String = "", genre: String = "") {
        if (type != "anime") return
        browseJob?.cancel()
        if (query.isBlank() && genre.isNotBlank()) {
            val ranked = editorialGenres[genre].orEmpty()
            if (ranked.isNotEmpty()) {
                browseHasMore = false
                _state.value = _state.value.copy(
                    browseType = "anime", browseGenre = genre, browseQuery = "",
                    browseItems = ranked, browseHasMore = false, browseError = null,
                    browseLoading = false
                )
                return
            }
        }
        browsePage = 0
        browseHasMore = true
        _state.value = _state.value.copy(
            browseType = "anime", browseGenre = genre, browseQuery = query,
            browseItems = emptyList(), browseHasMore = true,
            browseError = null, browseLoading = false
        )
        loadMore()
    }

    fun loadMore() {
        if (browseJob?.isActive == true || !browseHasMore) return
        val requestedGenre = _state.value.browseGenre
        val requestedQuery = _state.value.browseQuery
        val next = browsePage + 1
        browseJob = viewModelScope.launch {
            _state.value = _state.value.copy(browseLoading = true, browseError = null)
            try {
                val fetched = coroutineScope {
                    listOf("tv", "movie").map { type ->
                        async {
                            runCatching {
                                if (requestedQuery.isBlank()) discover(type, next, requestedGenre)
                                else search(type, requestedQuery, next, requestedGenre)
                            }.getOrDefault(emptyList())
                        }
                    }.flatMap { it.await() }
                }
                val old = if (next == 1) emptyList() else _state.value.browseItems
                browsePage = next
                // Search pages can be sparse after filtering; keep a finite horizon.
                browseHasMore = fetched.isNotEmpty() && next < 20
                _state.value = _state.value.copy(
                    browseItems = (old + fetched).distinctBy { it.sourceId },
                    browseHasMore = browseHasMore, browseLoading = false
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    browseLoading = false,
                    browseError = "Falha ao carregar resultados de anime.")
            }
        }
    }

    private suspend fun discover(
        type: String, page: Int, genre: String = "",
        sort: String = "popularity.desc", recent: Boolean = false
    ): List<KeylessTitle> {
        val filters = mutableMapOf(
            "page" to page.toString(),
            "sort_by" to sort,
            "with_original_language" to "ja",
            "with_genres" to if (genreCode(type, genre) != null)
                "16,${genreCode(type, genre)}" else "16",
            "language" to "pt-BR",
            "include_adult" to "false"
        )
        if (recent) {
            val today = LocalDate.now()
            val start = today.minusMonths(16)
            if (type == "tv") {
                filters["first_air_date.gte"] = start.toString()
                filters["first_air_date.lte"] = today.toString()
            } else {
                filters["primary_release_year"] = today.year.toString()
            }
        }
        val response = if (type == "movie") tmdb.discoverMovieWith(filters)
            else tmdb.discoverTvWith(filters)
        return response.results.mapNotNull { toTitle(it, type) }
    }

    private suspend fun search(type: String, query: String, page: Int, genre: String): List<KeylessTitle> {
        // Use the exact same TMDB search endpoints as the global TV search.
        val response = if (type == "movie") tmdb.searchMovie(query, page)
            else tmdb.searchTv(query, page)
        val genreId = genreCode(type, genre)
        return response.results.filter {
            it.originalLanguage.equals("ja", ignoreCase = true) &&
                16 in it.genreIds.orEmpty() &&
                (genreId == null || genreId in it.genreIds.orEmpty())
        }.mapNotNull { toTitle(it, type) }
    }

    private fun toTitle(item: AnimeDto, type: String): KeylessTitle? {
        val ids = item.genreIds.orEmpty()
        // TMDB trending/search endpoints are NOT guaranteed to return anime.
        // Check EVERY card locally, even when a discover request was filtered.
        // One Piece's 1999 anime is TV 37854; the live-action show is TV 111110.
        if (item.id <= 0 || item.name.isBlank() || item.posterPath.isNullOrBlank() ||
            item.originalLanguage != "ja" || 16 !in ids) return null
        if (type == "tv" && item.name.equals("One Piece", ignoreCase = true) &&
            item.id != 37854) return null
        val genreNames = buildList {
            if (28 in ids || 10759 in ids) add("Action")
            if (12 in ids) add("Adventure")
            if (35 in ids) add("Comedy")
            if (18 in ids) add("Drama")
            if (14 in ids || 10765 in ids) add("Fantasy")
            if (10749 in ids) add("Romance")
            if (878 in ids) add("Sci-Fi")
            if (9648 in ids) add("Mystery")
            if (27 in ids) add("Horror")
        }
        return KeylessTitle(
            sourceId = "tmdb:$type:${item.id}",
            title = item.name,
            type = "anime",
            tmdbId = item.id,
            year = item.date.take(4).toIntOrNull(),
            poster = "https://image.tmdb.org/t/p/w500${item.posterPath}",
            background = item.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            description = item.overview.orEmpty(),
            genres = genreNames,
            format = if (type == "movie") "MOVIE" else "TV"
        )
    }

    private fun genreCode(type: String, genre: String): Int? = when (genre) {
        "Action", "Adventure" -> if (type == "movie") 28 else 10759
        "Comedy" -> 35
        "Drama", "Romance", "Slice of Life", "Sports", "Music" -> 18
        "Fantasy", "Supernatural", "Sci-Fi" -> if (type == "movie") 14 else 10765
        "Mystery" -> 9648
        "Horror" -> if (type == "movie") 27 else 9648
        else -> null
    }
}
