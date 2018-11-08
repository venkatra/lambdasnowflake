package com.hashmap.blog.lambda.initializer

import java.sql.Connection
import java.util.Properties

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.logging.log4j.LogManager

/**
  * Case class to how snowflake connection information. At runtime, information from AWS SecretManager will be
  * retrieved and ppopulated and maintained privately in the ContainerInitializer class.
  *
  * @param url  => JDBC URL
  * @param user
  * @param passPhrase => The passphrase to decrypt the private key data stored in the secret manager
  * @param account
  * @param rsa_key_p8 => The private key data
  */
case class SnowflakeConn(url :String ,user :String ,passPhrase :String ,account :String ,rsa_key_p8 :String)

/** Initializer class that helps in initialization of snowflake related connection and initializing the pool.
  *
  */
trait DBConnPoolInitializer {
  val logger = LogManager.getLogger(getClass)

  var snowflakeDS:Option[HikariDataSource] = _

  /** Retreives the information from the AWS Secret Manager. The Secrets stored is explained in the case class
    * SnowflakeConn.
    *
    * @return The raw secret json string
    */
  private def retrieveDBConnInfo(awsSecretSnowflakeKey:String, awsRegion:String):Option[String] = {
    logger.debug("Retrieving connection information ...")

    // Create a Secrets Manager client
    val aws_sm_client  = AWSSecretsManagerClientBuilder.standard()
      .withRegion(awsRegion)
      .build();

    val getSecretValueRequest = new GetSecretValueRequest()
      .withSecretId(awsSecretSnowflakeKey)

    var secret :Option[String] = None

    try {
      val getSecretValueResult = aws_sm_client.getSecretValue(getSecretValueRequest)

      secret = Some(getSecretValueResult.getSecretString)
      logger.trace("secret retrieved")

    }catch {
      case decryptionEx :DecryptionFailureException => logger.error("Unable to decrypt info ",decryptionEx)
      // Secrets Manager can't decrypt the protected secret text using the provided KMS key.
      // Deal with the exception here, and/or rethrow at your discretion.

      case internalEx :InternalServiceErrorException => logger.error("Internal error ",internalEx)
      // An error occurred on the server side.
      // Deal with the exception here, and/or rethrow at your discretion.
      //
      case invalidParamEx :InvalidParameterException => logger.error("Invalid param error ",invalidParamEx)
      // You provided an invalid value for a parameter.
      // Deal with the exception here, and/or rethrow at your discretion.

      case invalidReqEx :InvalidRequestException => logger.error("Invalid request error ",invalidReqEx)
      // You provided a parameter value that is not valid for the current state of the resource.
      // Deal with the exception here, and/or rethrow at your discretion.

      case resourceEx :ResourceNotFoundException => logger.error("Resource not found error ",resourceEx)
      // We can't find the resource that you asked for.
      // Deal with the exception here, and/or rethrow at your discretion.
    }

    secret
  }

  /**
    * Parses the retrieved secrets key data. Example format
    * {{{
    *   {"url":"jdbc:snowflake://CLIENT.us-east-1.snowflakecomputing.com/?db=DB1&schema=SCHMEA1&warehouse=WH_TEST&role=DEV"
    *   ,"user":"svcBlogger"
    *   ,"passPhrase":"ASDADSASDAS!@"
    *   ,"account":"CLIENT"
    *   ,"rsa_key_p8":"MIIE...w=="}
    * }}}
    *
    * @param dbSecretKeyData
    * @return
    */
  def parseDBSecretInfo(dbSecretKeyData :String):Either[Error ,SnowflakeConn] = {
    logger.trace("Parsing snow flake secret info from retrieved aws secret manager key ...")
    val secretsData = dbSecretKeyData.replaceAll("\r","").replaceAll("\n","")
    val connInfoEither = decode[SnowflakeConn](secretsData)

    connInfoEither
  }

  private def instantiateDataSource(connInfo:SnowflakeConn):HikariDataSource = {
    logger.debug(s"Connecting to datastore as user[${connInfo.user}] to " +
      s"${connInfo.url}...")
    val connProp = new Properties()
    connProp.put("user", connInfo.user);
    connProp.put("account", connInfo.account);

    logger.trace("Creating datasource ...")
    val config = new HikariConfig()

    config.setDataSourceClassName("com.hashmap.blog.lambda.initializer.SnowflakeKeypairDataSource")
    config.addDataSourceProperty("properties", connProp)
    config.addDataSourceProperty("p8RSAKeyData", connInfo.rsa_key_p8)
    config.addDataSourceProperty("p8RSAKeyPassPhrase", connInfo.passPhrase)
    config.addDataSourceProperty("url", connInfo.url)

    config.setUsername(connInfo.user)

    config.setMaximumPoolSize(2) //Fine tuning parameter
    config.setConnectionTestQuery("select 1")

    new HikariDataSource(config)
  }

  def initializeConfigurationToDB(awsSecretSnowflakeKey:String, awsRegion:String, ocspCacheDir :String= "/tmp") = {
    logger.debug(">>>> DB connect ")

    //Ref : https://docs.snowflake.net/manuals/user-guide/jdbc-configure.html#file-caches
    //In this environment since we dont have the java submit process; the only way is to do the System.setProperty unfortunately
    System.setProperty("net.snowflake.jdbc.temporaryCredentialCacheDir", ocspCacheDir)
    System.setProperty("net.snowflake.jdbc.ocspResponseCacheDir", ocspCacheDir)

    snowflakeDS =  for {
      secertsJson <- retrieveDBConnInfo(awsSecretSnowflakeKey ,awsRegion);
      if secertsJson.length > 0;
      connInfoEither = parseDBSecretInfo(secertsJson);
      if connInfoEither.isRight == true;
      connInfo = connInfoEither.right
      } yield instantiateDataSource(connInfo.get)

    snowflakeDS
  }

  def isConnected():Boolean = snowflakeDS.isDefined match {
      case true => snowflakeDS.get.isRunning
      case _ => false
    }

  def getConnection():Option[Connection] = snowflakeDS.map(f => {
      f.getConnection
    })

  def testConnection(conn :Connection) = {
    logger.info("Testing connection ...")
    val stat = conn.createStatement();

    logger.debug("Issuing query ....")
    val res = stat.executeQuery("select 1");

    res.next();
    logger.debug(res.getString(1));

    logger.debug("Clossing connection ...")
    conn.close();
  }
}

object DBConnPoolInitializer {
}