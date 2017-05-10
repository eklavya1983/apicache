package app;

import com.mashape.unirest.http.HttpResponse;

public interface ApiCache {
    /**
     * Initialization function
     */
    void init();

    /**
     * Return cached value for provided endpoint.  If endpoint isn't in the cache
     * null is returned
     * @param endpoint
     * @return Cached value if one exists otherwise null
     */
    String get(String endpoint);

    static final String ROOT_EP = "/";
    static final String NETFLIX_EP = "/orgs/Netflix";
    static final String MEMBERS_EP = "/orgs/Netflix/members";
    static final String REPOS_EP = "/orgs/Netflix/repos";


}

