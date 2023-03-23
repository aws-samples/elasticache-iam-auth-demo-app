# Elasticache IAM authentication demo application

You can use this java-based application which uses the Redis Lettuce client to demo the IAM based Authentication to access your Elasticache for Redis cluster.
We use a Redis credentials provider using the SigV4 IAM Auth token generation.

NOTE: Make sure that The EC2 instance is in the same VPC as the ElastiCache cluster. Also this application works only with Elasticache for Redis version 7.0 or higher with TLS enabled.

## Getting started with setting up the EC2 instance to run the Demo application.

### Setup Java 8

To setup Java 8 on your instance, follow these instructions:

Check which version of Java is installed, if any.

```$ java -version```

if the appropriate version is not installed, then install Java 1.8

```$ sudo yum install java-1.8.0```

Change the Java version to 1.8 if you have multiple version of Java

```$ sudo alternatives --config java```

### Setup the demo Application

Now git clone the repo locally onto the EC2 instance from where you want to connect to ElastiCache for Redis.

```$ git clone https://github.com/aws-samples/elasticache-iam-auth-demo-app.git```

```$ cd elasticache-iam-auth-demo-app```

You can build the application from the source code using 

```$ mvn clean verify```

which generates a .jar file, which you can then use to run you java application.

### To generate a token using the demo app use the following command
```
$ java -cp target/ElastiCacheIAMAuthDemoApp-1.0-SNAPSHOT.jar \
	com.amazon.elasticache.IAMAuthTokenGeneratorApp \
	--region us-east-1 \
	--replication-group-id iam-test-rg-01 \
	--user-id iam-test-user-01
```

### Now to connect to a cluster using the demo app

The app uses the default [Default Credentials provider](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html) to generate the IAM Auth token (signs the token) using your current AWS caller identity. As host you can configure the primary or reader endpoints, or configuration endpoint for cluster-mode enabled replication groups. If you setup your IAM role as EC2 instance profile, then the temporary credentials for the IAM role will be automatically managed for you. 

NOTE:
* your application needs to run in the same VPC as your ElastiCache replication group as well as security group to allow traffic between ElastiCache and EC2
* replace the ```<host>``` with the cluster endpoint which you can fetch by looking into the cluster details section of your elasticache cluster. It could look something like ```elc-tutorial.lnvbt6.clustercfg.use1.cache.amazonaws.com```

```
$ java -jar target/ElastiCacheIAMAuthDemoApp-1.0-SNAPSHOT.jar \
	--redis-host <host> \
	--region us-east-1 \
	--replication-group-id iam-test-rg-01 \
	--user-id iam-test-user-01 \
	--tls
```

For cluster-mode enabled replication groups, please add the `--cluster-mode` flag.

The demo app creates a new connection to the host using the IAM user identity and generates an IAM authentication token.
