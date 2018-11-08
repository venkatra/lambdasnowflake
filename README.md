# Interfacing to snowflake from was lambda

## SNOWFLAKE DATA WAREHOUSE

[SNOWFLAKE DATA WAREHOUSE](https://www.snowflake.com/product/) is a fully relational ANSI SQL data warehouse with a 
patented technology. Some features are:
 - Cloud based 
 - Zero Management
 - Time Travel
 - Any scale (Data, Compute, Users)
 - Pay as you go usage
 
and much more

## AWS Lambda

[AWS Lambda](https://aws.amazon.com/lambda/) lets you run code without provisioning or managing servers. You pay only 
for the compute time you consume - there is no charge when your code is not running. 
 
This write-up is about how to code a simple AWS Lambda function that communicates with SnowFlake. It demonstrates a working
example and things to consider.explains  and points to consider. The code is present in the GitHub - 
[lambdasnowflake](https://github.com/venkatra/lambdasnowflake).
  
### Scenario
  Typically when loading data into snowflake the preferred approach is to collect large amount of data into an s3 bucket 
  and load (example via `COPY` command). This is also true to extent of loading mini batched streaming data in the 
  size of atleast 100mb. Blog list:
  - [Blog - Part1](https://www.snowflake.com/blog/designing-big-data-stream-ingestion-architectures-using-snowflake-part-1/)
  - [Blog - Part2](https://www.snowflake.com/blog/designing-data-stream-ingestion-architectures-using-snowflake-part-ii/)
   
   There are some scenarios where data obtained is very smaller in size or no of records is less (like less than 1000 
   records). In these case a direct write operation seems a better approach. This code writeup is a demonstration of 
   one such hypothetical scenario.

#### Kijiji
 [Kijiji](https://www.kijiji.ca/) ,here in Canada, which provides a social platform to buy and sell stuffs. This is somewhat 
 similar to well known craigslist. It offers a RSS feed of the ads which can be retrieved ex [Kijiji-rss](https://www.kijiji.ca/rss-srp/l0),
 shows ads across canada and across all ad category. Typically on each retrieval you get around 20 or so records.
 
 For this write up the AWS Lambda function retrieves the feed ,parses the content ,extracts some information and stores 
 the data into snowflake. 

###### Main entry function
 The entry point to the lambda function is implemented in class [KijijiLoaderFn](./KijijiLoaderLambda/src/main/scala/com/hashmap/blog/loader/KijijiLoaderFn.scala)
  method `handle`. The lambda is set to get triggered based on cloud watch scheduler, at the rate of every 5 min.

##### Service account
 To communicate with snowflake, typically you can go about creating a application service account with users and password. 
 Snowflake also provides authentication using keypair methodology, with a rotational functionality 
 [key-pair docs](https://docs.snowflake.net/manuals/user-guide/jdbc-configure.html#using-key-pair-authentication)

 In a normal execution environment, the private key is stored up in a secure location and the program would access from 
 the path. In a lambda environment, since the container is dynamic, a regular file location can not be provided. One 
 possible way to overcome this is by having the program load the private key file from specific protected S3 bucket. 

###### AWS secret manager
 A better approach is by using the [AWS SECRETS MANAGER](https://aws.amazon.com/secrets-manager/). We could store all 
 the necessary connection for a specific snowflake here. In this example I have stored the following
 - URL
 - Snowflake account
 - Private key passphrase
 - Private key file content
 
 ![screenshot - secrets_manager_key](./img/secrets_manager_key.png?raw=true "screenshot - secrets_manager_key")
 
 At run-time, a quick api call to this specific key would return the entire record as a json data. The program could 
 parse the json and go about authenticating with snowflake. The code for this is demonstrated in the trait class 
 [DBConnPoolInitializer](./LambdaFnCommons/src/main/scala/com/hashmap/blog/lambda/initializer/DBConnPoolInitializer.scala)
 method ```retrieveDBConnInfo``` and to parse the json in method ```parseDBSecretInfo```. 

 _NOTE:_ Though here in the code it is hardcoded; but in actual functioning logic; i would recomend retrieving the `awsSecretSnowflakeKey`
 via an environment variable. The environment variable can be defined as part of the deployment process. 

###### Connection pool
 The code instantiates and setups a connection pool, via the well know library [HikariCP](https://github.com/brettwooldridge/HikariCP).
 The common adopted pattern of connecting to a data source using the user/password works; however there is currently no 
 implementation for creating a data source using keypair methodology mentioned above.

 To overcome this; I created a simple implementation which can be configured in Hikari CP. The datasource implementation 
 is the class [SnowflakeKeypairDataSource](./LambdaFnCommons/src/main/scala/com/hashmap/blog/lambda/initializer/SnowflakeKeypairDataSource.scala)
 . The parsing the private keypair and connecting is present in the methods ```getEncryptedPrivateKey``` and ```getConnection```.
 Configuring the Hikari library is in the class [DBConnPoolInitializer](./LambdaFnCommons/src/main/scala/com/hashmap/blog/lambda/initializer/DBConnPoolInitializer.scala)
 on the method ```instantiateDataSource```.
 
###### Lambda startup 
 The lambda execution happens randomly and there is no control on how a lambda execution container gets allocated. There
 are no licycle events to indicate to your application code that the container is initialized. By the time your lambda 
 function executes the container is already initialized.The [article](https://read.acloud.guru/how-long-does-aws-lambda-keep-your-idle-functions-around-before-a-cold-start-bf715d3b810) 
 from Yan Cui provided a good explanation when i initially ventured out.  
 
 In such a condition, creating and maintaining a connection pool would be un-deterministic; However, its well demonstrated
 by other that AWS tries to reuse an existing lambda container, if there are frequent invocations. Hence implementing a 
 static block in the lambda class provides a good spot for such one time initialization. I adopted this approach and
 is done via the object class [ContainerInitializer](./KijijiLoaderLambda/src/main/scala/com/hashmap/blog/initializer/ContainerInitializer.scala).
 The class gets instantiated as part of the main class [KijijiLoaderFn](./KijijiLoaderLambda/src/main/scala/com/hashmap/blog/loader/KijijiLoaderFn.scala).
 
 __*NOTE:*__ Do not use this approach to store application states, as the container could be destroyed at any random point
 by AWS.
 
###### The '/tmp' folder
 As part of communicating with snowflake, for ideal performance you would need to set a folder for snowflake to store cache.
 For this, we could use the '/tmp' folder from the lambda execution environment. Hence we set the environment variables
 to this folder during runtime, as demonstrated in [DBConnPoolInitializer](./LambdaFnCommons/src/main/scala/com/hashmap/blog/lambda/initializer/DBConnPoolInitializer.scala)
  method ```initializeConfigurationToDB```.
  
  __*NOTE:*__ Though it is ok with limitation to use the `/tmp` folder; However I generally would avoid misusing the folder 
  to store large amount of processing data, as the storage size is very limit (currently at 512 MB) and could change.

###### Execution Time limits
 Currently an AWS Lambda function can run to max of 15 minutes, knowledge of such should be considered when implementing
 the function. These ar documented [here](https://docs.aws.amazon.com/lambda/latest/dg/limits.html).
 
 This code however executes at an average of 3 secs; well below this limit.
 __*NOTE:*__ The functionality demonstrated here does not mean the lambda is slow (comparitive to certain use cases); SLA
 was not a consideration, the functionality is merely demonstration of connecting to snowflake from lambda. 
 
###### AWS SAM and AWS SAM CLI
  When developing it is usually a pain to build the code ,deploy to aws and run. It’s time consuming and also could cost 
  you, with those frequent uploads. I recommend developing using the [AWS SAM CLI](https://github.com/awslabs/aws-sam-cli) 
   tool. By using this I could test locally and also deploy it to AWS Cloud Formation. The template to use for local 
  testing [local_samcli.yaml](./KijijiLoaderLambda/local_samcli.yaml) and when deploying to AWS use the template 
  [deploy.yaml](./aws/deploy.yaml).

###### Policies
 For proper functioning the lambda function needs the following policies:
 - S3 bucket get
 - AWS screts manager read
 - Lambda basic execution
 Being lazy, for this demo, I used the existing pre-defined managed policies as below.
 
 ![aws-lambda-policy-role.png](./img/aws-lambda-policy-role.png?raw=true "aws-lambda-policy-role")
 
 just ignore the SNS from the definition; it was used for some other demo.

##### Observations
 Here is the screenshot from the monitoring after this lambda ran for a couple of hours :
 
 ![KijijiLoader-Lambda-Monitoring.png](./img/KijijiLoader-Lambda-Monitoring.png?raw=true "KijijiLoader-Lambda-Monitoring")

 The lambda gets triggered every 5 minutes, and each invocation is typically 3 seconds in duration.

###### Instance count
 Even though i have set the max concurrency to 5, since the function finishes within the limit, AWS opts to reuse the 
 same instance on each reschedule. Thus normally there is only 1 execution ,observed in the `Concurent execution` graph.
 
###### initialization
 From the cloud watch extract log 
 ![cloud-watch-log](./img/cloud-watch-log.png?raw=true "cloud-watch-log"); 
 You can confirm that initialization happened only once but normal function which writes to snowflake happens regardless.
 This demonstrates that AWS tends to re-use the lambda container between execution
 
###### Gotchas
 You might be wondering; whats the big spike around 11:00 how come execution time went up; Well that’s the perils of budget. 
 I ran out of my snowflake credits. 
 ![credit-maxed-out](./img/credit-maxed-out.png?raw=true "credit-maxed-out")
 
 This does demonstrate that when you are designing for such mini batch dataload, consider the cost. If in your situation, 
 you can delay the loading to a later time (like every hour) and if there is a possibility to store the data (like in a 
 kafka queue); better have the data loading to every hour instead of every minute and using the COPY function.

###### Other scenarios
 Though this walk thru is about interfacing with snowflake and doing a mini batch data loading. You could learn from this
 exercise and build your lambda function ,coupled with a AWS CLOUD API, to host a REST end point. Then you could invoke 
 the end-point to provide a real-time data retrieval (doing a massive calculation in snowflake warehouse) and display in
 your webpage/dashboard.
 
### Final notes
 As explained, connecting from snowflake from AWS Lambda is possible; I have explained just the basics and there are other
 considerations to when adopting. We have developed AWS Lambda in python also communicating with snowflake and doing simillar
 functionality at our client engagements too.
 
 If you liked this write-up please :+1: it.
