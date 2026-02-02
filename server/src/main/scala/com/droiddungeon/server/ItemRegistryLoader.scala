package com.droiddungeon.server

import com.droiddungeon.items.ItemRegistry
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

object ItemRegistryLoader:
  def load(): ItemRegistry = {
    val itemLines =
      sys.props.get("items.path") match
        case Some(path) => Files.readAllLines(Paths.get(path)).asScala.toList
        case None =>
          val src = scala.io.Source.fromResource("items.txt")
          try src.getLines().toList
          finally src.close()

    ItemRegistry.loadDataOnly(itemLines.asJava)
  }
