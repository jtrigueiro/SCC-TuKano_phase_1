package tukano.impl.svr;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.rest.RestBlobsResource;
import tukano.impl.rest.RestShortsResource;
import tukano.impl.rest.RestUsersResource;

public class MainApplication extends Application
{
	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public MainApplication() {
		resources.add(ControlResource.class);
		singletons.add(new RestUsersResource());
		singletons.add(new RestShortsResource());
		singletons.add(new RestBlobsResource());
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
