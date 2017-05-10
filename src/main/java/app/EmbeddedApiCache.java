package app;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.GetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In memory implemenation of ApiCache interface
 */
public class EmbeddedApiCache implements ApiCache {
    /**
     * Constructor
     * @param refreshSec
     * @param apiToken
     */
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
    public String get(String endpoint) {
        Map<String, String> c = cache.get();
        String ret = c.get(endpoint);
        return ret;
    }

    /**
     * Refreshes the cache atomically
     */
    private void refreshCache() {
        final String ENDPOINTS[] = {
            ROOT_EP,
            NETFLIX_EP,
            MEMBERS_EP,
            REPOS_EP
        };

        Map<String, String> tempCache = new Hashtable<>();
        for (int i = 0; i < ENDPOINTS.length; i++) {
            String fromGit = getFromGit(ENDPOINTS[i]);
            if (fromGit == null) {
                    /* Use previous cached value in case we fail to fetch from github */
                fromGit = tempCache.get(ENDPOINTS[i]);
            }
            tempCache.put(ENDPOINTS[i], fromGit);
        }

        /* Atomically switch to new version of the cache */
        cache.set(tempCache);
    }

    /**
     * Performs HTTP GET on endpoint URI against github.  Body of the GET request
     * is returned as string.
     * On any exception or non 200 status null is returned.
     * @param endpoint
     * @return body of GET request as string or null on any exception or non-200 status code
     */
    private String getFromGit(String endpoint) {
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

        try {
            HttpResponse<String> httpResponse = getSupplier.get().asString();
            if (httpResponse.getStatus() != 200) {
                logger.warn("request for: " + endpoint + " resulted in status: " + httpResponse.getStatus()
                        + " with body: " + httpResponse.getBody());
                return null;
            }

            /* Do pagination if necessary */
            if (httpResponse.getHeaders().containsKey("link")) {
                ArrayNode bodyNode = mapper.readValue(httpResponse.getBody(), ArrayNode.class);

                String link = httpResponse.getHeaders().getFirst("link");
                String patternStr = "page=(\\d+)>; rel=\"last\"";
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(link);
                if (matcher.find()) {
                    String pageStr = matcher.group(1);
                    int numPages = Integer.parseInt(pageStr);
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
                            logger.warn("request for: " + endpoint + " at page: " + page
                                    + " resulted in status: " + httpResponse.getStatus()
                                    + " with body: " + httpResponse.getBody());
                            return null;
                        }
                    }
                    return bodyNode.toString();
                } else {
                    logger.warn("request for: " + endpoint
                            + " with pagination header didn't find last page info");
                    return null;
                }
            } else {
                return httpResponse.getBody();
            }
        } catch (Exception e) {
            logger.warn("Refresh for endpoint:"
                            + endpoint
                            + " encountered exception."
                            + "  Will keep previous cache entry",
                    e);
        }
        return null;
    }

    /* Cache refresh time in seconds */
    private int refreshSec;
    /* API token for github */
    private String apiToken;

    private Logger logger = LoggerFactory.getLogger(EmbeddedApiCache.class);
    private ObjectMapper mapper = new ObjectMapper();
    private AtomicReference<Map<String, String>> cache = new AtomicReference<>(new Hashtable<String, String>());
}
