package mist.api

import mist.api.args.{ArgCombiner, ToJobDef}
import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaSparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.api.java.JavaStreamingContext

trait BaseContexts {

  val sparkContext: ArgDef[SparkContext] = ArgDef.create(InternalArgument)(ctx => Extracted(ctx.setupConfiguration.context))
  val streamingContext: ArgDef[StreamingContext] = ArgDef.create(InternalArgument)(ctx => {
    val conf = ctx.setupConfiguration
    Extracted(
      StreamingContext.getActiveOrCreate(() => new StreamingContext(conf.context, conf.streamingDuration))
    )
  })

  val sqlContext: ArgDef[SQLContext] = sparkContext.map(SQLContext.getOrCreate)
  val hiveContext: ArgDef[HiveContext] = sparkContext.map(sc => new HiveContext(sc))
  val javaSparkContext: ArgDef[JavaSparkContext] = sparkContext.map(sc => new JavaSparkContext(sc))
  val javaStreamingContext: ArgDef[JavaStreamingContext] = streamingContext.map(scc => new JavaStreamingContext(scc))

  implicit class ContextsOps[A](args: ArgDef[A]) {

    def onSparkContext[F, Cmb, Out](f: F)(
      implicit
      cmb: ArgCombiner.Aux[A, SparkContext, Cmb],
      tjd: ToJobDef.Aux[Cmb, F, Out]): JobDef[Out] = tjd(args.combine(sparkContext), f)

    def onStreamingContext[F, Cmb, Out](f: F)(
      implicit
      cmb: ArgCombiner.Aux[A, StreamingContext, Cmb],
      tjd: ToJobDef.Aux[Cmb, F, Out]): JobDef[Out] = tjd(args.combine(streamingContext), f)

    def onSqlContext[F, Cmb, Out](f: F)(
      implicit
      cmb: ArgCombiner.Aux[A, SQLContext, Cmb],
      tjd: ToJobDef.Aux[Cmb, F, Out]): JobDef[Out] = tjd(args.combine(sqlContext), f)

    def onHiveContext[F, Cmb, Out](f: F)(
      implicit
      cmb: ArgCombiner.Aux[A, HiveContext, Cmb],
      tjd: ToJobDef.Aux[Cmb, F, Out]): JobDef[Out] = tjd(args.combine(hiveContext), f)
  }

  def onSparkContext[F, Out](f: F)(implicit tjd: ToJobDef.Aux[SparkContext, F, Out]): JobDef[Out] =
    tjd(sparkContext, f)

  def onStreamingContext[F, Out](f: F)(implicit tjd: ToJobDef.Aux[StreamingContext, F, Out]): JobDef[Out] =
    tjd(streamingContext, f)

  def onSqlContext[F, Out](f: F)(implicit tjd: ToJobDef.Aux[SQLContext, F, Out]): JobDef[Out] =
    tjd(sqlContext, f)

  def onHiveContext[F, Out](f: F)(implicit tjd: ToJobDef.Aux[HiveContext, F, Out]): JobDef[Out] =
    tjd(hiveContext, f)

}

object BaseContexts extends BaseContexts