package io.georocket.storage.h2

import org.h2.mvstore.MVStore
import java.io.File

class SharedMVMap(private val key: String, private val store: MVStore,
    private val map: MutableMap<String, String>) : MutableMap<String, String> by map {
  private var instanceCount = 0

  companion object {
    private val sharedMaps = mutableMapOf<String, SharedMVMap>()

    fun create(path: String, mapName: String, compress: Boolean): SharedMVMap {
      return synchronized(this) {
        val key = "$path##$mapName##$compress"
        val result = sharedMaps.computeIfAbsent(key) {
          val dir = File(path).parentFile
          if (!dir.exists()) {
            dir.mkdirs()
          }

          var builder = MVStore.Builder().fileName(path)
          if (compress) {
            builder = builder.compress()
          }
          val store = builder.open()
          val map = store.openMap<String, String>(mapName)

          SharedMVMap(key, store, map)
        }

        result.instanceCount++
        result
      }
    }
  }

  fun close() {
    synchronized(SharedMVMap) {
      instanceCount--
      if (instanceCount == 0) {
        store.close()
        sharedMaps.remove(key)
      }
    }
  }
}
