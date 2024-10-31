package tukano.impl.rest;

//import java.net.URI;
//import java.util.logging.Logger;

import java.util.HashSet;
import java.util.Set;

//import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
//import org.glassfish.jersey.server.ResourceConfig;

import jakarta.ws.rs.core.Application;
//import tukano.impl.Token;
import tukano.impl.svr.ControlResource;
//import utils.Args;
//import utils.IP;


public class TukanoRestServer extends Application{
	//final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/rest";

	public static final int PORT = 8080;

	public static String serverURI;
	
	/*
	protected TukanoRestServer() {
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
	}*/

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	public TukanoRestServer() {
		resources.add(ControlResource.class);
		resources.add(new RestUsersResource().getClass());
		resources.add(new RestBlobsResource().getClass());
		resources.add(new RestBlobsResource().getClass());
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}


	/*
	protected void start() throws Exception {
	
		ResourceConfig config = new ResourceConfig();
		
		config.register(RestBlobsResource.class);
		config.register(RestUsersResource.class); 
		config.register(RestShortsResource.class);
		
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);
		
		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}
	
	public static void main(String[] args) throws Exception {
		Args.use(args);
		
		Token.setSecret( Args.valueOf("-secret", ""));
//		Props.load( Args.valueOf("-props", "").split(","));
		
		new TukanoRestServer().start();
	}
	*/
}
