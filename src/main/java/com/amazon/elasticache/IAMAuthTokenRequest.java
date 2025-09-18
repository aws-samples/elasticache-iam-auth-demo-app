package com.amazon.elasticache;

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

import java.net.URI;

public class IAMAuthTokenRequest {
    private static final SdkHttpMethod REQUEST_METHOD = SdkHttpMethod.GET;
    private static final String REQUEST_PROTOCOL = "http://";
    private static final String PARAM_ACTION = "Action";
    private static final String PARAM_USER = "User";
    private static final String ACTION_NAME = "connect";
    private static final String SERVICE_NAME = "elasticache";
    private static final Duration TOKEN_EXPIRY_DURATION_SECONDS = Duration.ofSeconds(900);

    private final String userId;
    private final String replicationGroupId;
    private final String region;

    public IAMAuthTokenRequest(String userId, String replicationGroupId, String region) {
        this.userId = userId;
        this.replicationGroupId = replicationGroupId;
        this.region = region;
    }

    public String toSignedRequestUri(AwsCredentials credentials) {
        SdkHttpFullRequest request = getSignableRequest();

        // Sign the canonical request
        request = sign(request, credentials);

        // Return the signed URI
        return request.getUri().toString().replace(REQUEST_PROTOCOL, "");
    }

    private SdkHttpFullRequest getSignableRequest() {
        return SdkHttpFullRequest.builder()
            .method(REQUEST_METHOD)
            .uri(getRequestUri())
            .appendRawQueryParameter(PARAM_ACTION, ACTION_NAME)
            .appendRawQueryParameter(PARAM_USER, userId)
            .build();
    }

    private URI getRequestUri() {
        return URI.create(String.format("%s%s/", REQUEST_PROTOCOL, replicationGroupId));
    }

    private SdkHttpFullRequest sign(SdkHttpFullRequest request, AwsCredentials credentials) {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SignedRequest signedRequest = signer.sign(r -> r.identity(credentials)
            .request(request)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE_NAME)
            .putProperty(AwsV4HttpSigner.REGION_NAME, region)
            .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4HttpSigner.AuthLocation.QUERY_STRING)
            .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, TOKEN_EXPIRY_DURATION_SECONDS)
            .build()
        );
        
        return (SdkHttpFullRequest)signedRequest.request();
    }
}