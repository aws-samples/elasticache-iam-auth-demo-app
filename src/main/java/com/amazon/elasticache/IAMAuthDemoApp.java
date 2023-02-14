package com.amazon.elasticache;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Demo App for ElastiCache for Redis IAM Authentication
 */
public class IAMAuthDemoApp {

    @Parameter(names = {"-h", "--help"}, help = true)
    private boolean help;

    @Parameter(names = {"--redis-host"})
    private String redisHost = "localhost";

    @Parameter(names = {"--redis-port"})
    private int redisPort = 6379;

    @Parameter(names = {"--tls"})
    private boolean tlsEnabled = true;

    @Parameter(names = {"--cluster-mode"})
    private boolean clusterModeEnabled = false;

    @Parameter(names = {"--user-id"})
    private String userId;

    @Parameter(names = {"--password"})
    private String password;

    @Parameter(names = {"--replication-group-id"})
    private String replicationGroupId;

    @Parameter(names = {"--region"})
    private String region = "us-east-1";

    @Parameter(names = {"--connect-sleep-time"})
    private long connectSleepTimeSeconds = 1;

    private long numConnections = 0;

    public static void main(String[] args) {
        IAMAuthDemoApp app = new IAMAuthDemoApp();
        JCommander jc = JCommander.newBuilder().addObject(app).build();
        jc.parse(args);

        if (app.help) {
            jc.usage();
            return;
        }
        app.run();
    }

    private void run() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(redisHost),
            "redisHost cannot be null or emtpy");

        // Construct Redis URL with credentials provider.
        RedisURI redisURI = RedisURI.builder()
            .withHost(redisHost)
            .withPort(redisPort)
            .withSsl(tlsEnabled)
            .withAuthentication(getCredentialsProvider())
            .build();

        if (!clusterModeEnabled) {
            runRedisExample(redisURI);
        } else {
            runRedisClusterExample(redisURI);
        }
    }

    private void runRedisExample(RedisURI redisURI) {
        // Create a new Lettuce Redis client
        RedisClient redisClient =  RedisClient.create(redisURI);

        // Run until demo app is stopped
        while (true) {

            // Connect a new client and perform a read command.
            // This will automatically fetch an IAM Auth token from the credentials provider,
            // or reuse a cached token for `authTokenTimeoutSeconds`.
            StatefulRedisConnection<String, String> connection = redisClient.connect();
            connection.sync().get(getRandKey());

            numConnections += 1;
            System.out.println("=> Successful connections: " + numConnections);

            // Sleep for a bit
            Uninterruptibles.sleepUninterruptibly(connectSleepTimeSeconds, TimeUnit.SECONDS);

            // Close Redis client connection
            connection.close();
        }
    }

    private void runRedisClusterExample(RedisURI redisURI) {
        RedisClusterClient redisClusterClient = RedisClusterClient.create(redisURI);

        // Run until demo app is stopped
        while (true) {

            // Connect a new client and perform a read command.
            // This will automatically fetch an IAM Auth token from the credentials provider,
            // or reuse a cached token for `authTokenTimeoutSeconds`.
            StatefulRedisClusterConnection<String, String> connection = redisClusterClient.connect();
            connection.sync().get(getRandKey());

            numConnections += 1;
            System.out.println("=> Successful connections: " + numConnections);

            // Sleep for a bit
            Uninterruptibles.sleepUninterruptibly(connectSleepTimeSeconds, TimeUnit.SECONDS);

            // Close Redis client connection
            connection.close();
        }
    }

    private RedisCredentialsProvider getCredentialsProvider() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(userId),
            "userId cannot be be null or emtpy");

        // Use a static username and password
        if (!Strings.isNullOrEmpty(password)) {
            return new RedisStaticCredentialsProvider(userId, password);
        }

        Preconditions.checkArgument(!Strings.isNullOrEmpty(replicationGroupId),
            "replicationGroupId cannot be be null or emtpy");

        // Create a default AWS Credentials provider.
        // This will look for AWS credentials defined in environment variables or system properties.
        AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();

        // Create an IAM Auth Token request. Once this request is signed it can be used as an
        // IAM Auth token for Elasticache Redis.
        IAMAuthTokenRequest iamAuthTokenRequest = new IAMAuthTokenRequest(userId, replicationGroupId, region);

        // Create a Redis credentials provider using IAM credentials.
        return new RedisIAMAuthCredentialsProvider(
            userId, iamAuthTokenRequest, awsCredentialsProvider);
    }

    private String getRandKey() {
        return UUID.randomUUID().toString();
    }
}
