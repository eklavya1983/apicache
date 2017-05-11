import app.ApiCache;
import app.ApicacheWebServer;
import app.EmbeddedApiCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

class MockApiCache extends EmbeddedApiCache {
    public MockApiCache() {
        super(1 /* refresh time seconds */, "" /* api token */);
    }
    public void incrCounter() {
        counter++;
    }
    public int getCounter() {
        return counter;
    }
    protected String getFromGit(String endpoint) {
        String retTempl = "{\"endpoint\" : \"%s\", \"counter\" : %d}";
       if (endpoint.equals(ApiCache.ROOT_EP)) {
           return String.format(retTempl, ApiCache.ROOT_EP, counter);
       } else if (endpoint.equals(ApiCache.MEMBERS_EP)) {
           return String.format(retTempl, ApiCache.MEMBERS_EP, counter);
       } else if (endpoint.equals(ApiCache.NETFLIX_EP)) {
           return String.format(retTempl, ApiCache.NETFLIX_EP, counter);
       } else if (endpoint.equals(ApiCache.REPOS_EP)) {
           return String.format(retTempl, ApiCache.REPOS_EP, counter);
       } else {
           return null;
       }
    }

    private int counter = 0;
}
/**
 * Test class for ApicacheWebServer
 */
public class ApicacheWebserverTest {
    private ApicacheWebServer server;
    private MockApiCache cache;
    private String cacheHost;

    @Before
    public void setUp() {
        cache = new MockApiCache();
        server = new ApicacheWebServer(cache);
        cacheHost = "http://localhost:" + server.getConfig().getInt("port");
        server.startServer();
    }

    @After
    public void tearDown() {
        server.stopServer();
    }

    void testEndpoint(String endpoint, int expectedCounter) throws UnirestException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        HttpResponse<String> httpResponse = Unirest.get(cacheHost + endpoint).asString();
        assertEquals(httpResponse.getStatus(), 200);

        ObjectNode bodyNode = mapper.readValue(httpResponse.getBody(), ObjectNode.class);
        assertEquals(bodyNode.get("endpoint").asText(), endpoint);
        assertEquals(bodyNode.get("counter").asInt(), expectedCounter);
    }

    @Test
    public void testReadCacheEndpoints() throws UnirestException, IOException, InterruptedException {

        /* Health check endpoint test */
        HttpResponse<String> healthResponse = Unirest.get(cacheHost + "/healthcheck").asString();
        assertEquals(healthResponse.getStatus(), 200);

        /* Test all cached endpoints */
        testEndpoint(ApiCache.ROOT_EP, 0);
        testEndpoint(ApiCache.MEMBERS_EP, 0);
        testEndpoint(ApiCache.NETFLIX_EP, 0);
        testEndpoint(ApiCache.REPOS_EP, 0);

        cache.incrCounter();

        /* Allow cache refresh to run */
        Thread.sleep(2000);

        /* After refresh counter should be 1 */
        testEndpoint(ApiCache.ROOT_EP, 1);
        testEndpoint(ApiCache.MEMBERS_EP, 1);
        testEndpoint(ApiCache.NETFLIX_EP, 1);
        testEndpoint(ApiCache.REPOS_EP, 1);


    }

}
