package tf.bug.p8hijack

import cats.syntax.all._
import com.monovore.decline.{CommandApp, Opts}
import java.net.URI
import java.nio.file.Path
import scribe.{Level, Logger}

object Main extends CommandApp(
  name = "p8hijack",
  header = "PICO-8 Web Hijacker",
  main = {
    val urlArg =
      Opts.argument[URI]("https://input.url")

    val imageOption =
      Opts.option[Path]("image", "Set the cover image to be used for steganography.", "i", "input.png")
        .orNone

    val outputP8Png =
      Opts.argument[Path]("output.p8.png")

    (urlArg, imageOption, outputP8Png).mapN { (u, i, o) =>
      Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(minimumLevel = Some(Level.Info))
        .replace()
      Hijack.run(u, i, o)
    }
  }
)
