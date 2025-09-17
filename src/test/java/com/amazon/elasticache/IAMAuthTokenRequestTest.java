package com.amazon.elasticache;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class IAMAuthTokenRequestTest {

    private static final long NOW = 1735689600L;

    private static final String REPLICATION_GROUP_ID = "my-replication-group-id";
    private static final String USER_ID = "my-user-id";
    private static final String REGION = "eu-west-1";

    private static final String ACCESS_KEY_ID = "fakeAccessKeyId";
    private static final String SECRET_KEY = "fakeSecretKey";
    private static final String SESSION_TOKEN = "fakeSessionToken";

    private MockedStatic<Clock> mockedClock;
    private MockedStatic<Instant> mockedInstant;

    @BeforeEach
    public void setup() {
        Instant mockedInstantValue = Instant.ofEpochSecond(NOW);
        mockedInstant = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS);
        mockedInstant.when(Instant::now).thenReturn(mockedInstantValue);

        Clock mockedClockValue = Clock.fixed(mockedInstantValue, ZoneOffset.UTC);
        mockedClock = mockStatic(Clock.class, Mockito.CALLS_REAL_METHODS);
        mockedClock.when(Clock::systemUTC).thenReturn(mockedClockValue);
    }

    @AfterEach
    public void destroy() {
        mockedInstant.close();
        mockedClock.close();
    }

    private static Stream<Arguments> iamAuthTokenProvider() {
        return Stream.of(
                Arguments.of("my-replication-group-id/?Action=connect" +
                                "&User=my-user-id" +
                                "&X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                                "&X-Amz-Date=20250101T000000Z" +
                                "&X-Amz-SignedHeaders=host" +
                                "&X-Amz-Expires=900" +
                                "&X-Amz-Credential=fakeAccessKeyId%2F20250101%2Feu-west-1%2Felasticache%2Faws4_request" +
                                "&X-Amz-Signature=9e361603d85874bce06114c6644725ed113e3b11ae6fa92efdf10c6bcf5de8d7",
                        // above signature is computed using fake static credentials
                        AwsBasicCredentials.create(ACCESS_KEY_ID, SECRET_KEY)),
                Arguments.of("my-replication-group-id/?Action=connect" +
                                "&User=my-user-id" +
                                "&X-Amz-Security-Token=fakeSessionToken" +
                                "&X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                                "&X-Amz-Date=20250101T000000Z" +
                                "&X-Amz-SignedHeaders=host" +
                                "&X-Amz-Expires=900" +
                                "&X-Amz-Credential=fakeAccessKeyId%2F20250101%2Feu-west-1%2Felasticache%2Faws4_request" +
                                "&X-Amz-Signature=bbce2bd61bd78461297ecb92e7001ddbeab2d85906c3cbedad6c9b81fb3466dc",
                        // above signature is computed using fake temporary session credentials
                        AwsSessionCredentials.create(ACCESS_KEY_ID, SECRET_KEY, SESSION_TOKEN))
        );
    }

    @ParameterizedTest
    @MethodSource("iamAuthTokenProvider")
    public void testIAMAuthTokenRequest(String expectedIamAuthToken, AwsCredentials credentials) {
        IAMAuthTokenRequest request = new IAMAuthTokenRequest(USER_ID, REPLICATION_GROUP_ID, REGION);

        URI expectedPreSignedUri = tokenToUri(expectedIamAuthToken);
        URI actualPreSignedUri = tokenToUri(request.toSignedRequestUri(credentials));

        assertEquals(expectedPreSignedUri.getHost(), actualPreSignedUri.getHost());
        assertEquals(expectedPreSignedUri.getPath(), actualPreSignedUri.getPath());
        Assertions.assertIterableEquals(getQueryParams(expectedPreSignedUri), getQueryParams(actualPreSignedUri));
    }

    private static List<NameValuePair> getQueryParams(URI uri) {
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8)
                .stream()
                .sorted(Comparator.comparing(NameValuePair::getName))
                .collect(Collectors.toList());
    }

    private static URI tokenToUri(String token) {
        return URI.create("https://" + token);
    }
}