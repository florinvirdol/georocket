package io.georocket.storage.mongodb

import com.mongodb.ConnectionString
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoDatabase
import com.mongodb.reactivestreams.client.gridfs.GridFSBucket
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets
import io.georocket.constants.ConfigConstants.STORAGE_MONGODB_CONNECTION_STRING
import io.georocket.index.mongodb.SharedMongoClient
import io.georocket.storage.ChunkReadStream
import io.georocket.storage.indexed.IndexedStore
import io.georocket.util.PathUtils
import io.georocket.util.UniqueID
import io.georocket.util.io.DelegateChunkReadStream
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import org.bson.BsonDocument
import org.bson.BsonString
import reactor.core.publisher.Mono
import java.nio.ByteBuffer

/**
 * Stores chunks in MongoDB
 * @author Michel Kraemer
 */
class MongoDBStore(vertx: Vertx, connectionString: String? = null) : IndexedStore(vertx) {
  private val client: MongoClient
  private val db: MongoDatabase
  private val gridfs: GridFSBucket

  init {
    val config = vertx.orCreateContext.config()

    val actualConnectionString = connectionString ?: config.getString(
        STORAGE_MONGODB_CONNECTION_STRING) ?: throw IllegalArgumentException(
            """Missing configuration item "$STORAGE_MONGODB_CONNECTION_STRING"""")

    val cs = ConnectionString(actualConnectionString)
    client = SharedMongoClient.create(cs)
    db = client.getDatabase(cs.database)

    gridfs = GridFSBuckets.create(db)
  }

  override suspend fun getOne(path: String): ChunkReadStream {
    val publisher = gridfs.downloadToPublisher(path)
    val bytebuf = publisher.awaitSingle()
    val buf = Buffer.buffer(bytebuf.array())
    return DelegateChunkReadStream(buf)
  }

  override suspend fun doAddChunk(chunk: String, layer: String, correlationId: String): String {
    val path = if (layer.isEmpty()) "/" else layer
    val filename = PathUtils.join(path, correlationId + UniqueID.next())
    gridfs.uploadFromPublisher(filename, Mono.just(ByteBuffer.wrap(chunk.toByteArray()))).awaitSingle()
    return filename
  }

  override suspend fun doDeleteChunks(paths: Iterable<String>) {
    for (filename in paths) {
      gridfs.find(BsonDocument("filename", BsonString(filename))).asFlow().collect { file ->
        gridfs.delete(file.objectId).awaitSingleOrNull()
      }
    }
  }
}
