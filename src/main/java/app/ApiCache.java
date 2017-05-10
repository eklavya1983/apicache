package app;

import com.mashape.unirest.http.HttpResponse;

/**
 * Created by nbayyana on 5/6/17.
 */
public interface ApiCache {
    public void init();
    public HttpResponse<String> get(String endpoint);

    public static final String ROOT_EP = "/";
    public static final String NETFLIX_EP = "/orgs/Netflix";
    public static final String MEMBERS_EP = "/orgs/Netflix/members";
    public static final String REPOS_EP = "/orgs/Netflix/repos";


}

