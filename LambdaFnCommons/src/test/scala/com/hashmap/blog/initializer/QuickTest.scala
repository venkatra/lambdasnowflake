package com.hashmap.blog.initializer

import com.hashmap.blog.lambda.initializer.DBConnPoolInitializer

private class DBpoolInitializer() extends DBConnPoolInitializer {

}

/**
  * A quick test app connectivity to snowflake using the DBConnPoolInitializer. Be sure to set the following
  * system configurations
  * - AWS_DEFAULT_REGION
  * - AWS_PROFILE
  * - aws.accessKeyId
  * - aws.secretKey
  */
object QuickTest extends App {
  val AWS_SECRET_SNWFLK_KEY = "dev/blog"
  val AWS_REGION = "us-east-2"
  val OCSP_CACHE_DIR = "/tmp"

  System.setProperty("AWS_DEFAULT_REGION","<<TO FILL HERE>>")
  System.setProperty("AWS_PROFILE","<<TO FILL HERE>>")
  System.setProperty("aws.accessKeyId","<<TO FILL HERE>>")
  System.setProperty("aws.secretKey","<<TO FILL HERE>>")

  val dbConnHelper :DBConnPoolInitializer = new DBpoolInitializer
  dbConnHelper.initializeConfigurationToDB(AWS_SECRET_SNWFLK_KEY ,AWS_REGION ,OCSP_CACHE_DIR)

  val conn = dbConnHelper.getConnection()

  conn.map(dbConnHelper.testConnection)
}
