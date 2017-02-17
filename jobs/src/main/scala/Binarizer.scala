import io.hydrosphere.mist.lib._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.Binarizer


object Binarizer extends MLMistJob with SQLSupport {
  def train(): Map[String, Any] = {

    val data = Array((0, 0.1), (1, 0.8), (2, 0.2))
    val dataFrame = session.createDataFrame(data).toDF("id", "feature")

    val binarizer: Binarizer = new Binarizer()
      .setInputCol("feature")
      .setOutputCol("binarized_feature")
      .setThreshold(5.0)

    val pipeline = new Pipeline().setStages(Array(binarizer))

    val model = pipeline.fit(dataFrame)

    model.write.overwrite().save("models/binarizer")
    Map.empty[String, Any]
  }

  // TODO: test
  def serve(features: List[Double]): Map[String, Any] = {
    import io.hydrosphere.mist.ml.transformers.LocalTransformers._

    val pipeline = PipelineLoader.load("models/binarizer")
    val data = LocalData(
      LocalDataColumn("feature", features)
    )

    val result: LocalData = pipeline.transform(data)
    Map("result" -> result.select("feature", "binarized_feature").toMapList)
  }
}
