package utils;

import com.azure.cosmos.implementation.Constants.Properties;

public class AzureKeys {
    public static final String BLOB_KEY = "BlobStoreConnection";
    public static final String COSMOSDB_KEY = "COSMOSDB_KEY";
    public static final String COSMOSDB_URL = "COSMOSDB_URL";
    public static final String COSMOSDB_DATABASE = "COSMOSDB_DATABASE";

    private static Properties props;

    public static synchronized Properties getProperties() {

    }

}
