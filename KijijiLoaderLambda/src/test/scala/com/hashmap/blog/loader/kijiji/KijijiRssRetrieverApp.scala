package com.hashmap.blog.loader.kijiji

/**
  * Created for convieniently testing the rss retriever and basic understanding, not meant for anything else.
  */
object KijijiRssRetrieverApp extends App {

  val retriever = new RssRetreiver("https://www.kijiji.ca/rss-srp-cars-vehicles/city-of-toronto/c27l1700273")
  val httpResponse = retriever.getHttpResponseContent
  httpResponse.map(content => {
    val listOfKijijiAds = retriever.parseAndTransform(content)
    listOfKijijiAds.foreach(x => println(x.adUrl))
  })
}
