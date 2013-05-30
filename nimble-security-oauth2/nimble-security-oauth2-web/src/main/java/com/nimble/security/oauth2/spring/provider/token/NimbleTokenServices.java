package com.nimble.security.oauth2.spring.provider.token;

import com.nimble.security.oauth2.spring.common.NimbleRefreshToken;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;

/**
 * Date: 5/16/13
 * Time: 1:53 PM
 */
public class NimbleTokenServices extends DefaultTokenServices {
    protected Log log = LogFactory.getLog(getClass());
    private TokenStore tokenStore = null;

    public void setTokenStore(TokenStore tokenStore) {
        super.setTokenStore(tokenStore);
        this.tokenStore = tokenStore;
    }

    public OAuth2AccessToken refreshAccessToken(String refreshTokenValue, AuthorizationRequest request)
            throws AuthenticationException {
        OAuth2AccessToken accessToken = super.refreshAccessToken(refreshTokenValue, request);
        if (accessToken != null) {
            postProcessRefreshAccessToken(refreshTokenValue, request, accessToken);
        } else {
            log.warn("refreshAccessToken: no exception thrown from super and yet no access token returned.");
        }
        return accessToken;
    }

    /**
     * This callback will allow for any desired post processing
     *
     * @param refreshTokenValue - token value to look up refresh token
     * @param request           - details about the AuthorizationRequest
     * @param accessToken       - the new access token generated by the refresh token
     */
    protected void postProcessRefreshAccessToken(String refreshTokenValue, AuthorizationRequest request, OAuth2AccessToken accessToken) {
        //lets keep track of how many times the refresh token is used
        OAuth2RefreshToken refreshToken = accessToken.getRefreshToken();

        if (refreshToken == null || !(refreshToken instanceof NimbleRefreshToken)) {
            log.debug("postProcessRefreshAccessToken: refresh token attached to refreshed access token was either null or of unknown type");
            refreshToken = tokenStore.readRefreshToken(accessToken.getRefreshToken().getValue());
        }

        if (refreshToken != null && refreshToken instanceof NimbleRefreshToken) {
            NimbleRefreshToken token = (NimbleRefreshToken) refreshToken;
            token.setTimesUsed(token.getTimesUsed() + 1);
            tokenStore.storeRefreshToken(token, null);
        } else {
            log.warn("postProcessRefreshAccessToken: unable to find refresh token for processing. refreshToken="
                    + refreshTokenValue + ", accessToken=" + (accessToken != null ? accessToken.getValue() : "unknown"));
        }

    }
}
