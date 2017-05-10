import app.ApicacheWebServer;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test class for ApicacheWebServer
 */
public class ApicacheWebserverTest {
    private ApicacheWebServer server;

    @Before
    public void setUp() {
        server = new ApicacheWebServer();
        server.startServer();
    }

    @After
    public void tearDown() {
        server.stopServer();
    }

    @Test
    public void testHealthcheck() throws UnirestException {
        HttpResponse<String> httpResponse = Unirest.get("http://localhost:8000/healthcheck").asString();
        assertEquals(httpResponse.getStatus(), 200);
    }
}
