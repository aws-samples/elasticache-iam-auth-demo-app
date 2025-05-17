package com.amazon.elasticache;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
/* Show the arn of the Credentials if needed
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
*/

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;

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
    private boolean tlsEnabled = false;

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

        // Set DNS cache TTL
        java.security.Security.setProperty("networkaddress.cache.ttl", "10");
        
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
        boolean done = false;
        // Create a new Lettuce Redis client
        RedisClient redisClient =  RedisClient.create(redisURI);
        redisClient.setOptions(ClientOptions.builder().autoReconnect(true)
            .pingBeforeActivateConnection(true)
            .build());
        StatefulRedisConnection<String, String> connection = null;
        connection = redisClient.connect();

        // Run until demo app is stopped
        while (!done) {

            // Connect a new client and perform a read command.
            // This will automatically fetch an IAM Auth token from the credentials provider,
            // or reuse a cached token for `authTokenTimeoutSeconds`.
            try {
                connection.sync().get(getRandKey());

                numConnections += 1;
                System.out.println("=> Successful sent commands: " + numConnections);
            } catch(RedisException e) {
                e.printStackTrace();
            }

            // Sleep for a bit
            Uninterruptibles.sleepUninterruptibly(connectSleepTimeSeconds, TimeUnit.SECONDS);
        }

        // Close Redis client connection
        if(connection != null) connection.close();
    }

    private void runRedisClusterExample(RedisURI redisURI) {
        boolean done = false;
        RedisClusterClient redisClusterClient = RedisClusterClient.create(redisURI);
        ClusterTopologyRefreshOptions topologyOptions = ClusterTopologyRefreshOptions.builder()
            .enableAllAdaptiveRefreshTriggers()
            .enablePeriodicRefresh()
            .dynamicRefreshSources(true)
            .build();
        redisClusterClient.setOptions(ClusterClientOptions.builder()
        .topologyRefreshOptions(topologyOptions)
        .autoReconnect(true)
        .pingBeforeActivateConnection(true)
        .nodeFilter(it -> 
            ! (it.is(RedisClusterNode.NodeFlag.FAIL) 
            || it.is(RedisClusterNode.NodeFlag.EVENTUAL_FAIL) 
            || it.is(RedisClusterNode.NodeFlag.NOADDR)))
        .build());
        StatefulRedisClusterConnection<String, String> connection = null;
        connection = redisClusterClient.connect();
        connection.setReadFrom(ReadFrom.ANY);

        // Run until demo app is stopped
        while (!done) {

            // Connect a new client and perform a read command.
            // This will automatically fetch an IAM Auth token from the credentials provider,
            // or reuse a cached token for `authTokenTimeoutSeconds`.
            try {
                connection.sync().get(getRandKey());

                numConnections += 1;
                System.out.println("=> Successful sent commands: " + numConnections);
            } catch(RedisException e) {
                e.printStackTrace();
            }

            // Sleep for a bit
            Uninterruptibles.sleepUninterruptibly(connectSleepTimeSeconds, TimeUnit.SECONDS);
        }

        // Close Redis client connection
        if(connection != null) connection.close();
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
        // This will look for AWS credentials as defined in the doc.
        // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html
        AwsCredentialsProvider awsCredentialsProvider = DefaultCredentialsProvider.create();

        /* Show the arn of the Credentials if needed
        // Show the current role of the Credentials
        StsClient stsClient = StsClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.AWS_GLOBAL)  // STS is a global service
                .build();
        GetCallerIdentityRequest request = GetCallerIdentityRequest.builder().build();
        GetCallerIdentityResponse response = stsClient.getCallerIdentity(request);
        String arn = response.arn();
        System.out.println("The role of the Credential is: " + arn);
        stsClient.close();
        */

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
