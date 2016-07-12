import java.awt.Image
import java.awt.datatransfer.{DataFlavor, Transferable, UnsupportedFlavorException}
import java.awt.dnd.{DnDConstants, DropTarget, DropTargetDragEvent, DropTargetDropEvent, DropTargetEvent, DropTargetListener}
import java.awt.image.{BufferedImage, DataBufferByte}
import java.io.IOException
import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.{ImageIcon, JComponent, SwingUtilities, TransferHandler}

import akka.util.Timeout
import org.opencv.core._
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.swing.Swing.EmptyIcon
import scala.swing._
import scala.swing.event.ValueChanged
import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, Cancellable, Props}

import scala.collection.JavaConversions._

object HumanDetectionProtocol {
  val timeFormat = "%tY/%<tm/%<td %<tH:%<tM:%<tS"
  case class Schedule(timerMillis: Long, hitThreshold: Double, finalThreshold: Double, callback: (Mat, Int) => Unit)
  case class Detect(hitThreshold: Double, finalThreshold: Double, callback: (Mat, Int) => Unit)
}

class HumanDetectionActor extends Actor {
  import HumanDetectionProtocol._

  val system = context.system
  //implicit val executionContext = HumanDetectionActor.myExecutionContext
  implicit val executionContext = system.dispatcher

  var timer:Cancellable = null

  def receive = {
    case Schedule(timerMillis, hitThreshold, finalThreshold, callback) =>
      println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()} Schedule: hitThreshold = $hitThreshold, finalThreshold = $finalThreshold")
      if (timer != null) {
        timer.cancel()
      }
      timer = system.scheduler.scheduleOnce(timerMillis millis, self, Detect(hitThreshold, finalThreshold, callback))
    case Detect(hitThreshold, finalThreshold, callback) =>
      println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()} Detect: hitThreshold = $hitThreshold, finalThreshold = $finalThreshold")

      val (detectedImg, detectedNum) = HumanDetectionActor.detectHuman(hitThreshold, finalThreshold)
      callback(detectedImg, detectedNum)
  }

}

object HumanDetectionActor {
  import java.util.concurrent.Executors
  implicit val myExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  // ソース読み込み
  val srcImageRef = new AtomicReference[Mat](Imgcodecs.imread(getClass.getResource("/human_detection_sample.jpg").getPath))

  // グレー化
  val grayImage = new Mat()
  Imgproc.cvtColor(srcImageRef.get(), grayImage, Imgproc.COLOR_BGR2GRAY)
  Imgproc.equalizeHist(grayImage, grayImage)

  def detectHuman(hitThreshold: Double, finalThreshold: Double): (Mat, Int) = {
    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: detectHuman 1")
    // hog
    val hog = new HOGDescriptor() // デフォルト (D)
    // val hog = new HOGDescriptor(new Size(48, 96), new Size(16, 16), new Size(8, 8), new Size(8, 8), 9) // Daimler専用
    // val hog = new HOGDescriptor(new Size(32, 64), new Size(8, 8), new Size(4, 4), new Size(4, 4), 9) // ユーザー設定 (U)

    // setSVMDetectorにデータを設定する
    hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector) // デフォルト(D)
    // hog.setSVMDetector(HOGDescriptor.getDaimlerPeopleDetector) // Daimler (L)

    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: detectHuman 2")
    // detectMultiScalaを設定する
    val foundLocations = new MatOfRect()
    val foundWeights = new MatOfDouble()

    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: detectHuman 3")
    // hog.detectMultiScale(grayImage, foundLocations, foundWeights) // デフォルト (D)
    hog.detectMultiScale(HumanDetectionActor.grayImage, foundLocations, foundWeights, hitThreshold, new Size(8, 8), new Size(16, 16), 1.05, finalThreshold, false) // ユーザー設定 (U)

    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: detectHuman 4")
    println("Detected %s humans".format(foundLocations.toArray.length))

    // 検出結果を描画する
    val srcClone = HumanDetectionActor.srcImageRef.get().clone()
    foundLocations.toArray.foreach(rect => {
      Imgproc.rectangle(srcClone, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0))
    })
    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: detectHuman 5")
    (srcClone, foundLocations.toArray.length)
  }

}

object HumanDetectionDemo extends App {
  System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

  def toBufferedImage(m: Mat): Image = {
    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: toBufferedImage 1")
    val imgType =
      if ( m.channels() > 1 ) {
        BufferedImage.TYPE_3BYTE_BGR
      } else {
        BufferedImage.TYPE_BYTE_GRAY
      }

    val img = new BufferedImage(m.width(), m.height(), imgType)
    // Get the BufferedImage's backing array and copy the pixels directly into it
    val data = img.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData()
    m.get(0, 0, data)
    println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: toBufferedImage 2")
    img
  }

  val dropTargetListener = new DropTargetListener {
    override def dragOver(dtde: DropTargetDragEvent): Unit = {
    }

    override def dragExit(dte: DropTargetEvent): Unit = ???

    override def drop(dtde: DropTargetDropEvent): Unit = ???

    override def dropActionChanged(dtde: DropTargetDragEvent): Unit = ???

    override def dragEnter(dtde: DropTargetDragEvent): Unit = ???
  }

  def createWindow(imgOrNone: Option[Image]=None) = new MainFrame with DropTargetListener {
    // Windowのタイトル
    title = "Human body detection"
    // Windowのサイズ
    minimumSize = new Dimension( 850, 800 )

    val label = imgOrNone match {
      case Some(img) => new Label("", new ImageIcon(imgOrNone.orNull), Alignment.Center)
      case _ => new Label("", EmptyIcon, Alignment.Center)
    }
    val hitThresholdSlider = new Slider() {
      min = -100
      max = 100
      value = 0
    }
    val finalThresholdSlider = new Slider() {
      min = -5
      max = 20
      value = 2
    }
    val humansNumLabel = new Label("")
    // コンテンツにPanelを設定
    val panel = new BoxPanel( Orientation.Vertical ) {
      contents += label
      contents += new Label("hitThreshold")
      contents += hitThresholdSlider
      contents += new Label("finalThreshold")
      contents += finalThresholdSlider
      contents += new Label("detectedHumans")
      contents += humansNumLabel
    }

    //label.peer.setTransferHandler(createTransferHandler())
    peer.setTransferHandler(createTransferHandler())

    contents = panel

    def setImage(img: Image) = {
      this.label.peer.setIcon(new ImageIcon(img))
      //this.pack()
    }
    def setHumansNum(num: Int) = {
      this.humansNumLabel.text = s"$num"
    }

    listenTo(hitThresholdSlider, finalThresholdSlider)

    reactions += {
      case ValueChanged(source) => update(source)
    }

    def queueAndReRender(): Unit = {
      val hitThreshold = hitThresholdSlider.value.toDouble / 100
      val finalThreshold = finalThresholdSlider.value
      actor ! HumanDetectionProtocol.Schedule(300, hitThreshold, finalThreshold, (detectedImg: Mat, detectedNum: Int) => {
        runOnEDT {
          setImage(toBufferedImage(detectedImg))
          setHumansNum(detectedNum)
        }
      })
    }

    def update(source: Component): Unit = {
      source match {
        case src if src == hitThresholdSlider =>
          println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: onChange hitThreshold")
          queueAndReRender()
        case src if src == finalThresholdSlider =>
          println(s"${HumanDetectionProtocol.timeFormat format System.currentTimeMillis()}: onChange finalThreshold")
          queueAndReRender()
      }
    }

    def createTransferHandler(): TransferHandler = {
      new TransferHandler() {
        override def importData(comp: JComponent, t: Transferable): Boolean = {
          try {
            val transferData = t.getTransferData( DataFlavor.imageFlavor )
            setImage(transferData.asInstanceOf[Image])
          } catch {
            case e: UnsupportedFlavorException =>
            case e: IOException =>
          }
          true
        }

        override def canImport(comp: JComponent, transferFlavors: Array[DataFlavor]): Boolean = true
      }
    }

    override def drop(dtde: DropTargetDropEvent): Unit = {
      try {
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          dtde.acceptDrop(DnDConstants.ACTION_COPY)
          val tr = dtde.getTransferable
          val files = tr.getTransferData(DataFlavor.javaFileListFlavor).asInstanceOf[java.util.List[java.io.File]]
          files.foreach(file => {
            HumanDetectionActor.srcImageRef.set(Imgcodecs.imread(file.getAbsolutePath))
            setImage(toBufferedImage(HumanDetectionActor.srcImageRef.get()))
            queueAndReRender()
          })
          dtde.dropComplete(true)
        }
      } catch {
        case e: UnsupportedFlavorException =>
          println(e)
        case e: IOException =>
      }
    }

    override def dragOver(dtde: DropTargetDragEvent): Unit = {
      if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY)
      } else {
        dtde.rejectDrag()
      }
    }

    override def dragExit(dte: DropTargetEvent): Unit = {}

    override def dropActionChanged(dtde: DropTargetDragEvent): Unit = {}

    override def dragEnter(dtde: DropTargetDragEvent): Unit = {}

    new DropTarget(this.peer, DnDConstants.ACTION_COPY, this, true)
  }

  val window = createWindow()
  window.setImage(toBufferedImage(HumanDetectionActor.srcImageRef.get()))
  window.visible = true

  val actor = ActorSystem("schedule").actorOf(Props[HumanDetectionActor])
  implicit val timeout = Timeout(300, TimeUnit.MILLISECONDS)
  actor ! HumanDetectionProtocol.Detect(-0.20, 3, (detectedImg: Mat, detectedNum: Int) => {
    runOnEDT {
      window.setImage(toBufferedImage(detectedImg))
      window.setHumansNum(detectedNum)
    }
  })

  def runOnEDT(func: =>Unit): Unit = {
    SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        func
      }
    })
  }

}

