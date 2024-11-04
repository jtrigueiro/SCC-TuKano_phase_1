package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.checkerframework.checker.units.qual.C;

import redis.clients.jedis.Jedis;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.cache.RedisCache;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.CosmosDBLayer;
import utils.JSON;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID()); // TODO: change "+" to "_" to work deployed on
																		// Azure
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(CosmosDBLayer.getInstance(Shorts.NAME).insertOne(shrt),
					s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			var cached = jedis.get(Shorts.NAME + ':' + shortId);

			if (cached != null)
				return Result.ok(JSON.decode(cached, Short.class));

			CosmosDBLayer dblikes = CosmosDBLayer.getInstance(Shorts.LIKES);

			var query = format("SELECT count(1) AS count FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likes = dblikes.query(query, Long.class).value();

			CosmosDBLayer dbshorts = CosmosDBLayer.getInstance(Shorts.NAME);
			var result = errorOrValue(dbshorts.getOne(shortId, Short.class),
					shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));

			if (result.isOK())
				jedis.set(Shorts.NAME + ':' + shortId, JSON.encode(result.value()));

			return result;
		}
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

				CosmosDBLayer dbshorts = CosmosDBLayer.getInstance(Shorts.NAME);

				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del(Shorts.NAME + ':' + shortId);
				}

				dbshorts.deleteOne(shrt);

				CosmosDBLayer dblikes = CosmosDBLayer.getInstance(Shorts.LIKES);

				var query = format("SELECT Likes l WHERE l.shortId = '%s'", shortId);
				dblikes.deleteMany(query, Likes.class);

				JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
				return Result.ok();
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) { // TODO: fix 500 error
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue(okUser(userId), CosmosDBLayer.getInstance(Shorts.NAME).query(query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			CosmosDBLayer db = CosmosDBLayer.getInstance(Shorts.FOLLOWING);

			return errorOrVoid(okUser(userId2), isFollowing ? db.insertOne(f) : db.deleteOne(f));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue(okUser(userId, password),
				CosmosDBLayer.getInstance(Shorts.FOLLOWING).query(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			CosmosDBLayer db = CosmosDBLayer.getInstance(Shorts.LIKES);
			return errorOrVoid(okUser(userId, password), isLiked ? db.insertOne(l) : db.deleteOne(l));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password),
					CosmosDBLayer.getInstance(Shorts.LIKES).query(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.id, s.timestamp FROM Short s WHERE	s.ownerId = '%s'
				UNION
				SELECT s.id, s.timestamp FROM Short s, Following f
					WHERE
						f.followee = s.ownerId AND f.follower = '%s'
				ORDER BY s.timestamp DESC""";

		return errorOrValue(okUser(userId, password),
				CosmosDBLayer.getInstance(Shorts.NAME).query(format(QUERY_FMT, userId, userId), String.class));
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else
			return error(res.error());
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if (!Token.isValid(token, userId))
			return error(FORBIDDEN);

		// delete shorts
		CosmosDBLayer dbshorts = CosmosDBLayer.getInstance(Shorts.NAME);
		var query1 = format("SELECT Short s WHERE s.ownerId = '%s'", userId);
		var shorts = dbshorts.query(query1, Short.class).value();
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			shorts.forEach(shrt -> jedis.del(Shorts.NAME + ':' + shrt.getId()));
		}
		dbshorts.deleteMany(query1, Short.class);

		// delete follows
		CosmosDBLayer dbfollowing = CosmosDBLayer.getInstance(Shorts.FOLLOWING);
		var query2 = format("SELECT Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
		dbfollowing.deleteMany(query2, Following.class);

		// delete likes
		CosmosDBLayer dblikes = CosmosDBLayer.getInstance(Shorts.LIKES);
		var query3 = format("SELECT Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
		dblikes.deleteMany(query3, Likes.class);

		return ok();
	}

}