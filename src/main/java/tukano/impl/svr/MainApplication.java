package tukano.impl.svr;

import jakarta.ws.rs.core.Application;
import tukano.impl.rest.RestBlobsResource;
import tukano.impl.rest.RestShortsResource;
import tukano.impl.rest.RestUsersResource;

import java.util.HashSet;
import java.util.Set;

public class MainApplication extends Application {
    
    private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

    MainApplication() {
        resources.add(ControlResource.class);
        resources.add(RestBlobsResource.class);
        resources.add(RestUsersResource.class);
        resources.add(RestShortsResource.class);
    }

    @Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}
