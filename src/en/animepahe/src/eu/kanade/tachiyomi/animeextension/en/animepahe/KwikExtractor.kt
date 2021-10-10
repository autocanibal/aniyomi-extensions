/** The following file is slightly modified and taken from: https://github.com/LagradOst/CloudStream-3/blob/4d6050219083d675ba9c7088b59a9492fcaa32c7/app/src/main/java/com/lagradost/cloudstream3/animeproviders/AnimePaheProvider.kt
 * It is published under the following license:
 *
MIT License

Copyright (c) 2021 Osten

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 *
 */


package eu.kanade.tachiyomi.animeextension.en.animepahe

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.math.pow

class KwikExtractor(private val client: OkHttpClient) {
    private var cookies: String = ""

    private val ytsm = "ysmm = '([^']+)".toRegex()
    private val kwikParamsRegex = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""")
    private val kwikDUrl = Regex("action=\"([^\"]+)\"")
    private val kwikDToken = Regex("value=\"([^\"]+)\"")

    private fun bypassAdfly(adflyUri: String): String {
        var responseCode = 302
        var adflyContent: Response? = null
        var tries = 0
        val noRedirectClient = OkHttpClient().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        while (responseCode != 200 && tries < 20) {
            val nextUrl = noRedirectClient.newCall(GET(adflyUri)).execute().header("location")!!
            adflyContent = noRedirectClient.newCall(GET(nextUrl)).execute()
            cookies += adflyContent.header("set-cookie") ?: ""
            responseCode = adflyContent.code
            ++tries
        }
        if (tries > 19) {
            throw Exception("Failed to bypass adfly.")
        }
        return decodeAdfly(ytsm.find(adflyContent?.body!!.string())!!.destructured.component1())
    }

    private fun decodeAdfly(codedKey: String): String {
        var r = ""
        var j = ""

        for ((n, l) in codedKey.withIndex()) {
            if (n % 2 != 0) {
                j = l + j
            } else {
                r += l
            }
        }

        val encodedUri = ((r + j).toCharArray().map { it.toString() }).toMutableList()
        val numbers = sequence {
            for ((i, n) in encodedUri.withIndex()) {
                if (isNumber(n)) {
                    yield(Pair(i, n.toInt()))
                }
            }
        }

        for ((first, second) in zipGen(numbers)) {
            val xor = first.second.xor(second.second)
            if (xor < 10) {
                encodedUri[first.first] = xor.toString()
            }
        }
        var returnValue = String(encodedUri.joinToString("").toByteArray(), Charsets.UTF_8)
        returnValue = String(android.util.Base64.decode(returnValue, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
        return returnValue.slice(16..returnValue.length - 17)
    }

    private fun isNumber(s: String?): Boolean {
        return s?.toIntOrNull() != null
    }

    private fun zipGen(gen: Sequence<Pair<Int, Int>>): ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val allItems = gen.toList().toMutableList()
        val newList = ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>>()

        while (allItems.size > 1) {
            newList.add(Pair(allItems[0], allItems[1]))
            allItems.removeAt(0)
            allItems.removeAt(0)
        }
        return newList
    }

    fun getStreamUrlFromKwik(adflyUri: String): String {
        val fContent =
            client.newCall(GET(bypassAdfly(adflyUri), Headers.headersOf("referer", "https://kwik.cx/"))).execute()
        cookies += (fContent.header("set-cookie")!!)
        val fContentString = fContent.body!!.string()

        val (fullString, key, v1, v2) = kwikParamsRegex.find(fContentString)!!.destructured
        val decrypted = decrypt(fullString, key, v1.toInt(), v2.toInt())
        val uri = kwikDUrl.find(decrypted)!!.destructured.component1()
        val tok = kwikDToken.find(decrypted)!!.destructured.component1()
        var content: Response? = null

        var code = 419
        var tries = 0

        val noRedirectClient = OkHttpClient().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(client.cookieJar)
            .build()

        while (code != 302 && tries < 20) {

            content = noRedirectClient.newCall(
                POST(
                    uri,
                    Headers.headersOf(
                        "referer", fContent.request.url.toString(),
                        "cookie", fContent.header("set-cookie")!!.replace("path=/;", "")
                    ),
                    FormBody.Builder().add("_token", tok).build()
                )
            ).execute()
            code = content.code
            ++tries
        }
        if (tries > 19) {
            throw Exception("Failed to extract the stream uri from kwik.")
        }
        val location = content?.header("location").toString()
        content?.close()
        return location
    }

    private fun decrypt(fullString: String, key: String, v1: Int, v2: Int): String {
        var r = ""
        var i = 0

        while (i < fullString.length) {
            var s = ""

            while (fullString[i] != key[v2]) {
                s += fullString[i]
                ++i
            }
            var j = 0

            while (j < key.length) {
                s = s.replace(key[j].toString(), j.toString())
                ++j
            }
            r += (getString(s, v2).toInt() - v1).toChar()
            ++i
        }
        return r
    }

    private fun getString(content: String, s1: Int): String {
        val s2 = 10
        val characterMap = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"

        val slice2 = characterMap.slice(0 until s2)
        var acc: Long = 0

        for ((n, i) in content.reversed().withIndex()) {
            acc += (
                when (isNumber("$i")) {
                    true -> "$i".toLong()
                    false -> "0".toLong()
                }
                ) * s1.toDouble().pow(n.toDouble()).toInt()
        }

        var k = ""

        while (acc > 0) {
            k = slice2[(acc % s2).toInt()] + k
            acc = (acc - (acc % s2)) / s2
        }

        return when (k != "") {
            true -> k
            false -> "0"
        }
    }
}