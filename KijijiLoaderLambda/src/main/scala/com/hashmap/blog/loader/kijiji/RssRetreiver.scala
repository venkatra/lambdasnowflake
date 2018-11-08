package com.hashmap.blog.loader.kijiji

import java.sql.Connection

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.logging.log4j.LogManager

import scala.io.Source
import scala.xml.XML

case class KijijiAd(adId :Long ,adUrl :String ,category :String ,location :String ,title :String ,description :String ,pubDate :String ,price :Float)

/** Queries the KIJIJI RSS URL ,parses the content and stores the result into datastore
  *
  */
class RssRetreiver(val rssURL :String) {
  require((rssURL != null),"The kijiji rss url needs to be supplied")
  require((rssURL.isEmpty == false),"The kijiji rss url cannot be an empty string")
  val logger = LogManager.getLogger(getClass)

  val ADURL_LINK_PATTERN = "https://www.kijiji.ca/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/.*/([0-9]+)".r

  private def extractCategoryAndLocationFromAdURL(adUrl :String):Tuple3[String,String,Long] = {
   val ADURL_LINK_PATTERN(adCategory ,adLocation ,adId) = adUrl
    (adCategory ,adLocation ,adId.toLong)
  }

  def parseAndTransform(httpResponse :String):Seq[KijijiAd] = {
    logger.trace("parsing and transforming the response ...")

    // (2) convert it to xml
    val xml = XML.loadString(httpResponse)
    assert(xml.isInstanceOf[scala.xml.Elem])

    /* Sample ITEM element from the rss data
    <item>
      <title>2009 Mazda 3 Sport GS</title>
      <link>https://www.kijiji.ca/v-cars-trucks/hamilton/2009-mazda-3-sport-gs/1395844012</link>
      <description>I am offering a mazda 3 sport GS hatchback FULLY LOADED!!!! -Power windows -Leather interior -Heated seats -Bose speakers -Sun roof -Spacious trunk space -Cruise control, volume and change stations ...</description>
      <enclosure url="https://i.ebayimg.com/00/s/ODAwWDYwMA==/z/LhMAAOSwVH9b4YPC/$_59.JPG" length="14" type="image/jpeg" />
      <pubDate>Tue, 06 Nov 2018 12:07:25 GMT</pubDate>
      <guid>https://www.kijiji.ca/v-cars-trucks/hamilton/2009-mazda-3-sport-gs/1395844012</guid>
      <dc:date>2018-11-06T12:07:25Z</dc:date>
      <geo:lat>43.255722</geo:lat>
      <geo:long>-79.8711</geo:long>
      <g-core:price>4900.0</g-core:price>
    </item>
     */
    val itemNodeSeq = (xml \\ "rss" \\ "item")

    itemNodeSeq.map(itemNode => {
      val adUrl = (itemNode \\ "link").text
      val (adCategory ,adLocation ,adId) = extractCategoryAndLocationFromAdURL(adUrl)
      val adPrice:Float = ((itemNode \\ "price").text) match {
        case s:String if s.length > 0 => s.toFloat
        case _ => -1
      }

      KijijiAd(adId ,adUrl ,adCategory ,adLocation
        ,(itemNode \\ "title").text ,(itemNode \\ "description").text ,(itemNode \\ "pubDate").text
        ,adPrice)
    })

  }

  /**
    * Returns the response from the HTTP call. Returns a blank String if there
    * is a problem.
    */
  def getHttpResponseContent: Option[String] = {
    logger.debug(s"Retrieving content from kijiji ${rssURL} ...")
    val httpClient = new DefaultHttpClient()
    val httpResponse = httpClient.execute(new HttpGet(rssURL))
    val entity = httpResponse.getEntity()

    val content = if (entity != null) {
      val inputStream = entity.getContent()
      val respcontent = Source.fromInputStream(inputStream).getLines.mkString
      inputStream.close
      Some(respcontent)
    } else None
    httpClient.getConnectionManager().shutdown()

    content
  }
}
