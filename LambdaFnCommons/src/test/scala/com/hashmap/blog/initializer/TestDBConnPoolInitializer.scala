package com.hashmap.blog.initializer

import com.hashmap.blog.lambda.initializer.{DBConnPoolInitializer}
import org.apache.logging.log4j.LogManager
import org.scalatest.{BeforeAndAfter, FunSuite}

import scala.io.Source

private class DBpoolInitializerImpl() extends DBConnPoolInitializer {
}

class TestDBConnPoolInitializer extends FunSuite with BeforeAndAfter {

  val logger = LogManager.getLogger(getClass)

  var connInitializer :DBConnPoolInitializer = _

  before {
    connInitializer = new DBpoolInitializerImpl
  }

  test("should be parse the retrieved secret information from secret manager") {
    val SECRETS_TEST_DATA_FILE = "src/test/test-resources/secrets_data.json"

    val test_data = Source.fromFile(SECRETS_TEST_DATA_FILE).mkString

    val connInfo = connInitializer.parseDBSecretInfo(test_data)

    assert(connInfo.isLeft == false)
    assert(connInfo.right.get.url == "jdbc:snowflake://CLIENT.us-east-1.snowflakecomputing.com")
    assert(connInfo.right.get.user == "svcBlogger")
    assert(connInfo.right.get.passPhrase == "ASDADSASDAS!@")
    assert(connInfo.right.get.account == "CLIENT")
    assert(connInfo.right.get.rsa_key_p8 == "MIIE...w==")
  }

}
