import org.opencv.core.{Core, MatOfRect, Point, Scalar}
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier

object DetectFaceDemo extends App {
  System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

  // Create a face detector from the cascade file in the resources
  // directory.
  val faceDetector = new CascadeClassifier(getClass.getResource("/lbpcascade_frontalface.xml").getPath)
  val image = Imgcodecs.imread(getClass.getResource("/lena.png").getPath)

  // Detect faces in the image.
  // MatOfRect is a special container class for Rect.
  val faceDetections = new MatOfRect()
  faceDetector.detectMultiScale(image, faceDetections)

  println("Detected %s faces".format(faceDetections.toArray.size))

  faceDetections.toArray.foreach(rect => {
    Imgproc.rectangle(image, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0, 0))
  })

  // Save the visualized detection.
  val filename = "faceDetection.png"
  System.out.println(String.format("Writing %s", filename))
  Imgcodecs.imwrite(filename, image)
}
