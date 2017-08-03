package io.hydrosphere.mist.master

import java.io.File
import io.hydrosphere.mist.master.data.ContextsStorage
import io.hydrosphere.mist.master.models.{ContextConfig, RunMode}
import io.hydrosphere.mist.utils.Logger

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.sys.process._

trait WorkerRunner {

  def runWorker(name: String, context: String, mode: RunMode): Unit

  def onStop(name: String): Unit = {}
}

trait ShellWorkerScript {

  def workerArgs(
    name: String,
    context: String,
    mode: RunMode,
    config: MasterConfig,
    contextsStorage: ContextsStorage
  ): Seq[String] = {

    // TODO!!!!!
    val contextConfig = Await.result(contextsStorage.getOrDefault(context), 10 second)

    Seq[String](
      "--master", s"${config.cluster.host}:${config.cluster.port}",
      "--name", name,
      "--context-name", context,
      "--max-jobs", contextConfig.maxJobs.toString,
      "--downtime", durationToArg(contextConfig.downtime),
      "--spark-streaming-duration", durationToArg(contextConfig.streamingDuration),
      "--log-service", s"${config.logs.host}:${config.logs.port}",
      "--mode", mode.name
    ) ++ mkSparkConf(contextConfig)
  }

  def mkSparkConf(ctxConfig: ContextConfig): Seq[String] = {
    ctxConfig.sparkConf.toList.map({case (k, v) => s"$k=$v"})
      .flatMap(p=> Seq("--spark-conf", p))
  }

  def durationToArg(d: Duration): String = d match {
    case f: FiniteDuration => s"${f.toSeconds}s"
    case _ => "Inf"
  }

}

/**
  * Spawn workers on the same host
  */
class LocalWorkerRunner(config: MasterConfig, contextsStorage: ContextsStorage)
  extends WorkerRunner with ShellWorkerScript with Logger {

  override def runWorker(name: String, context: String, mode: RunMode): Unit = {
    val cmd =
      Seq[String](s"${sys.env("MIST_HOME")}/bin/mist-worker", "--runner", "local") ++
      workerArgs(name, context, mode, config, contextsStorage)

    val builder = Process(cmd)
    builder.run(false)
  }

}

class DockerWorkerRunner(config: MasterConfig, contextsStorage: ContextsStorage)
  extends WorkerRunner with ShellWorkerScript {

  override def runWorker(name: String, context: String, mode: RunMode): Unit = {
    val cmd =
      Seq(s"${sys.env("MIST_HOME")}/bin/mist-worker",
          "--runner", "docker",
          "--docker-host", config.workers.dockerHost,
          "--docker-port", config.workers.dockerPort.toString) ++
      workerArgs(name, context, mode, config, contextsStorage)
    val builder = Process(cmd)
    builder.run(false)
  }

}

class ManualWorkerRunner(
  config: MasterConfig,
  contextsStorage: ContextsStorage,
  jarPath: String) extends WorkerRunner {

  override def runWorker(name: String, context: String, mode: RunMode): Unit = {
    val contextConfig = Await.result(contextsStorage.getOrDefault(context), 10 second)
    Process(
      Seq("bash", "-c", config.workers.cmd),
      None,
      "MIST_WORKER_NAMESPACE" -> name,
      "MIST_WORKER_JAR_PATH" -> jarPath,
      "MIST_WORKER_RUN_OPTIONS" -> contextConfig.runOptions
    ).run(false)
  }

  override def onStop(name: String): Unit = withStopCommand { cmd =>
    Process(
      Seq("bash", "-c", cmd),
      None,
      "MIST_WORKER_NAMESPACE" -> name
    ).run(false)
  }

  private def withStopCommand(f: String => Unit): Unit = {
    val cmd = config.workers.cmdStop
    if (cmd.nonEmpty) f(cmd)
  }

}


object WorkerRunner {

  def create(config: MasterConfig, contextsStorage: ContextsStorage): WorkerRunner = {
    val runnerType = config.workers.runner
    runnerType match {
      case "local" =>
        sys.env.get("SPARK_HOME") match {
          case None => throw new IllegalStateException("You should provide SPARK_HOME env variable for local runner")
          case Some(home) => new LocalWorkerRunner(config, contextsStorage)
        }
      case "docker" => new DockerWorkerRunner(config, contextsStorage)
      case "manual" =>
        val jarPath = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath).toString
        new ManualWorkerRunner(config, contextsStorage, jarPath)
      case _ =>
        throw new IllegalArgumentException(s"Unknown worker runner type $runnerType")

    }
  }
}

