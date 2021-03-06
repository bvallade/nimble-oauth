package com.nimble.security.authentication.rest;


import com.nimble.security.core.userdetails.NimbleUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Will delegate the authentication call to a remote, RESTFul call.
 * Date: 4/10/13
 * Time: 1:56 PM
 */
public class RestDelegatingAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider implements AuthenticationProvider, UserDetailsService {
    private RestTemplate restOperations;
    private String createSessionEndpointUrl;
    //this will be to load users from within the security domain
    private UserDetailsManager userDetailsManager;
    private String defaultUserName = "default_user";

    public void setCreateSessionEndpointUrl(String createSessionEndpointUrl) {
        this.createSessionEndpointUrl = createSessionEndpointUrl;
    }

    public void setUserDetailsManager(UserDetailsManager userDetailsManager) {
        this.userDetailsManager = userDetailsManager;
    }

    public void setDefaultUserName(String defaultUserName) {
        this.defaultUserName = defaultUserName;
    }

    protected RestOperations getRestOperations() {
        if (restOperations == null) {
            setRestOperations(new RestTemplate());
        }
        return restOperations;
    }

    public void setRestOperations(RestTemplate restOperations) {
        this.restOperations = restOperations;
        //want to use the default message converters and add the form one since it is essentially a form submission that's received by the session api.
        List<HttpMessageConverter<?>> converters = restOperations.getMessageConverters();
        converters.add(0, new org.springframework.http.converter.FormHttpMessageConverter());
        restOperations.setMessageConverters(converters);
        //set up the request factory as well
        ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        restOperations.setRequestFactory(requestFactory);

    }

    @Override
    protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        //prepare the request body for execution.
        MultiValueMap<String, Object> params = new LinkedMultiValueMap<String, Object>();
        params.add("email", authentication.getName());
        params.add("password", authentication.getCredentials());
        params.add("is_persistent", "1");
        UserDetails userDetails = null;
        if (logger.isDebugEnabled()) {
            logger.debug("retrieveUser: start: username=" + username);
        }
        //ok, let's see if the user authenticates with the application
        try {
            ResponseEntity<Map> response = getRestOperations().postForEntity(createSessionEndpointUrl, params, Map.class, params);
            //this check is unnecessary as an exception will be thrown if we get a 4xx, 5xx etc
            if (response.getStatusCode() == HttpStatus.CREATED) {
                //have a user.  Now need to look up their authorities
                Map<String, Object> data = response.getBody();
                Map<String, Object> user = (Map<String, Object>) data.get("user");
                String uname = (String) user.get("email");
                String token = (String) data.get("token");
                if (token == null) {
                    logger.error("retrieveUser: No token received from successful session API call.  username=" + username);
                }
                UserDetails defaultDetais = userDetailsManager.loadUserByUsername(defaultUserName);
                Collection<? extends GrantedAuthority> authorities = null;
                if (defaultDetais != null && defaultDetais.getAuthorities() != null) {
                    authorities = defaultDetais.getAuthorities();
                } else {
                    logger.warn("retrieveUser: No default user found.  This is to be used for setting authorities");
                    //authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
                }
                userDetails = new NimbleUser(uname, (String) authentication.getCredentials(), authorities, token);
            } else {
                logger.warn("retrieveUser: username=" + username + "unexpected http response: " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException e) {
            //todo: decipher the cause of the excetion and translate into the correct AuthenticationException
            throw new BadCredentialsException(e.getResponseBodyAsString(), e);
        }

        logger.info("retrieveUser: end: username=" + username + ", found=" + (userDetails != null ? "true" : "false"));
        return userDetails;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean supports(Class<?> aClass) {
        return Authentication.class.isAssignableFrom(aClass);
    }

    /**
     * This implementation will only be able to return UserDetails for users who have already been authenticated.
     * Will make a call to the api or load from local storage (cache etc)
     *
     * @param username
     * @return
     * @throws UsernameNotFoundException
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
