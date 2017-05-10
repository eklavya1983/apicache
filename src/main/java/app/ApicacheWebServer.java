package app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.GetRequest;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static spark.Spark.*;


public class ApicacheWebServer {
    public ApicacheWebServer()
    {
        cache = new EmbeddedApiCache(config.getInt("cache_refresh_interval_sec"),
                config.getString("api_token"));
    }

    public void startServer() {

        cache.init();

        port(config.getInt("port"));

        get("/healthcheck", (req, res) -> {
            res.status(200);
            return "";
        });
        get(ApiCache.ROOT_EP, (req, res) -> {
            transformToSparkResponse(cache.get(ApiCache.ROOT_EP), res);
            return res.body();
        });
        get(ApiCache.NETFLIX_EP, (req, res) -> {
            transformToSparkResponse(cache.get(ApiCache.NETFLIX_EP), res);
            return res.body();
        });
        get(ApiCache.MEMBERS_EP, (req, res) -> {
            transformToSparkResponse(cache.get(ApiCache.MEMBERS_EP), res);
            return res.body();
        });
        get(ApiCache.REPOS_EP, (req, res) -> {
            transformToSparkResponse(cache.get(ApiCache.REPOS_EP), res);
            return res.body();
        });
        get("/view/top/:count/forks", (req, res) -> {
            int count = Integer.parseInt(req.params(":count"));
            ArrayNode forks = viewTopReposBy(count,
                    (o1, o2) -> new Integer(o2.get("forks").asInt()).compareTo(o1.get("forks").asInt()));
            return forks.toString();
        });
        get("/view/top/:count/last_updated", (req, res) -> {
            int count = Integer.parseInt(req.params(":count"));
            ArrayNode lastUpdated= viewTopReposBy(count,
                    (o1, o2) -> Instant.parse(o2.get("updated_at").asText()).compareTo(Instant.parse(o1.get("updated_at").asText())));
            return lastUpdated.toString();
        });
        get("/view/top/:count/open_issues", (req, res) -> {
            int count = Integer.parseInt(req.params(":count"));
            ArrayNode openIssues = viewTopReposBy(count,
                    (o1, o2) -> new Integer(o2.get("open_issues").asInt()).compareTo(o1.get("open_issues").asInt()));
            return openIssues.toString();
        });
        get("/view/top/:count/stars", (req, res) -> {
            int count = Integer.parseInt(req.params(":count"));
            ArrayNode stargazersCount = viewTopReposBy(count,
                    (o1, o2) -> new Integer(o2.get("stargazers_count").asInt()).compareTo(o1.get("stargazers_count").asInt()));
            return stargazersCount.toString();
        });

        /* NOTE: Only any other gets are handled.  We are not handling POST, PUT, DELETE, etc */
        get("/*", (req, res) -> {
            String uri = req.uri();
            Map<String, String[]> queryMap = req.queryMap().toMap();
            Set<String> headers = req.headers();
            String body = req.body();

            /* Convert to unirest request */
            GetRequest getRequest = Unirest.get("https://api.github.com" + uri);
            for (String h : headers) {
                if (h == "Host") {
                    continue;
                }
                getRequest.header(h, req.headers(h));
            }
            for (Map.Entry<String, String[]> item : queryMap.entrySet()) {
                getRequest.queryString(item.getKey(), Arrays.asList(item.getValue()));
            }
            HttpResponse<String> httpResponse = getRequest.asString();
            transformToSparkResponse(httpResponse, res);
            return res.body();
        });
    }

    public void stopServer() {
        stop();
    }

    private void transformToSparkResponse(HttpResponse<String> in, Response out) {
        Headers headers = in.getHeaders();
        out.type(headers.getFirst("content-type"));
        out.status(in.getStatus());
        out.body(in.getBody());
        String body1 = out.body();
        String body2 = in.getBody();
    }

    private ArrayNode viewTopReposBy(int count,
                                     Comparator<ObjectNode> comparator) throws IOException {
        HttpResponse<String> httpResponse = cache.get(ApiCache.REPOS_EP);
        ArrayNode topRepos = mapper.createArrayNode();
        if (httpResponse.getStatus() == 200) {
            ArrayNode bodyNode = mapper.readValue(httpResponse.getBody(), ArrayNode.class);
            Collection<ObjectNode> nodes = new ArrayList<>();
            for (int i = 0; i < bodyNode.size(); i++) {
                nodes.add((ObjectNode) bodyNode.get(i));
            }
            nodes
                    .stream()
                    .sorted(comparator)
                    .limit(count)
                    .forEach((item) -> topRepos.add(item));

        }
        return topRepos;
    }

    private Logger logger = LoggerFactory.getLogger(ApicacheWebServer.class);
    private Config config = ConfigFactory.load();
    private ObjectMapper mapper = new ObjectMapper();
    private ApiCache cache;


    public static void main(String[] args) {
    	ApicacheWebServer app = new ApicacheWebServer();
        app.startServer();
    }
}