package org.dekstroza.examples.storm;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Created by deki on 08/01/15.
 * oo-yeah, baby
 */
public class TwitterUtil implements Serializable {

    public static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";
    public static final String OAUTH_SIGNATURE_METHOD = "oauth_signature_method";
    public static final String OAUTH_TOKEN = "oauth_token";
    public static final String TRACK = "track";
    public static final int HTTP_OK = 200;
    public static final String TWITTER_CONSUMER_KEY = "twitter_consumer_key";
    public static final String TWITTER_CONSUMER_SECRET = "twitter_consumer_secret";
    public static final String OAUTH_TOKEN1 = "oauth_token";
    public static final String OAUTH_TOKEN_SECRET = "oauth_token_secret";
    private static final Logger LOG = LoggerFactory.getLogger(TwitterUtil.class);
    private static final String oauth_signature_method = "HMAC-SHA1";
    private static final String SECRET_KEY_SPEC_NAME = "HmacSHA1";
    private static final String TWITTER_ENDPOINT = "https://stream.twitter.com/1.1/statuses/filter.json";
    private static final String AUTH_HEADER_KEY = "Authorization";
    private static final String OAUTH_TIMESTAMP = "oauth_timestamp";
    private static final String OAUTH_SIGNATURE = "oauth_signature";
    private static final String OAUTH_NONCE = "oauth_nonce";
    private static final String OAUTH_VERSION = "oauth_version";
    private static final String OAUTH_VERSION_VALUE = "1.0";
    private String twitter_consumer_key;
    private String twitter_consumer_secret;
    private String oauth_token;
    private String oauth_token_secret;

    public TwitterUtil(final String confFile) {
        try {
            final Properties prop = new Properties();
            final File twitterKeys = new File(confFile);
            final FileInputStream fis = new FileInputStream(twitterKeys);
            prop.load(fis);
            this.twitter_consumer_key = prop.getProperty(TWITTER_CONSUMER_KEY);
            this.twitter_consumer_secret = prop.getProperty(TWITTER_CONSUMER_SECRET);
            this.oauth_token = prop.getProperty(OAUTH_TOKEN1);
            this.oauth_token_secret = prop.getProperty(OAUTH_TOKEN_SECRET);
            prop.clear();
            fis.close();

        } catch (final IOException ioe) {
            LOG.error("Unable to read twitter keys", ioe);
        }
    }

    /**
     * Compute signature method
     *
     * @param baseString Base string
     * @param keyString  Key string
     * @return Signature
     * @throws GeneralSecurityException
     * @throws UnsupportedEncodingException
     */
    String computeSignature(final String baseString, final String keyString) throws GeneralSecurityException, UnsupportedEncodingException {

        final byte[] keyBytes = keyString.getBytes();
        final SecretKey secretKey = new SecretKeySpec(keyBytes, SECRET_KEY_SPEC_NAME);
        final Mac mac = Mac.getInstance(SECRET_KEY_SPEC_NAME);
        mac.init(secretKey);
        final byte[] text = baseString.getBytes();
        final byte[] tt = mac.doFinal(text);
        return new String(Base64.encodeBase64(tt)).trim();
    }

    /**
     * Encode method, as jvm encode method does not work here
     *
     * @param value Value to encode
     * @return Encoded value
     */
    String encode(final String value) {

        try {
            final String encoded = URLEncoder.encode(value, "UTF-8");
            final StringBuilder buf = new StringBuilder(encoded.length());
            char focus;
            for (int i = 0; i < encoded.length(); i++) {
                focus = encoded.charAt(i);
                if (focus == '*') {
                    buf.append("%2A");
                } else if (focus == '+') {
                    buf.append("%20");
                } else if (focus == '%' && (i + 1) < encoded.length() && encoded.charAt(i + 1) == '7' && encoded.charAt(i + 2) == 'E') {
                    buf.append('~');
                    i += 2;
                } else {
                    buf.append(focus);
                }
            }
            return buf.toString();
        } catch (UnsupportedEncodingException ignore) {
            throw new RuntimeException(ignore);
        }

    }

    /**
     * Generate authorization header
     *
     * @param track Words to track in tweets
     * @return Authorization header
     */
    final Map<String, String> generateAuthorizationHeaderAndParams(final String track) {
        final Map<String, String> headerAndParams = new HashMap<String, String>();

        String uuid_string = UUID.randomUUID().toString();
        uuid_string = uuid_string.replaceAll("-", "");
        final String oauth_nonce = uuid_string; // any relatively random alphanumeric string will work here
        headerAndParams.put(OAUTH_NONCE, oauth_nonce);

        // get the timestamp
        final Calendar tempCal = Calendar.getInstance();
        long ts = tempCal.getTimeInMillis();// get current time in milliseconds
        String oauth_timestamp = (new Long(ts / 1000)).toString(); // then divide by 1000 to get seconds
        headerAndParams.put(OAUTH_TIMESTAMP, oauth_timestamp);

        // the parameter string must be in alphabetical order
        // this time, I add 3 extra params to the request, "lang", "result_type" and "q".
        final String parameter_string =
                "oauth_consumer_key=" + twitter_consumer_key + "&oauth_nonce=" + oauth_nonce + "&oauth_signature_method=" + oauth_signature_method +
                        "&oauth_timestamp=" + oauth_timestamp + "&oauth_token=" + encode(oauth_token) + "&oauth_version=1.0&track=" + encode(track);
        LOG.info("parameter_string is:{}", parameter_string);

        String signature_base_string = "GET" + "&" + encode(TWITTER_ENDPOINT) + "&" + encode(parameter_string);
        LOG.info("signature_base_string is:{}", signature_base_string);

        // this time the base string is signed using twitter_consumer_secret + "&" + encode(oauth_token_secret) instead of just twitter_consumer_secret + "&"
        String oauth_signature = "";
        try {
            oauth_signature = computeSignature(signature_base_string, twitter_consumer_secret + "&" + encode(
                    oauth_token_secret));  // note the & at the end. Normally the user access_token would go here, but we don't know it yet for request_token
        } catch (final GeneralSecurityException e) {
            LOG.error("Error computing signature", e);
        } catch (final UnsupportedEncodingException e) {
            LOG.error("Error encoding values for signature", e);
        }
        headerAndParams.put(OAUTH_SIGNATURE, oauth_signature);

        final String authorization_header_string = createAuthHeader(oauth_nonce, oauth_signature, oauth_timestamp);
        LOG.info("authorization_header_string is:{}", authorization_header_string);
        headerAndParams.put(AUTH_HEADER_KEY, authorization_header_string);
        return headerAndParams;
    }

    /**
     * Create Authorization Header method
     *
     * @param oauth_nonce     OAUTH_NONCE
     * @param oauth_signature OAUTH SIGNATURE
     * @param oauth_timestamp OAUTH_TIMESTAMP
     * @return String representing authorization header value
     */
    String createAuthHeader(final String oauth_nonce, final String oauth_signature, final String oauth_timestamp) {
        return "OAuth oauth_consumer_key=\"" + twitter_consumer_key + "\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"" + oauth_timestamp +
                "\",oauth_nonce=\"" + oauth_nonce + "\",oauth_version=\"1.0\",oauth_signature=\"" + encode(oauth_signature) + "\",oauth_token=\""
                + encode(oauth_token) + "\"";

    }

    /**
     * Create URIBuilder
     *
     * @param headerAndParams Map containing params to set in URI
     * @param track           Tracking keywords to set in URI
     * @return URIBuilder
     */
    URIBuilder createURIBuilder(final Map<String, String> headerAndParams, final String track) {
        try {
            final URIBuilder builder = new URIBuilder(TWITTER_ENDPOINT);
            builder.setParameter(OAUTH_CONSUMER_KEY, twitter_consumer_key);
            builder.setParameter(OAUTH_NONCE, headerAndParams.get(OAUTH_NONCE));
            builder.setParameter(OAUTH_SIGNATURE_METHOD, oauth_signature_method);
            builder.setParameter(OAUTH_TIMESTAMP, headerAndParams.get(OAUTH_TIMESTAMP));
            builder.setParameter(OAUTH_TOKEN, oauth_token);
            builder.setParameter(OAUTH_VERSION, OAUTH_VERSION_VALUE);
            builder.setParameter(TRACK, track);
            return builder;
        } catch (URISyntaxException e) {
            LOG.error("Error creating URI builder:", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Create HTTP Get request
     *
     * @param headerAndParams headers and params
     * @param track           tracking strings
     * @return HTTP Get object
     */
    HttpGet createHTTPGet(final Map<String, String> headerAndParams, final String track) {
        try {
            return new HttpGet(createURIBuilder(headerAndParams, track).build());
        } catch (final URISyntaxException urise) {
            throw new RuntimeException(urise);
        }
    }

    /**
     * Open input stream from twitter to read tweeets
     *
     * @param track Words tracked in tweets
     * @return InputStream
     */
    public InputStream openTwitterStreamForTrack(final String track) {

        final Map<String, String> headerAndParams = generateAuthorizationHeaderAndParams(track);
        final HttpGet get = createHTTPGet(headerAndParams, track);
        get.setHeader(AUTH_HEADER_KEY, headerAndParams.get(AUTH_HEADER_KEY));
        final HttpClient client = HttpClientBuilder.create().build();
        try {
            final HttpResponse response = client.execute(get);
            LOG.info("Response status is:{}", response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() == HTTP_OK) {
                return response.getEntity().getContent();
            } else {
                throw new RuntimeException(
                        "Twitter did not return HTTP OK for the request, returned HTTP CODE was " + response.getStatusLine().getStatusCode());
            }
        } catch (final IOException e) {
            LOG.error("Error reading input stream from twitter:", e);
            throw new RuntimeException(e);
        }

    }

}
