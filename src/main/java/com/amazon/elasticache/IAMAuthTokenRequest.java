package com.amazon.elasticache;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import java.net.URI;
import java.time.Instant;

public class IAMAuthTokenRequest {
    private static final SdkHttpMethod REQUEST_METHOD = SdkHttpMethod.GET;
    private static final String REQUEST_PROTOCOL = "http://";
    private static final String PARAM_ACTION = "Action";
    private static final String PARAM_USER = "User";
    private static final String ACTION_NAME = "connect";
    private static final String SERVICE_NAME = "elasticache";
    private static final Duration TOKEN_EXPIRY_DURATION_SECONDS = Duration.ofSeconds(900);

    private final String userId;
    private final Boolean serverless;
    private final String replicationGroupId;
    private final String region;

    public IAMAuthTokenRequest(String userId, String replicationGroupId, String region, Boolean serverless) {
        this.userId = userId;
        this.replicationGroupId = replicationGroupId;
        this.region = region;
        this.serverless = serverless;
    }

    public String toSignedRequestUri(AwsCredentials credentials) {
        SdkHttpFullRequest request = getSignableRequest();

        // Sign the canonical request
        request = sign(request, credentials);

        // Return the signed URI
        return request.getUri().toString().replace(REQUEST_PROTOCOL, "");
    }

    private SdkHttpFullRequest getSignableRequest() {
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(REQUEST_METHOD)
                .uri(getRequestUri())
                .appendRawQueryParameter(PARAM_ACTION, ACTION_NAME)
                .appendRawQueryParameter(PARAM_USER, userId);

        // if serverless is true then add the 'ServerlessCache' ResourceType parameter
        if (serverless) {
            requestBuilder.appendRawQueryParameter("ResourceType", "ServerlessCache");
        }

        SdkHttpFullRequest request = requestBuilder.build();
        return request;
    }

    private URI getRequestUri() {
        return URI.create(String.format("%s%s/", REQUEST_PROTOCOL, replicationGroupId));
    }

    private SdkHttpFullRequest sign(SdkHttpFullRequest request, AwsCredentials credentials) {
        Instant expiryInstant = Instant.now().plus(TOKEN_EXPIRY_DURATION_SECONDS);
        Aws4Signer signer = Aws4Signer.create();
        Aws4PresignerParams signerParams = Aws4PresignerParams.builder()
                .signingRegion(Region.of(region))
                .awsCredentials(credentials)
                .signingName(SERVICE_NAME)
                .expirationTime(expiryInstant)
                .build();
        return signer.presign(request, signerParams);
    }
}