package com.nimble.security.oauth2.spring.provider.token.dao;

import com.nimble.security.oauth2.spring.common.NimbleAccessToken;
import com.nimble.security.oauth2.spring.provider.authentication.IdAwareOAuth2Authentication;
import com.nimble.security.oauth2.spring.provider.token.dao.sql.AbstractAccessTokenMapper;
import com.nimble.security.oauth2.spring.provider.token.dao.sql.NimbleAccessTokenMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.AuthenticationKeyGenerator;
import org.springframework.security.oauth2.provider.token.DefaultAuthenticationKeyGenerator;
import org.springframework.util.Assert;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

/**
 * Date: 5/2/13
 * Time: 3:51 PM
 */
public class JdbcAccessTokenDAO implements AccessTokenDAO<OAuth2AccessToken> {
    private final JdbcTemplate jdbcTemplate;
    protected Log log = LogFactory.getLog(getClass());
    private SimpleJdbcInsert insert;
    private AuthenticationKeyGenerator authenticationKeyGenerator = new DefaultAuthenticationKeyGenerator();
    private AbstractAccessTokenMapper<NimbleAccessToken> accessTokenMapper = new NimbleAccessTokenMapper();
    //private String selectAccessTokenAuthenticationSql = "select token_id, token from oauth_access_token where authentication_id = ?";;
    private String selectAccessTokenAuthenticationSql = "select * from oauth2_access_token where authentication_id = ?";
    private String selectAccessTokenByTokenIdSql = "select * from oauth2_access_token where access_token = ?";
    private String deleteAccessTokenSql = "delete from oauth2_access_token where access_token = ?";
    private String deleteAccessTokenByRefreshTokenSql = "delete from oauth2_access_token where refresh_token = ?";
    private String insertAccessTokenSql = "insert into oauth2_access_token (access_token, token_type, scope, expiration, client_id, authentication, refresh_token) values (?, ?, ?, ?, ?, ?, ?)";
    private String updateAccessTokenSql = "update oauth2_access_token set access_token=? token_type=? scope=? expiration=? client_id=? authentication=? refresh_token=? where id=?";

    public JdbcAccessTokenDAO(DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource required");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(jdbcTemplate);
        insert.setTableName("oauth2_access_token");
        insert.setColumnNames(Arrays.asList("access_token", "token_type", "scope", "expiration", "refresh_token", "authentication_id", "is_encrypted", "additional_info"));
        insert.setGeneratedKeyName("id");
        insert.compile();
    }

    public void setAccessTokenMapper(AbstractAccessTokenMapper accessTokenMapper) {
        this.accessTokenMapper = accessTokenMapper;
    }

    public void setSelectAccessTokenAuthenticationSql(String selectAccessTokenAuthenticationSql) {
        this.selectAccessTokenAuthenticationSql = selectAccessTokenAuthenticationSql;
    }

    public void setAuthenticationKeyGenerator(AuthenticationKeyGenerator authenticationKeyGenerator) {
        this.authenticationKeyGenerator = authenticationKeyGenerator;
    }

    public void setInsertAccessTokenSql(String insertAccessTokenSql) {
        this.insertAccessTokenSql = insertAccessTokenSql;
    }

    public OAuth2AccessToken readAccessToken(String tokenValue) {
        NimbleAccessToken accessToken = null;

        try {
            accessToken = jdbcTemplate.queryForObject(selectAccessTokenByTokenIdSql, accessTokenMapper, tokenValue);
        } catch (EmptyResultDataAccessException e) {
            if (log.isInfoEnabled()) {
                log.debug("Failed to find access token for token value " + tokenValue);
            }
        } catch (IllegalArgumentException e) {
            log.error("Could not extract access token for token value " + tokenValue);
        }


        return accessToken;
    }

    public void removeAccessToken(OAuth2AccessToken token) {
        jdbcTemplate.update(deleteAccessTokenSql, token.getValue());
    }

    public OAuth2AccessToken getAccessToken(OAuth2Authentication authentication, String authenticationId) {
        NimbleAccessToken accessToken = null;

        if (authenticationId != null) {
            try {
                accessToken = jdbcTemplate.queryForObject(selectAccessTokenAuthenticationSql, accessTokenMapper, authenticationId);
            } catch (EmptyResultDataAccessException e) {
                if (log.isInfoEnabled()) {
                    log.debug("Failed to find access token for authentication " + authentication);
                }
            } catch (IllegalArgumentException e) {
                log.error("Could not extract access token for authentication " + authentication);
            }
        }

        return accessToken;
    }

    public Collection<OAuth2AccessToken> findTokensByUserName(String userName) {
        //TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Collection<OAuth2AccessToken> findTokensByClientId(String clientId) {
        //TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void storeAccessToken(OAuth2AccessToken base, String authenticationId, OAuth2Authentication authentication) {
        NimbleAccessToken token = createAccessToken(base);
        int id = token.getId();
        if (id <= 0) {
            log.debug("Creating storeAccessToken: " + token);

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("access_token", token.getValue(), Types.VARCHAR);
            params.addValue("token_type", token.getTokenType(), Types.VARCHAR);
            params.addValue("scope", StringUtils.collectionToCommaDelimitedString(token.getScope()), Types.VARCHAR);
            params.addValue("expiration", token.getExpiration(), Types.TIMESTAMP);
            params.addValue("refresh_token", token.getRefreshToken(), Types.VARCHAR);
            params.addValue("authentication_id", authenticationId, Types.VARCHAR);
            params.addValue("is_encrypted", token.isEncrypted(), Types.TINYINT);
            params.addValue("additional_info", SerializationUtils.serialize(token.getAdditionalInformation()), Types.BLOB);

            java.lang.Number n = insert.executeAndReturnKey(params);
            if (n != null) {
                id = n.intValue();
            } else {
                log.error("storeAccessToken: Expected a return ID from insert but none received!");
            }
        } else {
            log.debug("Updating storeAccessToken: " + id);
            jdbcTemplate.update(updateAccessTokenSql, new Object[]{token.getValue(), token.getTokenType(), StringUtils.collectionToCommaDelimitedString(token.getScope()),
                    token.getExpiration(), token.getRefreshToken(), authenticationId, token.isEncrypted(), SerializationUtils.serialize(token.getAdditionalInformation()),
                    id
            }, new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.INTEGER, Types.TINYINT, Types.BLOB, Types.INTEGER});
        }
        token.setId(id);
        return;
    }

    public void removeAccessTokenUsingRefreshToken(OAuth2RefreshToken refreshToken) {
        jdbcTemplate.update(deleteAccessTokenByRefreshTokenSql, refreshToken.getValue());
    }

    private NimbleAccessToken createAccessToken(OAuth2AccessToken base) {
        if(base instanceof NimbleAccessToken) {
            return (NimbleAccessToken)base;
        } else {
            return new NimbleAccessToken(base);
        }
    }

    protected Object[] getAccessTokenStoredValues(NimbleAccessToken token, int authenticationId, OAuth2Authentication authentication) {
        throw new UnsupportedOperationException("Not yet implemented");
        /*return new Object[]{extractTokenKey(token.getValue()),
                new SqlLobValue(serializeAccessToken(token)), authenticationKeyGenerator.extractKey(authentication),
                authentication.isClientOnly() ? null : authentication.getName(),
                authentication.getAuthorizationRequest().getClientId(),
                new SqlLobValue(serializeAuthentication(authentication)), extractTokenKey(refreshToken)};*/
    }

    protected int[] getAccessTokenStoredTypes() {
        return new int[]{
                Types.VARCHAR, Types.BLOB, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BLOB, Types.VARCHAR};
    }


}
