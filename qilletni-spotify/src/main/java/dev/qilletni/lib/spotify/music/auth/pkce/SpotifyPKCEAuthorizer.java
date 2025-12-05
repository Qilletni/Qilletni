package dev.qilletni.lib.spotify.music.auth.pkce;

import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.lib.spotify.async.ExecutorServiceUtility;
import dev.qilletni.lib.spotify.async.ThrowableVoid;
import dev.qilletni.lib.spotify.music.SpotifyAuthUtility;
import dev.qilletni.lib.spotify.music.auth.SpotifyAuthorizer;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.net.URIBuilder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyApiThreading;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.User;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpotifyPKCEAuthorizer implements SpotifyAuthorizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyPKCEAuthorizer.class);

    private static final String CACHED_CREDS_NAME = "cached-creds";
    
    private final SpotifyApi spotifyApi;

    private final PackageConfig packageConfig;
    private final String codeChallenge;
    private final String codeVerifier;
    private final ExecutorService executorService;
    private final URI redirectUri;
    
    private se.michaelthelin.spotify.model_objects.specification.User currentUser;

    public SpotifyPKCEAuthorizer(PackageConfig packageConfig, String codeChallenge, String codeVerifier, String clientId, String redirectUri) {
        this.packageConfig = packageConfig;
        this.codeChallenge = codeChallenge;
        this.codeVerifier = codeVerifier;
        this.executorService = Executors.newCachedThreadPool();
        this.redirectUri = SpotifyHttpManager.makeUri(redirectUri);

        spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setRedirectUri(this.redirectUri)
                .build();
    }

    /**
     * Creates a {@link SpotifyPKCEAuthorizer} with generated code verifier and challenges.
     *
     * @return The created authorizer
     */
    public static SpotifyPKCEAuthorizer createWithCodes(PackageConfig packageConfig, String clientId, String redirectUri) {
        var codeVerifier = SpotifyAuthUtility.generateCodeVerifier(43, 128);
        var codeChallenge = SpotifyAuthUtility.generateCodeChallenge(codeVerifier);

        return new SpotifyPKCEAuthorizer(packageConfig, codeChallenge, codeVerifier, clientId, redirectUri);
    }

    /**
     * Populates {@link #spotifyApi} with correct access and refresh tokens, starting a loop to automatically refresh.
     *
     * @return The populated {@link SpotifyApi}
     */
    @Override
    public CompletableFuture<SpotifyApi> authorizeSpotify() {
        var completableFuture = new CompletableFuture<SpotifyApi>();

        packageConfig.get(CACHED_CREDS_NAME).ifPresentOrElse(cachedCreds -> {
            refreshTokens(AuthCodeCredentials.fromRefreshToken(cachedCreds));

            try {
                var credentials = performImmediateRefresh();
                beginRefreshLoop(credentials.expiresIn());
                updateCurrentUser().thenRun(() -> completableFuture.complete(spotifyApi));
            } catch (IOException | ParseException | SpotifyWebApiException e) {
                LOGGER.error("Exception occurred while using cached creds, resetting and trying again", e);
                packageConfig.remove(CACHED_CREDS_NAME);
                packageConfig.saveConfig();
                
                getManualCreds(completableFuture);
            }
        }, () -> getManualCreds(completableFuture));

        return completableFuture;
    }
    
    private void getManualCreds(CompletableFuture<SpotifyApi> completableFuture) {
        LOGGER.debug("Manual creds!");

        try {
            getCodeFromUser().thenCompose(this::setupSpotifyApi)
                    .thenAccept(this::beginRefreshLoop)
                    .thenCompose(_ -> updateCurrentUser())
                    .thenRun(() -> completableFuture.complete(spotifyApi));
        } catch (Exception e) {
            completableFuture.completeExceptionally(e);
        }
    }

    @Override
    public void shutdown() {
        ExecutorServiceUtility.shutdown(executorService);
        ExecutorServiceUtility.shutdown(SpotifyApiThreading.THREAD_POOL);
    }

    /**
     * Opens the Spotify authentication URL and gets the authentication code from the callback of it.
     *
     * @return The authentication code future
     * @throws Exception
     */
    private CompletableFuture<String> getCodeFromUser() throws Exception {
        var scopes = List.of(
                "user-read-email",
                "user-library-modify",
                "user-library-read",
                "user-modify-playback-state",
                "user-read-playback-state",
                "user-read-playback-position",
                "user-read-currently-playing",
                "playlist-read-private",
                "playlist-modify-private",
                "playlist-modify-public"
        );
        
        var authorizationCodeUriRequest = spotifyApi.authorizationCodePKCEUri(codeChallenge)
          .scope(String.join(",", scopes)).build();

        authorizationCodeUriRequest.executeAsync().thenAccept(uri -> {
            LOGGER.info("Redirecting to: {}", uri);
            
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {} // Might not be supported
        });

        var codeFuture = new CompletableFuture<String>();

        var server = setupCallbackServer(codeFuture);
        server.join();

        return codeFuture;
    }

    /**
     * Sets up the server on port 8088 and completes the future with the {@code code} query parameter.
     *
     * @param codeFuture The future to complete with the code
     * @return The created server
     * @throws Exception
     */
    private Server setupCallbackServer(CompletableFuture<String> codeFuture) throws Exception {
        var callbackPort = redirectUri.getPort();
        var contextPath = redirectUri.getPath();

        var server = new Server(callbackPort); // Set your desired port
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                if (!request.getHttpURI().getPath().equals(contextPath)) {
                    return false;
                }

                var code = new URIBuilder(request.getHttpURI().toURI()).getFirstQueryParam("code").getValue();

                response.setStatus(200);
                response.write(true, ByteBuffer.wrap("Thank you! You may close this tab.\n".getBytes(StandardCharsets.UTF_8)), callback);

                server.stop();

                codeFuture.complete(code);
                return true;
            }
        });

        server.start();
        return server;
    }

    /**
     * Sets up the spotify API and returns how many seconds until it expires.
     *
     * @param code The code given by the redirect URL
     * @return The future of the request
     */
    private CompletableFuture<Integer> setupSpotifyApi(String code) {
        var authorizationCodePKCERequest = spotifyApi.authorizationCodePKCE(code, codeVerifier).build();

        return authorizationCodePKCERequest.executeAsync().thenApply(authorizationCodeCredentials -> {
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

            packageConfig.set(CACHED_CREDS_NAME, authorizationCodeCredentials.getRefreshToken());
            packageConfig.saveConfig();

            LOGGER.debug("Expires in {}s", authorizationCodeCredentials.getExpiresIn());

            return authorizationCodeCredentials.getExpiresIn();
        }).exceptionally(new ThrowableVoid<>("Exception while getting access and refresh tokens", 0));
    }

    /**
     * Starts a loop on another thread to continuously refresh the token in {@link #spotifyApi} before it's going to be expired.
     *
     * @param initialExpiresIn
     */
    private void beginRefreshLoop(int initialExpiresIn) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(calculateExpirySleepTime(initialExpiresIn));

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        var authCodeCredentials = performImmediateRefresh();

                        LOGGER.debug("Expires in {}s", authCodeCredentials.expiresIn());

                        Thread.sleep(calculateExpirySleepTime(authCodeCredentials.expiresIn()));
                    } catch (IOException | SpotifyWebApiException | ParseException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executorService);
    }

    /**
     * Updates the {@link #spotifyApi} with the new {@link AuthCodeCredentials} values.
     *
     * @param authCodeCredentials The {@link AuthCodeCredentials} to update with
     */
    private void refreshTokens(AuthCodeCredentials authCodeCredentials) {
        synchronized (spotifyApi) {
            spotifyApi.setAccessToken(authCodeCredentials.accessToken());
            spotifyApi.setRefreshToken(authCodeCredentials.refreshToken());
        }
    }

    /**
     * Immediately refreshes the tokens and updates {@link #spotifyApi}.
     *
     * @return The refreshed credentials
     */
    private AuthCodeCredentials performImmediateRefresh() throws IOException, ParseException, SpotifyWebApiException {
        var authorizationCodePKCERefreshRequest = spotifyApi.authorizationCodePKCERefresh().build();

        var authorizationCodeCredentials = authorizationCodePKCERefreshRequest.execute();

        var authCodeCredentials = AuthCodeCredentials.from(authorizationCodeCredentials);
        refreshTokens(authCodeCredentials);

        packageConfig.set(CACHED_CREDS_NAME, authCodeCredentials.refreshToken());
        packageConfig.saveConfig();

        return authCodeCredentials;
    }

    /**
     * Sets the {@link #currentUser} to the current user that has been authenticated.
     * 
     * @return The future of the user request
     */
    private CompletableFuture<Void> updateCurrentUser() {
        return spotifyApi.getCurrentUsersProfile().build().executeAsync()
                .thenAccept(user -> currentUser = user);
    }

    @Override
    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    @Override
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * Calculates the time to sleep between token refresh iterations, based off of the previous token expiry time.
     *
     * @param expiresInSeconds The time in seconds the token will expire in
     * @return The millisecond time to wait
     */
    private long calculateExpirySleepTime(int expiresInSeconds) {
        if (expiresInSeconds == 0) {
            return 0;
        }

        return (expiresInSeconds - 10) * 1000L;
    }

    public record AuthCodeCredentials(String accessToken, String refreshToken, int expiresIn) {
        static AuthCodeCredentials from(AuthorizationCodeCredentials authorizationCodeCredentials) {
            return new AuthCodeCredentials(authorizationCodeCredentials.getAccessToken(), authorizationCodeCredentials.getRefreshToken(), authorizationCodeCredentials.getExpiresIn());
        }

        public static AuthCodeCredentials fromRefreshToken(String refreshToken) {
            return new AuthCodeCredentials("", refreshToken, -1);
        }
    }
}
