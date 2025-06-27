package com.dark.neuroverse.temp

import org.jsoup.Jsoup

fun main() {
    scrapeWebsite("https://www.youtube.com/")
}

fun scrapeWebsite(url: String) {
    val doc = Jsoup.connect(url).get()
    val title = doc.title()
    println("Title: $title")

    val links = doc.select("a[href]")
    for (link in links) {
        println("Link: ${link.attr("abs:href")}")
    }
}
