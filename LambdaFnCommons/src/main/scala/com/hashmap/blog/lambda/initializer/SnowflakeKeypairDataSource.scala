package com.hashmap.blog.lambda.initializer

import java.security.KeyFactory
import java.sql.{Connection, DriverManager, SQLException}
import java.util.{Base64, Properties}

import javax.crypto.spec.PBEKeySpec
import javax.crypto.{EncryptedPrivateKeyInfo, SecretKeyFactory}
import net.snowflake.client.jdbc.SnowflakeBasicDataSource
import org.apache.logging.log4j.LogManager

import scala.util.{Failure, Success, Try}

/** Custom overridden implementation of the Snowflake Datasource. The default datasource implementation does not handle
  * authenticating via the KeyPair. To overcome this; I have implemented this basic approach.
  *
  */
class SnowflakeKeypairDataSource extends SnowflakeBasicDataSource {
  val logger = LogManager.getLogger(getClass)
  var p8RSAKeyData :String = _
  var p8RSAKeyPassPhrase :String = _
  var connProperties :Properties = new Properties()
  var user :String = _
  var account :String = _
  var jdbcURL :String = _

  /** Parses the private key data and gets the private key which is used for connecting to snowflake via keypair authentication.
    * Reference :
    *  https://docs.snowflake.net/manuals/user-guide/jdbc-configure.html#using-key-pair-authentication
    *
    * @return Initialized properties
    */
  private def getEncryptedPrivateKey = {
    logger.trace("creating the private key  ...")
    val private_key_data = p8RSAKeyData
      .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
      .replace("-----END ENCRYPTED PRIVATE KEY-----", "")

    val pkInfo = new EncryptedPrivateKeyInfo(Base64.getMimeDecoder().decode(private_key_data))
    val keySpec = new PBEKeySpec(p8RSAKeyPassPhrase.toCharArray());
    val pbeKeyFactory = SecretKeyFactory.getInstance(pkInfo.getAlgName());
    val encodedKeySpec = pkInfo.getKeySpec(pbeKeyFactory.generateSecret(keySpec));
    val keyFactory = KeyFactory.getInstance("RSA");
    val encryptedPrivateKey = keyFactory.generatePrivate(encodedKeySpec);

    encryptedPrivateKey
  }

  override def getConnection: Connection = {
    return this.getConnection(this.user, "");
  }

  @throws(classOf[SQLException])
  override def getConnection(username :String,password :String):Connection = {
    val encryptedPrivateKey = getEncryptedPrivateKey

    logger.info(s"Connecting to db ${jdbcURL}")

    connProperties.put("privateKey", encryptedPrivateKey);

    Try(DriverManager.getConnection(jdbcURL, connProperties)) match {
        case Failure(sqlEx) =>
          logger.error(s"Failed to create a connection for ${connProperties.getProperty("user")} " +
            s"at ${jdbcURL}: ${sqlEx.getMessage}",sqlEx)
          throw sqlEx
        case Success (conn) =>
          logger.trace(s"Created a connection for ${connProperties.getProperty("user")} at ${jdbcURL}")
          conn
    }
  }

  def setP8RSAKeyData(rsaKeyData :String) {
    this.p8RSAKeyData = rsaKeyData;
  }

  def setP8RSAKeyPassPhrase(passPhrase :String) = {
    this.p8RSAKeyPassPhrase = passPhrase
  }

  def setProperties(prop :Properties) = {
    this.connProperties = prop
  }

  override def setUrl(url: String): Unit = {
    this.jdbcURL = url
  }
}
