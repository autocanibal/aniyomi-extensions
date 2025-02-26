package eu.kanade.tachiyomi.multisrc.dopeflix

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object DopeFlixFilters {

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
        return (this.getFirst<R>() as QueryPartFilter).toQueryPart()
    }

    private inline fun <reified R> AnimeFilterList.getFirst(): R {
        return this.filterIsInstance<R>().first()
    }

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
    ): String {
        return (this.getFirst<R>() as CheckBoxFilterList).state
            .mapNotNull { checkbox ->
                if (checkbox.state) {
                    options.find { it.first == checkbox.name }!!.second
                } else {
                    null
                }
            }.joinToString("-").let {
                if (it.isBlank()) {
                    "all"
                } else {
                    it
                }
            }
    }

    class TypeFilter : QueryPartFilter("Type", DopeFlixFiltersData.types)
    class QualityFilter : QueryPartFilter("Quality", DopeFlixFiltersData.qualities)
    class ReleaseYearFilter : QueryPartFilter("Released at", DopeFlixFiltersData.years)

    class GenresFilter : CheckBoxFilterList(
        "Genres",
        DopeFlixFiltersData.genres.map { CheckBoxVal(it.first, false) },
    )
    class CountriesFilter : CheckBoxFilterList(
        "Countries",
        DopeFlixFiltersData.countries.map { CheckBoxVal(it.first, false) },
    )

    val filterList = AnimeFilterList(
        TypeFilter(),
        QualityFilter(),
        ReleaseYearFilter(),
        AnimeFilter.Separator(),
        GenresFilter(),
        CountriesFilter(),
    )

    data class FilterSearchParams(
        val type: String = "",
        val quality: String = "",
        val releaseYear: String = "",
        val genres: String = "",
        val countries: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            filters.asQueryPart<TypeFilter>(),
            filters.asQueryPart<QualityFilter>(),
            filters.asQueryPart<ReleaseYearFilter>(),
            filters.parseCheckbox<GenresFilter>(DopeFlixFiltersData.genres),
            filters.parseCheckbox<CountriesFilter>(DopeFlixFiltersData.countries),
        )
    }

    private object DopeFlixFiltersData {
        val all = Pair("All", "all")

        val types = arrayOf(
            all,
            Pair("Movies", "movies"),
            Pair("TV Shows", "tv"),
        )

        val qualities = arrayOf(
            all,
            Pair("HD", "HD"),
            Pair("SD", "SD"),
            Pair("CAM", "CAM"),
        )

        val years = arrayOf(
            all,
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("Older", "older-2018"),
        )

        val genres = arrayOf(
            Pair("Action", "10"),
            Pair("Action & Adventure", "24"),
            Pair("Adventure", "18"),
            Pair("Animation", "3"),
            Pair("Biography", "37"),
            Pair("Comedy", "7"),
            Pair("Crime", "2"),
            Pair("Documentary", "11"),
            Pair("Drama", "4"),
            Pair("Family", "9"),
            Pair("Fantasy", "13"),
            Pair("History", "19"),
            Pair("Horror", "14"),
            Pair("Kids", "27"),
            Pair("Music", "15"),
            Pair("Mystery", "1"),
            Pair("News", "34"),
            Pair("Reality", "22"),
            Pair("Romance", "12"),
            Pair("Sci-Fi & Fantasy", "31"),
            Pair("Science Fiction", "5"),
            Pair("Soap", "35"),
            Pair("Talk", "29"),
            Pair("Thriller", "16"),
            Pair("TV Movie", "8"),
            Pair("War", "17"),
            Pair("War & Politics", "28"),
            Pair("Western", "6"),
        )

        val countries = arrayOf(
            Pair("Argentina", "11"),
            Pair("Australia", "151"),
            Pair("Austria", "4"),
            Pair("Belgium", "44"),
            Pair("Brazil", "190"),
            Pair("Canada", "147"),
            Pair("China", "101"),
            Pair("Czech Republic", "231"),
            Pair("Denmark", "222"),
            Pair("Finland", "158"),
            Pair("France", "3"),
            Pair("Germany", "96"),
            Pair("Hong Kong", "93"),
            Pair("Hungary", "72"),
            Pair("India", "105"),
            Pair("Ireland", "196"),
            Pair("Israel", "24"),
            Pair("Italy", "205"),
            Pair("Japan", "173"),
            Pair("Luxembourg", "91"),
            Pair("Mexico", "40"),
            Pair("Netherlands", "172"),
            Pair("New Zealand", "122"),
            Pair("Norway", "219"),
            Pair("Poland", "23"),
            Pair("Romania", "170"),
            Pair("Russia", "109"),
            Pair("South Africa", "200"),
            Pair("South Korea", "135"),
            Pair("Spain", "62"),
            Pair("Sweden", "114"),
            Pair("Switzerland", "41"),
            Pair("Taiwan", "119"),
            Pair("Thailand", "57"),
            Pair("United Kingdom", "180"),
            Pair("United States of America", "129"),
        )
    }
}
