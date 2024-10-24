package tukano.impl.storage;

import java.util.List;
import java.util.function.Supplier;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result;

import static tukano.api.Result.ErrorCode;

public class CosmosDBLayer {
	private static final String CONNECTION_URL = "https://por preencher"; // TODO
	private static final String DB_KEY = "por preencher=="; // TODO
	private static final String DB_NAME = "por preencher"; // TODO
	// private static final String CONTAINER = "users";

	private static CosmosDBLayer instance;

	private String containerName;

	public static synchronized CosmosDBLayer getInstance(String containerName) {
		if (instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
				.endpoint(CONNECTION_URL)
				.key(DB_KEY)
				.directMode()
				// .gatewayMode() // replace by .directMode() for better performance
				.consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
				.connectionSharingAcrossClientsEnabled(true)
				.contentResponseOnWriteEnabled(true) // On write, return the object written
				.buildClient();
		instance = new CosmosDBLayer(client, containerName);
		return instance;

	}

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer container;

	public CosmosDBLayer(CosmosClient client, String containerName) {
		this.client = client;
		this.containerName = containerName;
	}

	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		container = db.getContainer(containerName);
	}

	public void close() {
		client.close();
	}

	public <T> Result<T> getOne(String id, Class<T> clazz) {
		return tryCatch(() -> container.readItem(id, new PartitionKey(id), clazz).getItem());
	}

	public <T> Result<?> deleteOne(T obj) {
		return tryCatch(() -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}

	public <T> Result<T> updateOne(T obj) {
		return tryCatch(() -> container.upsertItem(obj).getItem());
	}

	public <T> Result<T> insertOne(T obj) {
		return tryCatch(() -> container.createItem(obj).getItem());
	}

	public <T> Result<?> deleteMany(String queryStr, Class<T> clazz) {
		return tryCatch(() -> container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz)
				.stream()
				.map(item -> container.deleteItem(item, new CosmosItemRequestOptions()).getItem())
				.toList());
	}

	public <T> Result<List<T>> query(String queryStr, Class<T> clazz) {
		return tryCatch(() -> {
			var res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		});
	}

	<T> Result<T> tryCatch(Supplier<T> supplierFunc) {
		try {
			init();
			return Result.ok(supplierFunc.get());
		} catch (CosmosException ce) {
			// ce.printStackTrace();
			return Result.error(errorCodeFromStatus(ce.getStatusCode()));
		} catch (Exception x) {
			x.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	static Result.ErrorCode errorCodeFromStatus(int status) {
		return switch (status) {
			case 200 -> ErrorCode.OK;
			case 404 -> ErrorCode.NOT_FOUND;
			case 409 -> ErrorCode.CONFLICT;
			default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}
