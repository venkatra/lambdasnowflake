package com.hashmap.blog.initializer

import java.sql.Connection

import org.apache.logging.log4j.{LogManager, Logger}
import com.hashmap.blog.lambda.initializer.DBConnPoolInitializer
import javax.sql.DataSource


/** Implementation for common initialization steps for the Lambda execution environment. We are having code that initialize
  * resources which are typically a little costly operations, ex database connection pooling.
  *
  * Remember that there is no guarentee that when the lambda function executes the resources initialized here will be
  * available. So as a precautioner step always check for resource validity before execution.
  *
  */
class ContainerInitializer(override val logger :Logger) extends DBConnPoolInitializer {
  val AWS_SECRET_SNWFLK_KEY = "dev/blog"
  val AWS_REGION = "us-east-2"
  val OCSP_CACHE_DIR = "/tmp"

  lazy val dbConnPoolDS:Option[DataSource] = initializeConfigurationToDB(AWS_SECRET_SNWFLK_KEY ,AWS_REGION ,OCSP_CACHE_DIR)

}

object ContainerInitializer {
  val logger = LogManager.getLogger(getClass)
  val initializer = new ContainerInitializer(logger)

  logger.info("====== INITIALIZE CONTAINER =====")
  initializer.dbConnPoolDS // force initialization
//  initializer.dbConnPoolDS.map(dbDS => {
//    val conn = dbDS.getConnection()
//    initializer.testConnection(conn)
//  })

  logger.info("====== INITIALIZED !!! =====")

  def getDataSource = initializer.dbConnPoolDS

  def testConnection(conn :Connection) = initializer.testConnection(conn)

}