package eu.kanade.tachiyomi.animeextension.en.nineanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object NineAnimeFilters {
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

    private inline fun <reified R> AnimeFilterList.getFirst(): R {
        return this.filterIsInstance<R>().first()
    }

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
        name: String,
    ): String {
        return (this.getFirst<R>() as CheckBoxFilterList).state
            .mapNotNull { checkbox ->
                if (checkbox.state) {
                    options.find { it.first == checkbox.name }!!.second
                } else {
                    null
                }
            }.joinToString("&$name[]=").let {
                if (it.isBlank()) {
                    ""
                } else {
                    "&$name[]=$it"
                }
            }
    }

    class SortFilter : QueryPartFilter("Sort order", NineAnimeFiltersData.sort)

    class GenreFilter : CheckBoxFilterList(
        "Genre",
        NineAnimeFiltersData.genre.map { CheckBoxVal(it.first, false) },
    )

    class CountryFilter : CheckBoxFilterList(
        "Country",
        NineAnimeFiltersData.country.map { CheckBoxVal(it.first, false) },
    )

    class SeasonFilter : CheckBoxFilterList(
        "Season",
        NineAnimeFiltersData.season.map { CheckBoxVal(it.first, false) },
    )

    class YearFilter : CheckBoxFilterList(
        "Year",
        NineAnimeFiltersData.year.map { CheckBoxVal(it.first, false) },
    )

    class TypeFilter : CheckBoxFilterList(
        "Type",
        NineAnimeFiltersData.type.map { CheckBoxVal(it.first, false) },
    )

    class StatusFilter : CheckBoxFilterList(
        "Status",
        NineAnimeFiltersData.status.map { CheckBoxVal(it.first, false) },
    )

    class LanguageFilter : CheckBoxFilterList(
        "Language",
        NineAnimeFiltersData.language.map { CheckBoxVal(it.first, false) },
    )

    class RatingFilter : CheckBoxFilterList(
        "Rating",
        NineAnimeFiltersData.rating.map { CheckBoxVal(it.first, false) },
    )

    val filterList = AnimeFilterList(
        SortFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        CountryFilter(),
        SeasonFilter(),
        YearFilter(),
        TypeFilter(),
        StatusFilter(),
        LanguageFilter(),
        RatingFilter(),
    )

    data class FilterSearchParams(
        val sort: String = "",
        val genre: String = "",
        val country: String = "",
        val season: String = "",
        val year: String = "",
        val type: String = "",
        val status: String = "",
        val language: String = "",
        val rating: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            filters.asQueryPart<SortFilter>(),
            filters.parseCheckbox<GenreFilter>(NineAnimeFiltersData.genre, "genre"),
            filters.parseCheckbox<CountryFilter>(NineAnimeFiltersData.country, "country"),
            filters.parseCheckbox<SeasonFilter>(NineAnimeFiltersData.season, "season"),
            filters.parseCheckbox<YearFilter>(NineAnimeFiltersData.year, "year"),
            filters.parseCheckbox<TypeFilter>(NineAnimeFiltersData.type, "type"),
            filters.parseCheckbox<StatusFilter>(NineAnimeFiltersData.status, "status"),
            filters.parseCheckbox<LanguageFilter>(NineAnimeFiltersData.language, "language"),
            filters.parseCheckbox<RatingFilter>(NineAnimeFiltersData.rating, "rating"),
        )
    }

    private object NineAnimeFiltersData {
        val sort = arrayOf(
            Pair("Most relevance", "most_relevance"),
            Pair("Recently updated", "recently_updated"),
            Pair("Recently added", "recently_added"),
            Pair("Release date", "release_date"),
            Pair("Trending", "trending"),
            Pair("Name A-Z", "title_az"),
            Pair("Scores", "scores"),
            Pair("MAL scores", "mal_scores"),
            Pair("Most watched", "most_watched"),
            Pair("Most favourited", "most_favourited"),
            Pair("Number of episodes", "number_of_episodes"),
        )

        val genre = arrayOf(
            Pair("Action", "1"),
            Pair("Adventure", "2"),
            Pair("Avant Garde", "2262888"),
            Pair("Boys Love", "2262603"),
            Pair("Comedy", "4"),
            Pair("Demons", "4424081"),
            Pair("Drama", "7"),
            Pair("Ecchi", "8"),
            Pair("Fantasy", "9"),
            Pair("Girls Love", "2263743"),
            Pair("Gourmet", "2263289"),
            Pair("Harem", "11"),
            Pair("Horror", "14"),
            Pair("Isekai", "3457284"),
            Pair("Iyashikei", "4398552"),
            Pair("Josei", "15"),
            Pair("Kids", "16"),
            Pair("Magic", "4424082"),
            Pair("Mahou Shoujo", "3457321"),
            Pair("Martial Arts", "18"),
            Pair("Mecha", "19"),
            Pair("Military", "20"),
            Pair("Music", "21"),
            Pair("Mystery", "22"),
            Pair("Parody", "23"),
            Pair("Psychological", "25"),
            Pair("Reverse Harem", "4398403"),
            Pair("Romance", "26"),
            Pair("School", "28"),
            Pair("Sci-Fi", "29"),
            Pair("Seinen", "30"),
            Pair("Shoujo", "31"),
            Pair("Shounen", "33"),
            Pair("Slice of Life", "35"),
            Pair("Space", "36"),
            Pair("Sports", "37"),
            Pair("Super Power", "38"),
            Pair("Supernatural", "39"),
            Pair("Suspense", "2262590"),
            Pair("Thriller", "40"),
            Pair("Vampire", "41"),
        )

        val country = arrayOf(
            Pair("China", "120823"),
            Pair("Japan", "120822"),
        )

        val season = arrayOf(
            Pair("Fall", "fall"),
            Pair("Summer", "summer"),
            Pair("Spring", "spring"),
            Pair("Winter", "winter"),
            Pair("Unknown", "unknown"),
        )

        val year = arrayOf(
            Pair("2023", "2023"),
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("2017", "2017"),
            Pair("2016", "2016"),
            Pair("2015", "2015"),
            Pair("2014", "2014"),
            Pair("2013", "2013"),
            Pair("2012", "2012"),
            Pair("2011", "2011"),
            Pair("2010", "2010"),
            Pair("2009", "2009"),
            Pair("2008", "2008"),
            Pair("2007", "2007"),
            Pair("2006", "2006"),
            Pair("2005", "2005"),
            Pair("2004", "2004"),
            Pair("2003", "2003"),
            Pair("2000s", "2000s"),
            Pair("1990s", "1990s"),
            Pair("1980s", "1980s"),
            Pair("1970s", "1970s"),
            Pair("1960s", "1960s"),
            Pair("1950s", "1950s"),
            Pair("1940s", "1940s"),
            Pair("1930s", "1930s"),
            Pair("1920s", "1920s"),
            Pair("1910s", "1910s"),
        )

        val type = arrayOf(
            Pair("Movie", "movie"),
            Pair("TV", "tv"),
            Pair("OVA", "ova"),
            Pair("ONA", "ona"),
            Pair("Special", "special"),
            Pair("Music", "music"),
        )

        val status = arrayOf(
            Pair("Not Yet Aired", "info"),
            Pair("Releasing", "releasing"),
            Pair("Completed", "completed"),
        )

        val language = arrayOf(
            Pair("Sub and Dub", "subdub"),
            Pair("Sub", "sub"),
            Pair("Dub", "dub"),
        )

        val rating = arrayOf(
            Pair("G - All Ages", "g"),
            Pair("PG - Children", "pg"),
            Pair("PG 13 - Teens 13 and Older", "pg_13"),
            Pair("R - 17+, Violence & Profanity", "r"),
            Pair("R+ - Profanity & Mild Nudity", "r+"),
            Pair("Rx - Hentai", "rx"),
        )
    }
}
