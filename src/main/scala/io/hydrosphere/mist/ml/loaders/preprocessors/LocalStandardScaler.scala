package io.hydrosphere.mist.ml.loaders.preprocessors

import io.hydrosphere.mist.ml.Metadata
import io.hydrosphere.mist.ml.loaders.LocalModel
import org.apache.spark.ml.feature.StandardScaler


object LocalStandardScaler extends LocalModel {
  override def localLoad(metadata: Metadata, data: Map[String, Any]): StandardScaler = {
    var scaler = new StandardScaler(metadata.uid)
      .setInputCol(metadata.paramMap("inputCol").asInstanceOf[String])
      .setOutputCol(metadata.paramMap("outputCol").asInstanceOf[String])

    metadata.paramMap.get("withMean").foreach{ x => scaler = scaler.setWithMean(x.asInstanceOf[Boolean])}
    metadata.paramMap.get("withStd").foreach{ x => scaler = scaler.setWithStd(x.asInstanceOf[Boolean])}

    scaler
  }
}
