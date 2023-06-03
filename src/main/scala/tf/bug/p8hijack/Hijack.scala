package tf.bug.p8hijack

import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import org.openqa.selenium.{By, JavascriptExecutor, WebDriver, WebElement}
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.WebDriverWait
import scala.util.{Try, Using}

object Hijack {

  val footer: Vector[Byte] = Vector[Byte](
    0x00, // internal PICO-8 version
    0x00, 0x00, 0x00, // external PICO-8 version
    0x00, // PICO-8 edition
    0x00, // version suffix
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // SHA hash of cartridge content
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // padding
  )

  def run(url: URI, coverImagePath: Option[Path], outputPath: Path): Unit = {
    val cartData: Vector[Byte] = Using(new ChromeDriver()) { driver =>
      driver.get(url.toString)

      val p8Containers =
        new WebDriverWait(driver, Duration.ofSeconds(10L))
          .until(findP8Container)

      p8Containers match {
        case Nil => throw new IllegalArgumentException("Found no frames containing #p8_container") // TODO logger
        case List((trace, container)) =>
          trace.foreach(driver.switchTo().frame)
          val start = driver.findElement(By.id("p8_start_button"))
          start.click()

          val cartData: Vector[Byte] =
            new WebDriverWait(driver, Duration.ofSeconds(10L))
              .until(driver => getCartData(driver.asInstanceOf[ChromeDriver]))

          if(cartData.size != 0x8000) {
            throw new IllegalArgumentException(s"Cart data not 0x8000 bytes: got 0x${cartData.size.toHexString}") // TODO logger
          }

          cartData
        case _ => throw new IllegalArgumentException("Found multiple frames containing #p8_container") // TODO logger
      }
    }(_.close()).get

    val readCoverImage: BufferedImage = coverImagePath.fold {
      ImageIO.read(this.getClass.getResource("default-cover.png"))
    } { p =>
      ImageIO.read(p.toFile)
    }
    if(readCoverImage.getWidth != 160 || readCoverImage.getHeight != 205) {
      throw new IllegalArgumentException(s"Cover image not 160x205: got ${readCoverImage.getWidth}x${readCoverImage.getHeight}")
    }

    val outputRaster = new BufferedImage(160, 205, BufferedImage.TYPE_INT_ARGB)
    outputRaster.getGraphics.drawImage(readCoverImage, 0, 0, null)

    (cartData ++ footer).view.zipWithIndex.foreach {
      case (b, i) =>
        val x = i % 160
        val y = i / 160
        val pixel = outputRaster.getRaster.getPixel(x, y, new Array[Int](4))
        val byteA = (b >>> 6) & 0x03
        val byteR = (b >>> 4) & 0x03
        val byteG = (b >>> 2) & 0x03
        val byteB = (b >>> 0) & 0x03

        // Java returns in RGBA even though we request ARGB...
        pixel(3) = (pixel(3) & 0xFC) | byteA
        pixel(0) = (pixel(0) & 0xFC) | byteR
        pixel(1) = (pixel(1) & 0xFC) | byteG
        pixel(2) = (pixel(2) & 0xFC) | byteB
        outputRaster.getRaster.setPixel(x, y, pixel)
    }

    ImageIO.write(outputRaster, "PNG", outputPath.toFile)
  }

  def findP8Container(driver: WebDriver): List[(List[WebElement], WebElement)] = {
    try {
      val p8Container = driver.findElement(By.id("p8_container"))
      List((Nil, p8Container))
    } catch {
      case _: org.openqa.selenium.NoSuchElementException =>
        val iframes = driver.findElements(By.tagName("iframe"))
        val recursiveContainers = (0 until iframes.size()).flatMap { idx =>
          val iframe = iframes.get(idx)
          scribe.trace("switching to iframe: " + iframe.getAttribute("src"))
          driver.switchTo().frame(idx)
          val found = findP8Container(driver)
          scribe.trace("switching out of iframe")
          driver.switchTo().parentFrame()
          if (found == null) List() else found.map {
            case (trace, container) => (iframe :: trace, container)
          }
        }
        if (recursiveContainers.isEmpty) null else recursiveContainers.toList
    }
  }

  def getCartData(driver: WebDriver with JavascriptExecutor): Vector[Byte] = {
    val cartData = driver.executeScript("return (window['_cartdat'] || null)")
    if(cartData != null) {
      val juCartData = cartData.asInstanceOf[java.util.List[Long]]
      Vector.tabulate(juCartData.size()) { idx => juCartData.get(idx).toByte }
    } else null
  }

}
