package com.amazon.elasticache;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
/* Show the arn of the Credentials if needed */
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;


/**
 * Utility app to create an IAM Auth token
 */
public class IAMAuthTokenGeneratorApp {

    @Parameter(names = {"-h", "--help"}, help = true)
    private boolean help;

    @Parameter(names = {"--user-id"})
    private String userId;

    @Parameter(names = {"--replication-group-id"})
    private String replicationGroupId;

    @Parameter(names = {"--region"})
    private String region = "us-east-1";

    public static void main(String[] args) throws Exception {
        IAMAuthTokenGeneratorApp app = new IAMAuthTokenGeneratorApp();
        JCommander jc = JCommander.newBuilder().addObject(app).build();
        jc.parse(args);

        if (app.help) {
            jc.usage();
            return;
        }
        app.run();
    }

    private void run() throws Exception {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(userId),
            "userId cannot be be null or emtpy");

        Preconditions.checkArgument(!Strings.isNullOrEmpty(replicationGroupId),
            "replicationGroupId cannot be be null or emtpy");

        // Create a default AWS Credentials provider.
        // This will look for AWS credentials as defined in the doc.
        // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html
        AwsCredentialsProvider awsCredentialsProvider = DefaultCredentialsProvider.create();

        /* Show the arn of the Credentials if needed */
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
        

        // Create an IAM Auth Token request and signed it using the AWS credentials.
        // The pre-signed request URL is used as an IAM Auth token for Elasticache Redis.
        IAMAuthTokenRequest iamAuthTokenRequest = new IAMAuthTokenRequest(userId, replicationGroupId, region);
        String iamAuthToken = iamAuthTokenRequest.toSignedRequestUri(awsCredentialsProvider.resolveCredentials());

        System.out.println(iamAuthToken);
    }
}
