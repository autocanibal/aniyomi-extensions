package eu.kanade.tachiyomi.animeextension.it.animeunity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeUnityFilters {

    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart() = vals[state].second
    }

    open class CheckBoxFilterList(name: String, values: List<CheckBox>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)
    private class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(): String {
        return this.filterIsInstance<R>().joinToString("") {
            (it as QueryPartFilter).toQueryPart()
        }
    }

    class TopFilter : QueryPartFilter("Top Anime", AnimeUnityFiltersData.top)

    class GenreFilter : CheckBoxFilterList(
        "Genere",
        AnimeUnityFiltersData.genere.map { CheckBoxVal(it.first, false) },
    )

    class YearFilter : QueryPartFilter("Anno", AnimeUnityFiltersData.year)

    class OrderFilter : QueryPartFilter("Ordina", AnimeUnityFiltersData.order)

    class StateFilter : QueryPartFilter("Stato", AnimeUnityFiltersData.state)

    class TypeFilter : QueryPartFilter("Tipo", AnimeUnityFiltersData.type)

    class SeasonFilter : QueryPartFilter("Stagione", AnimeUnityFiltersData.season)

    class DubFilter : QueryPartFilter("Dub ITA", AnimeUnityFiltersData.dub)

    val filterList = AnimeFilterList(
        AnimeFilter.Header("Le migliori pagine di anime"),
        AnimeFilter.Header("Nota: ignora altri filtri"),
        TopFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        YearFilter(),
        OrderFilter(),
        StateFilter(),
        TypeFilter(),
        SeasonFilter(),
        DubFilter(),
    )

    data class FilterSearchParams(
        val top: String = "",
        val genre: String = "",
        val year: String = "",
        val order: String = "",
        val state: String = "",
        val type: String = "",
        val season: String = "",
        val dub: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        val genre: String = filters.filterIsInstance<GenreFilter>()
            .first()
            .state.mapNotNull { format ->
                if (format.state) {
                    "{\"id\":" +
                        AnimeUnityFiltersData.genere.find { it.first == format.name }!!.second +
                        ",\"name\":\"" +
                        AnimeUnityFiltersData.genere.find { it.first == format.name }!!.first +
                        "\"}"
                } else { null }
            }.joinToString(",")

        return FilterSearchParams(
            filters.asQueryPart<TopFilter>(),
            if (genre.isEmpty()) "" else "[$genre]",
            filters.asQueryPart<YearFilter>(),
            filters.asQueryPart<OrderFilter>(),
            filters.asQueryPart<StateFilter>(),
            filters.asQueryPart<TypeFilter>(),
            filters.asQueryPart<SeasonFilter>(),
            filters.asQueryPart<DubFilter>(),
        )
    }

    private object AnimeUnityFiltersData {
        val any = Pair("Any", "")

        val top = arrayOf(
            Pair("Nessuno", ""),
            Pair("Tutto", "top-anime"),
            Pair("In corso", "top-anime?status=In Corso"),
            Pair("In arrivo", "top-anime?status=In uscita prossimamente"),
            Pair("TV", "top-anime?type=TV"),
            Pair("Movie", "top-anime?type=Movie"),
            Pair("OVA", "top-anime?type=OVA"),
            Pair("ONA", "top-anime?type=ONA"),
            Pair("Special", "top-anime?type=Special"),
            Pair("Popolari", "top-anime?popular=true"),
            Pair("Preferiti", "top-anime?order=favorites"),
            Pair("Più visti", "top-anime?order=most_viewed"),
        )

        val genere = arrayOf(
            Pair("Action", "51"),
            Pair("Adventure", "21"),
            Pair("Cars", "29"),
            Pair("Comedy", "37"),
            Pair("Dementia", "43"),
            Pair("Demons", "13"),
            Pair("Drama", "22"),
            Pair("Ecchi", "5"),
            Pair("Fantasy", "9"),
            Pair("Game", "44"),
            Pair("Harem", "15"),
            Pair("Hentai", "4"),
            Pair("Historical", "30"),
            Pair("Horror", "3"),
            Pair("Josei", "45"),
            Pair("Kids", "14"),
            Pair("Magic", "23"),
            Pair("Martial Arts", "Martial 31"),
            Pair("Mecha", "38"),
            Pair("Military", "46"),
            Pair("Music", "16"),
            Pair("Mystery", "24"),
            Pair("Parody", "32"),
            Pair("Police", "39"),
            Pair("Psychological", "47"),
            Pair("Romance", "17"),
            Pair("Samurai", "25"),
            Pair("School", "33"),
            Pair("Sci-fi", "Sci-40"),
            Pair("Seinen", "49"),
            Pair("Shoujo", "18"),
            Pair("Shoujo Ai", "Shoujo 26"),
            Pair("Shounen", "34"),
            Pair("Shounen Ai", "Shounen 41"),
            Pair("Slice of Life", "Slice of 50"),
            Pair("Space", "19"),
            Pair("Splatter", "52"),
            Pair("Sports", "27"),
            Pair("Super Power", "Super 35"),
            Pair("Supernatural", "42"),
            Pair("Thriller", "48"),
            Pair("Vampire", "20"),
            Pair("Yaoi", "28"),
            Pair("Yuri", "36"),
        )

        val order = arrayOf(
            any,
            Pair("Lista A-Z", "Lista A-Z"),
            Pair("Lista Z-A", "Lista Z-A"),
            Pair("Popolarità", "Popolarità"),
            Pair("Valutazione", "Valutazione"),
        )

        val state = arrayOf(
            any,
            Pair("In Corso", "In Corso"),
            Pair("Terminato", "Terminato"),
            Pair("In Uscita", "In Uscita"),
            Pair("Droppato", "Droppato"),
        )

        val type = arrayOf(
            any,
            Pair("TV", "TV"),
            Pair("OVA", "OVA"),
            Pair("ONA", "ONA"),
            Pair("Special", "Special"),
            Pair("Movie", "Movie"),
        )

        val season = arrayOf(
            any,
            Pair("Inverno", "Inverno"),
            Pair("Primavera", "Primavera"),
            Pair("Estate", "Estate"),
            Pair("Autunno", "Autunno"),
        )

        val dub = arrayOf(
            Pair("No", ""),
            Pair("Sì", "true"),
        )

        val year = arrayOf(any) + (1969..2024).map {
            Pair(it.toString(), it.toString())
        }.reversed().toTypedArray()
    }
}
