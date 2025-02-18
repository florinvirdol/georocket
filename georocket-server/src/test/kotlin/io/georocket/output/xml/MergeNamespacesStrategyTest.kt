package io.georocket.output.xml

import io.georocket.assertThatThrownBy
import io.georocket.coVerify
import io.georocket.storage.XMLChunkMeta
import io.georocket.util.XMLStartElement
import io.georocket.util.io.BufferWriteStream
import io.georocket.util.io.DelegateChunkReadStream
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Test [MergeNamespacesStrategy]
 * @author Michel Kraemer
 */
@ExtendWith(VertxExtension::class)
class MergeNamespacesStrategyTest {
  companion object {
    private const val XMLHEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>${"\n"}"""

    private val ROOT1 = XMLStartElement(null, "root", arrayOf("", "ns1", "xsi"),
        arrayOf("uri0", "uri1", "http://www.w3.org/2001/XMLSchema-instance"),
        arrayOf("xsi", "ns1"), arrayOf("schemaLocation", "attr1"),
        arrayOf("uri0 location0 uri1 location1", "value1"))
    private val ROOT2 = XMLStartElement(null, "root", arrayOf("", "ns2", "xsi"),
        arrayOf("uri0", "uri2", "http://www.w3.org/2001/XMLSchema-instance"),
        arrayOf("xsi", "ns2"), arrayOf("schemaLocation", "attr2"),
        arrayOf("uri0 location0 uri2 location2", "value2"))

    private val EXPECTEDROOT = XMLStartElement(null, "root", arrayOf("", "ns1", "xsi", "ns2"),
        arrayOf("uri0", "uri1", "http://www.w3.org/2001/XMLSchema-instance", "uri2"),
        arrayOf("xsi", "ns1", "ns2"), arrayOf("schemaLocation", "attr1", "attr2"),
        arrayOf("uri0 location0 uri1 location1 uri2 location2", "value1", "value2"))

    private const val CONTENTS1 = "<elem><ns1:child1></ns1:child1></elem>"

    private val CHUNK1 = Buffer.buffer("""$XMLHEADER$ROOT1$CONTENTS1</${ROOT1.name}>""")
    private const val CONTENTS2 = "<elem><ns2:child2></ns2:child2></elem>"
    private val CHUNK2 = Buffer.buffer("""$XMLHEADER$ROOT2$CONTENTS2</${ROOT2.name}>""")

    private val META1 = XMLChunkMeta(listOf(ROOT1),
        XMLHEADER.length + ROOT1.toString().length,
        CHUNK1.length() - ROOT1.name.length - 3)
    private val META2 = XMLChunkMeta(listOf(ROOT2),
        XMLHEADER.length + ROOT2.toString().length,
        CHUNK2.length() - ROOT2.name.length - 3)
  }

  /**
   * Test a simple merge
   */
  @Test
  fun simple(vertx: Vertx, ctx: VertxTestContext) {
    val strategy = MergeNamespacesStrategy()
    val bws = BufferWriteStream()

    GlobalScope.launch(vertx.dispatcher()) {
      ctx.coVerify {
        strategy.init(META1)
        strategy.init(META2)
        strategy.merge(DelegateChunkReadStream(CHUNK1), META1, bws)
        strategy.merge(DelegateChunkReadStream(CHUNK2), META2, bws)
        strategy.finish(bws)
        assertThat(bws.buffer.toString("utf-8")).isEqualTo(
            """$XMLHEADER$EXPECTEDROOT$CONTENTS1$CONTENTS2</${EXPECTEDROOT.name}>""")
      }
      ctx.completeNow()
    }
  }

  /**
   * Make sure that chunks that have not been passed to the initalize method cannot be merged
   */
  @Test
  fun mergeUninitialized(vertx: Vertx, ctx: VertxTestContext) {
    val strategy = MergeNamespacesStrategy()
    val bws = BufferWriteStream()

    GlobalScope.launch(vertx.dispatcher()) {
      ctx.coVerify {
        assertThatThrownBy {
          strategy.init(META1) // skip second init
          strategy.merge(DelegateChunkReadStream(CHUNK1), META1, bws)
          strategy.merge(DelegateChunkReadStream(CHUNK2), META2, bws)
        }.isInstanceOf(IllegalStateException::class.java)
      }
      ctx.completeNow()
    }
  }
}
