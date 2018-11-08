package com.hashmap.blog.loader

import java.sql.{Connection, PreparedStatement}
import java.util.{Calendar, Date}

import org.apache.logging.log4j.LogManager
import io.circe.generic.auto._
import io.github.mkotsur.aws.handler.Lambda._
import io.github.mkotsur.aws.handler.Lambda
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.util.DateUtils
import com.hashmap.blog.initializer.ContainerInitializer
import com.hashmap.blog.loader.kijiji.{KijijiAd, RssRetreiver}

import scala.util.{Failure, Success, Try}

case class CloudWatchScheduleEvent(inputMsg: String)


/** Lambda function that retrieves rss from Kijiji and loads the same into the snowflake datastore
  */
class KijijiLoaderFn extends Lambda[CloudWatchScheduleEvent.type, None.type] {
  val logger = LogManager.getLogger(getClass)

  val initializer = ContainerInitializer


  /** Handle the schedule event. On each event run, the instance would query KIJIJI RSS end point and parse and extract
    * the ads. Once parsed it will be stored in snowflake table 'KijijiAds'.
    *
    * @param event - Sample event (Ref: https://docs.aws.amazon.com/AmazonCloudWatch/latest/events/RunLambdaSchedule.html)
    *              {
    *              "version": "0",
    *              "id": "53dc4d37-cffa-4f76-80c9-8b7d4a4d2eaa",
    *              "detail-type": "Scheduled Event",
    *              "source": "aws.events",
    *              "account": "123456789012",
    *              "time": "2015-10-08T16:53:06Z",
    *              "region": "us-east-1",
    *              "resources": [
    *              "arn:aws:events:us-east-1:123456789012:rule/my-scheduled-rule"
    *              ],
    *              "detail": {}
    *              }
    *
    * @param context
    * @return
    */

  override def handle(event: CloudWatchScheduleEvent.type,context: Context) = {
    val retriever = new RssRetreiver("https://www.kijiji.ca/rss-srp/l0")
    val httpResponse = retriever.getHttpResponseContent
    val listOfKijijiAds = httpResponse.map( retriever.parseAndTransform )

    listOfKijijiAds.map(lst => {
      initializer.getDataSource.map(ds => {
        save(lst , ds.getConnection)
      })
    })

    Right(None)
  }


  def save(listOfAds: Seq[KijijiAd], dbConn: Connection): Unit = {
    logger.trace(s"Saving ticker prices ...")
    val sqlStmt =
      s"""
        insert into KijijiAds(ADID ,ADURL ,ADCATEGORY ,LOCATION ,TITLE ,DESCRIPTION ,PUBDATE ,PRICE ,LAST_UPDATE)
        values(?,?,?,?,?,?,?,?,?)
      """.stripMargin

    var insertStatement: PreparedStatement = dbConn.prepareStatement(sqlStmt)
    for(t <- listOfAds) {
      insertStatement.setLong(1, t.adId)
      insertStatement.setString(2, t.adUrl)
      insertStatement.setString(3, t.category)
      insertStatement.setString(4, t.location)
      insertStatement.setString(5, t.title)
      insertStatement.setString(6, t.description)
      insertStatement.setString(7, t.pubDate)
      insertStatement.setFloat(8, t.price)
      val cal = Calendar.getInstance()
      insertStatement.setDate(9, new java.sql.Date(cal.getTimeInMillis)) //Ref : https://docs.snowflake.net/manuals/user-guide/date-time-examples.html#loading-dates-and-timestamps
      insertStatement.addBatch()
    }

    Try(insertStatement.executeBatch()) match {
      case Failure(sqlEx) => logger.error("Unable to write to datastore ", sqlEx)
        throw sqlEx

      case Success(_) =>
        logger.info(s"No of records written to datastore: ${listOfAds.size}")
    }

    if (insertStatement != null) insertStatement.close()

    dbConn.close()
  }


}

