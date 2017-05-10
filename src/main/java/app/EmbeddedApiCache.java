package app;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.utils.SyncIdleConnectionMonitorThread;
import com.mashape.unirest.request.GetRequest;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.SysexMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nbayyana on 5/6/17.
 */

public class EmbeddedApiCache implements ApiCache {
    public EmbeddedApiCache(int refreshSec, String apiToken)
    {
        this.refreshSec = refreshSec;
        this.apiToken = apiToken;

        logger.info("refreshSec: " + refreshSec + " apiToken: " + apiToken);
    }

    @Override
    public void init()
    {
        /* Baseline refresh of the cache */
        refreshCache();

        /* Start background thread to refresh cache periodically */
        Runnable task = () -> {
            logger.info("Started cache refresh thread");
            try {
                while (true) {
                    Thread.sleep(refreshSec * 1000);
                    refreshCache();
                }
            } catch (InterruptedException e) {
            }
            logger.info("Exiting cache refresh thread");
        };

        new Thread(task).start();
    }

    @Override
    public HttpResponse<String> get(String endpoint) {
        Map<String, HttpResponse<String>> c = cache.get();
        HttpResponse<String> ret = c.get(endpoint);
        return ret;
    }

    private void refreshCache() {
        final String ENDPOINTS[] = {
            ROOT_EP,
            NETFLIX_EP,
            MEMBERS_EP,
            REPOS_EP
        };

        Map<String, HttpResponse<String>> tempCache = new Hashtable<>();
        for (int i = 0; i < ENDPOINTS.length; i++) {
            try {
                HttpResponse<String> fromGit = getFromGit(ENDPOINTS[i]);
                // TODO(Rao): How to handle non 200 error codes
                tempCache.put(ENDPOINTS[i], fromGit);
            } catch (Exception e) {
                logger.warn("Refresh for endpoint:"
                        + ENDPOINTS[i]
                        + " encountered exception."
                        + "  Will keep previous cache entry",
                        e);
                tempCache.put(ENDPOINTS[i], get(ENDPOINTS[i]));
            }
        }

        /* Atomically switch to new version of the cache */
        cache.set(tempCache);
    }

    private HttpResponse<String> getFromGit(String endpoint) throws UnirestException, IOException {
        String gitUri = "https://api.github.com";
        Supplier<GetRequest> getSupplier = ()-> {
            GetRequest getRequest;
            if (apiToken.isEmpty()) {
                getRequest = Unirest.get(gitUri + endpoint);
            } else {
                getRequest = Unirest.get(gitUri + endpoint)
                        .header("Authorization", "token " + apiToken);
            }
            return getRequest;
        };
        HttpResponse<String> httpResponse = getSupplier.get().asString();

        /* Do pagination if necessary */
        if (httpResponse.getHeaders().containsKey("link")) {
            ArrayNode bodyNode = mapper.readValue(httpResponse.getBody(), ArrayNode.class);

            String link = httpResponse.getHeaders().getFirst("link");
            String patternStr = "page=(.+)>; rel=\"last\"";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(link);
            if (matcher.find()) {
                int numPages = Integer.parseInt(matcher.group(1));
                for (int page = 2; page <= numPages; page++) {
                    httpResponse = getSupplier
                            .get()
                            .queryString("page", page)
                            .asString();
                    if (httpResponse.getStatus() == 200) {
                        ArrayNode tempNode = mapper.readValue(httpResponse.getBody(), ArrayNode.class);
                        bodyNode.addAll(tempNode);
                    } else {
                        /* Return http response with the error code */
                        logger.warn("Received status: " + httpResponse.getStatus() + " for pagination request.");
                        return httpResponse;
                    }
                }
                /*
                org.apache.http.HttpResponse resp = new DefaultHttpResponseFactory().newHttpResponse();
                resp.setHeader(httpResponse.getHeaders());
                httpResponse.get
                */
            }
        } else {
            return httpResponse;
        }

        return httpResponse;
    }

    private int refreshSec;
    private String apiToken;

    private Logger logger = LoggerFactory.getLogger(EmbeddedApiCache.class);
    private ObjectMapper mapper = new ObjectMapper();
    private AtomicReference<Map<String, HttpResponse<String>>> cache = new AtomicReference<>(new Hashtable<String, HttpResponse<String>>());
}
