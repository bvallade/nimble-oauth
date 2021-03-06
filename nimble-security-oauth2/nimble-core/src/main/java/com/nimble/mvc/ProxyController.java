package com.nimble.mvc;

import com.nimble.content.ContentModifier;
import com.nimble.http.BasicHeadersExtractor;
import com.nimble.http.HeadersExtractor;
import com.nimble.http.RequestBodyExtractor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Controller
public class ProxyController {
    private static int MAX_RETRIES = 3;
    protected Log log = LogFactory.getLog(getClass());
    //private Set<String> headersToStrip;
    private String targetDomain;
    private RestOperations restOperations;
    private RequestBodyExtractor requestBodyExtractor;
    private List<ContentModifier> inboundBodyModifiers = new ArrayList<ContentModifier>();
    private List<ContentModifier> outboundBodyModifiers = new ArrayList<ContentModifier>();
    private List<ContentModifier> urlModifiers = new ArrayList<ContentModifier>();
    private List<ContentModifier> inboundHeadersMods = new ArrayList<ContentModifier>();
    private List<ContentModifier> outboundHeadersMods = new ArrayList<ContentModifier>();
    private HeadersExtractor headersExtractor = new BasicHeadersExtractor();
    private boolean throwExceptionNon200Reponse = false;    //default false - complete pass through

    public ProxyController() {
        /*headersToStrip = new HashSet<String>();
        headersToStrip.add("Content-Encoding");
        headersToStrip.add("Transfer-Encoding");*/
        //want to strip authorization since changing the method
        //setHeadersToStrip(headersToStrip);
    }

    /*public void setHeadersToStrip(Set<String> headersToStrip) {
        this.headersToStrip = headersToStrip;
    }*/

    public void setThrowExceptionNon200Reponse(boolean throwExceptionNon200Reponse) {
        this.throwExceptionNon200Reponse = throwExceptionNon200Reponse;
    }

    public void setRequestBodyExtractor(RequestBodyExtractor requestBodyExtractor) {
        this.requestBodyExtractor = requestBodyExtractor;
    }

    public void setInboundBodyModifiers(List<ContentModifier> inboundBodyModifiers) {
        this.inboundBodyModifiers = inboundBodyModifiers;
    }

    public void setUrlModifiers(List<ContentModifier> urlModifiers) {
        this.urlModifiers = urlModifiers;
    }

    public void setInboundHeadersMods(List<ContentModifier> inboundHeadersMods) {
        this.inboundHeadersMods = inboundHeadersMods;
    }

    public void setOutboundHeadersMods(List<ContentModifier> outboundHeadersMods) {
        this.outboundHeadersMods = outboundHeadersMods;
    }

    public void setHeadersExtractor(HeadersExtractor headersExtractor) {
        this.headersExtractor = headersExtractor;
    }

    public void setRestOperations(RestTemplate restOperations) {
        this.restOperations = restOperations;

    }

    public void setTargetDomain(String targetDomain) {
        this.targetDomain = targetDomain;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<String> proxy(HttpServletRequest request, Model model) throws Exception {
        String url = getUrl(request);

        //extract the request body - assuming only string data being to be supported.
        String body = getBody(request);

        //create the entity that will be sent
        HttpEntity<String> reqEntity = new HttpEntity<String>(body, extractHeaders(request));

        URI uri = new URI(url);
        if (log.isDebugEnabled()) {
            log.debug("proxy URL: " + url);
            log.debug("request: " + reqEntity.toString());
        }

        //get the response from the request.  For now assume that the response will always be a string since this is just
        //a dumb proxy.
        ResponseEntity<String> responseEntity = getStringResponseEntity(request, reqEntity, uri);
        if (throwExceptionNon200Reponse && responseEntity.getStatusCode().series().value() != HttpStatus.Series.SUCCESSFUL.value()) {
            //TODO: determine the correct exception to throw.  This could also be done in spring via configuration of the resttemplate
        }


        //apply any registered post processors to the response body
        body = applyStringMods(responseEntity.getBody(), outboundBodyModifiers);

        MultiValueMap<String, String> newHeaders = new LinkedMultiValueMap<String, String>();
        for (Map.Entry<String, List<String>> entry : responseEntity.getHeaders().entrySet()) {
            newHeaders.put(entry.getKey(), entry.getValue());
        }

        //apply and registered header post processors
        if (outboundHeadersMods != null) {
            Map<String, List<String>> newMap = newHeaders;
            for (ContentModifier modifier : outboundHeadersMods) {
                newMap = modifier.modify(newMap);
            }
            if (MultiValueMap.class.isInstance(newMap)) {
                newHeaders = (MultiValueMap<String, String>) newMap;
            } else {
                newHeaders = new LinkedMultiValueMap<String, String>(newMap);
            }
        }

        newHeaders.put("Content-Length", Arrays.asList("" + (body != null ? body.getBytes().length : 0)));

        ResponseEntity<String> proxyResp = new ResponseEntity<String>(body, newHeaders, responseEntity.getStatusCode());

        return proxyResp;
    }

    protected ResponseEntity<String> getStringResponseEntity(HttpServletRequest request, HttpEntity<String> reqEntity, URI uri) {
        ResponseEntity<String> entity;
        int tries = 1;
        do {
            entity = restOperations.exchange(uri, HttpMethod.valueOf(request.getMethod()), reqEntity, String.class);
        }
        while (entity.getStatusCode().series().value() == HttpStatus.Series.SERVER_ERROR.value() && tries++ < MAX_RETRIES);

        return entity;
    }

    protected HttpHeaders extractHeaders(HttpServletRequest request) {
        Assert.notNull(headersExtractor, "headersExtractor cannot be null");
        HttpHeaders headers = new HttpHeaders();

        Map<String, List<String>> headersMap = headersExtractor.extractHeaders(request);

        for (ContentModifier modifier : inboundHeadersMods) {
            headersMap = modifier.modify(headersMap);
        }
        headers.putAll(headersMap);
        return headers;
    }

    protected String getUrl(HttpServletRequest request) throws IOException {
        String url = targetDomain + request.getServletPath() + "?" + request.getQueryString();
        //do any post processing
        return applyStringMods(url, urlModifiers);
    }

    protected String getBody(HttpServletRequest request) throws IOException {
        Assert.notNull(requestBodyExtractor, "requestBodyExtractor cannot be null");
        String body = requestBodyExtractor.extractBody(request);
        //do any post processing
        body = applyStringMods(body, inboundBodyModifiers);
        return body;
    }

    private String applyStringMods(String input, List<ContentModifier> mods) {
        if (mods != null) {
            for (ContentModifier modifier : mods) {
                input = modifier.modify(input);
            }
        }
        return input;
    }

}
