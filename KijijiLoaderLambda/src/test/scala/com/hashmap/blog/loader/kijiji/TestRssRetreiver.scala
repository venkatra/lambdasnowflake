package com.hashmap.blog.loader.kijiji

import java.util.Date

import com.amazonaws.util.DateUtils
import com.hashmap.blog.lambda.initializer.DBConnPoolInitializer
import org.apache.logging.log4j.LogManager
import org.scalatest.{BeforeAndAfter, FunSuite, PrivateMethodTester}

import scala.io.Source

class TestRssRetreiver extends FunSuite with BeforeAndAfter with PrivateMethodTester {

  val logger = LogManager.getLogger(getClass)

  var retriever :RssRetreiver = _

  before {
    retriever = new RssRetreiver("https://www.kijiji.ca/rss-srp-cars-vehicles/city-of-toronto/c27l1700273")
  }

  test("should be extract category and location from adUrl") {
    val adUrl = "https://www.kijiji.ca/v-baby-clothes-12-18-months/city-of-toronto/north-face-down-jacket-12-18-months/1395894351"

    val extractCategoryAndLocationFromAdURLMethod = PrivateMethod[Tuple3[String,String,Long]]('extractCategoryAndLocationFromAdURL)

    val (adCategory ,adLocation ,adId) = retriever invokePrivate extractCategoryAndLocationFromAdURLMethod(adUrl)
    assert(adCategory == "v-baby-clothes-12-18-months")
    assert(adLocation == "city-of-toronto")
    assert(adId == 1395894351)
  }

  test("should be parse and return the list of ads") {
    val sampleRssContentFile = "KijijiLoaderLambda/src/test/test-resources/KijijiRss-Sample.xml"
    val sampleRssContent = Source.fromFile(sampleRssContentFile).mkString

    val listOfKijijiAds = retriever.parseAndTransform(sampleRssContent)
    assert(listOfKijijiAds != null)
    assert(listOfKijijiAds.size == 20)

    val expected = KijijiAd(1329621642 ,"https://www.kijiji.ca/v-drum-percussion/london/drums/1329621642" ,"v-drum-percussion" ,"london"
      ,"Drums" ,"Good condition Single: 6&quot; x 6&quot; Double: 6&quot; x 5&quot;- 5&quot; x 5&quot; $25 each"
      ,"Tue, 06 Nov 2018 12:07:23 GMT" ,25)
    assert(listOfKijijiAds(1) == expected)
  }

}
