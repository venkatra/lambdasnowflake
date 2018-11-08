
Some notes i captured when i did my development.

###### Pre-requisite

####### Developer workstation
In order to test with this functionality; you would need to have an AWS account. You also need to have
the following tools installed in your local development:
 - Maven
 - AWS CLI
 - AWS SAM CLI

####### AWS Resources
As mentioned in the [![ReadMe.md]](./ReadMe.md); you would need to have defined the following in AWS
 - S3 bucket => In the code this is set to hmap-lambdasnowflake-east1  with a folder/prefix 'code' which 
 will host the packaged code base
 
 - AWS Secrets Manager => Hosting the secrets to connect to snowflake. In the code ```dev/blog```
 
 - AWS Policy => This will be used by the lambda to use various resources. In the code this is set to ```lambda-funct-poc-role```  
     
####### To build 

```bash
mvn package
```

####### To test locally using AWS SAM CLI

The following environment variables need to be set before running all the below commands, with the appropriate
value substituted

```bash
export AWS_DEFAULT_REGION=<fill your here>
export AWS_PROFILE=<fill your here>
S3_CODE_BUCKET=s3://<fill your here>
CLOUD_STACK=<fill your stack name>
``` 

```bash
cd KijijiLoaderLambda
sam local generate-event cloudwatch scheduled-event | sam local invoke KijijiLoaderFn -t aws/local_dev.yaml
```

####### To test Upload to S3

```bash
echo "Uploading artifacts ..."
aws s3 rm  ${S3_CODE_BUCKET}/
aws s3 cp KijijiLoaderLambda/target/KijijiLoaderLambda-1.0.0.jar ${S3_CODE_BUCKET}/
```
####### To deploy to AWS using SAM

You should be in the project root directory to run these commands
```bash
echo "Creating cloud stack ..."
aws cloudformation delete-stack --stack-name ${CLOUD_STACK}
sleep 120
#(wait for sometime as deletion might be in progress)
sam deploy --template-file ./aws/deploy.yaml --stack-name ${CLOUD_STACK}
```
####### To list out the deployed lambda function
The lambda function gets suffixed with random characters after successful deployment; hence you would need to
obtain the deployed name.

The following is for getting the list of lambda functions
```bash
aws lambda list-functions | grep FunctionName | sed 's/.*: \"\([0-9a-zA-Z-]*\)\".*/\1/' 
```

To filter out only to the current deployed kijiji loader function
```bash
KIJIJI_LOADER_FN=$(aws lambda list-functions | grep FunctionName | sed 's/.*: \"\([0-9a-zA-Z-]*\)\".*/\1/' | grep ${CLOUD_STACK} | grep KijijiLoaderFn)
echo $KIJIJI_LOADER_FN
```
####### To invoke lambda function

```bash
aws lambda invoke --function-name $KIJIJI_LOADER_FN  --invocation-type Event out.txt
```
####### To update the code and deploy
If you make a change to the codebase and run, you dont need to redeploy the entire cloudformation. You could
just update the packaged code in S3 and issue a `lambda update-function-code` like below

```bash
KIJIJI_LOADER_FN=$(aws lambda list-functions | grep FunctionName | sed 's/.*: \"\([0-9a-zA-Z-]*\)\".*/\1/' | grep ${CLOUD_STACK} | grep KijijiLoaderFn)
aws lambda update-function-code --function-name $KIJIJI_LOADER_FN --s3-bucket <to fill here> --s3-key <set s3 prefix>/${CODE_PACKAGE}
```
