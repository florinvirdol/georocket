package io.georocket.http

import io.georocket.coVerify
import io.georocket.constants.ConfigConstants
import io.georocket.storage.GeoJsonChunkMeta
import io.georocket.storage.Store
import io.georocket.storage.StoreCursor
import io.georocket.storage.StoreFactory
import io.georocket.util.io.DelegateChunkReadStream
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.web.client.sendAwait
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Path

/**
 * Test [StoreEndpoint]
 * @author Michel Kraemer
 */
@ExtendWith(VertxExtension::class)
class StoreEndpointTest {
  private var port: Int = 0
  private lateinit var store: Store

  private class EndpointVerticle(private val port: Int) : CoroutineVerticle() {
    override suspend fun start() {
      val router = Router.router(vertx)
      val se = StoreEndpoint(coroutineContext, vertx)
      router.mountSubRouter("/store", se.createRouter())
      val server = vertx.createHttpServer(httpServerOptionsOf())
      server.requestHandler(router).listenAwait(port, "localhost")
    }
  }

  @BeforeEach
  fun setUp(vertx: Vertx, ctx: VertxTestContext, @TempDir tempDir: Path) {
    port = ServerSocket(0).use { it.localPort }

    store = mockk()
    mockkObject(StoreFactory)
    every { StoreFactory.createStore(any()) } returns store

    val config = json {
      obj(
          ConfigConstants.STORAGE_FILE_PATH to tempDir.toString()
      )
    }
    val options = deploymentOptionsOf(config)
    vertx.deployVerticle(EndpointVerticle(port), options, ctx.completing())
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  /**
   * Get a single chunk
   */
  @Test
  fun getSingleChunk(vertx: Vertx, ctx: VertxTestContext) {
    val strChunk1 = """{"type":"Polygon"}"""
    val chunk1 = Buffer.buffer(strChunk1)
    val chunk1Path = "/foobar"

    val cm = GeoJsonChunkMeta("Polygon", "geometries", 0, chunk1.length())

    coEvery { store.get(null, "/") } answers {
      val cursor = mockk<StoreCursor>()

      coEvery { cursor.hasNext() } returns true andThen false
      coEvery { cursor.next() } returns cm

      every { cursor.iterator() } returns cursor
      every { cursor.chunkPath } returns chunk1Path

      cursor
    }

    coEvery { store.getOne(chunk1Path) } returns DelegateChunkReadStream(chunk1)

    val client = WebClient.create(vertx)
    GlobalScope.launch(vertx.dispatcher()) {
      ctx.coVerify {
        val response = client.get(port, "localhost", "/store")
            .`as`(BodyCodec.string())
            .expect(ResponsePredicate.SC_OK)
            .sendAwait()
        assertThat(response.body()).isEqualTo(strChunk1)
      }
      ctx.completeNow()
    }
  }
}
