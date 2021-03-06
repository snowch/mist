package io.hydrosphere.mist.master

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.TestKit
import io.hydrosphere.mist.core.CommonData.{Action, JobParams, RunJobRequest}
import io.hydrosphere.mist.core.{JvmJobInfo, MockitoSugar, PyJobInfo}
import io.hydrosphere.mist.master.artifact.ArtifactRepository
import io.hydrosphere.mist.master.data.{ContextsStorage, EndpointsStorage}
import io.hydrosphere.mist.master.models._
import org.mockito.Mockito.{doReturn, spy}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpecLike, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MainServiceSpec extends TestKit(ActorSystem("testMasterService"))
  with FunSpecLike
  with Matchers
  with MockitoSugar {

  import TestUtils._

  it("should run job") {
    val endpoints = mock[EndpointsStorage]
    val contexts = mock[ContextsStorage]
    val jobService = mock[JobService]
    val logs = mock[LogStoragePaths]
    val artifactRepo = mock[ArtifactRepository]

    when(endpoints.get(any[String]))
      .thenSuccess(Some(EndpointConfig("name", "path.py", "MyJob", "namespace")))

    when(contexts.getOrDefault(any[String]))
      .thenSuccess(TestUtils.contextSettings.default)

    when(artifactRepo.get(any[String]))
      .thenReturn(Some(new File("path.py")))

    when(jobService.startJob(any[JobStartRequest])).thenSuccess(ExecutionInfo(
        req = RunJobRequest("id", JobParams("path.py", "MyJob", Map("x" -> 1), Action.Execute)),
        status = JobDetails.Status.Queued
      ))

    val service = new MainService(jobService, endpoints, contexts, logs, artifactRepo)

    val req = EndpointStartRequest("name", Map("x" -> 1), Some("externalId"))
    val runInfo = service.runJob(req, JobDetails.Source.Http).await
    runInfo shouldBe defined
  }

  it("should return failed future on validating params") {
    val endpoints = mock[EndpointsStorage]
    val contexts = mock[ContextsStorage]
    val jobService = mock[JobService]
    val logs = mock[LogStoragePaths]
    val artifactRepo = mock[ArtifactRepository]
    val jvmMock = mock[JvmJobInfo]

    val info = FullEndpointInfo(
      EndpointConfig("name", "path", "MyJob", "namespace"),
      jvmMock
    )
    when(jvmMock.validateAction(any[Map[String, Any]], any[Action]))
      .thenReturn(Left(new IllegalArgumentException("INVALID")))

    val service = new MainService(jobService, endpoints, contexts, logs, artifactRepo)

    val spiedMasterService = spy(service)

    doReturn(Future.successful(Some(info)))
      .when(spiedMasterService)
      .endpointInfo(any[String])

    val req = EndpointStartRequest("scalajob", Map("notNumbers" -> Seq(1, 2, 3)), Some("externalId"))
    val f = spiedMasterService.runJob(req, JobDetails.Source.Http)

    ScalaFutures.whenReady(f.failed) { ex =>
      ex shouldBe a[IllegalArgumentException]
    }

  }
  it("should fail job execution when context config filled with incorrect worker mode") {
    val endpoints = mock[EndpointsStorage]
    val contexts = mock[ContextsStorage]
    val jobService = mock[JobService]
    val logs = mock[LogStoragePaths]
    val artifactRepository = mock[ArtifactRepository]
    val service = new MainService(jobService, endpoints, contexts, logs, artifactRepository)
    val spiedService = spy(service)
    val fullInfo = FullEndpointInfo(
      EndpointConfig("name", "path", "MyJob", "namespace"),
      PyJobInfo
    )
    doReturn(Future.successful(Some(fullInfo)))
      .when(spiedService)
      .endpointInfo(any[String])

    when(contexts.getOrDefault(any[String]))
      .thenSuccess(ContextConfig(
        "default",
        Map.empty,
        Duration.Inf,
        20,
        precreated = false,
        "",
        "wrong_mode",
        1 seconds
      ))


    val req = EndpointStartRequest("name", Map("x" -> 1), Some("externalId"))
    val runInfo = spiedService.runJob(req, JobDetails.Source.Http)

    ScalaFutures.whenReady(runInfo.failed) {ex =>
      ex shouldBe an[IllegalArgumentException]
    }

  }
  it("should return only existing endpoint jobs") {
    val endpoints = mock[EndpointsStorage]
    val contexts = mock[ContextsStorage]
    val jobService = mock[JobService]
    val logs = mock[LogStoragePaths]
    val artifactRepository = mock[ArtifactRepository]
    val service = new MainService(jobService, endpoints, contexts, logs, artifactRepository)
    val spiedService = spy(service)
    val epConf = EndpointConfig("name", "path", "MyJob", "namespace")
    val noMatterEpConf = EndpointConfig("no_matter", "testpath", "MyJob2", "namespace")
    val fullInfo = FullEndpointInfo(epConf, PyJobInfo)

    doReturn(Success(fullInfo))
      .when(spiedService)
      .loadEndpointInfo(epConf)

    doReturn(Failure(new RuntimeException("failed")))
      .when(spiedService)
      .loadEndpointInfo(noMatterEpConf)

    when(endpoints.all)
      .thenSuccess(Seq(epConf, noMatterEpConf))

    val endpointsInfo = spiedService.endpointsInfo.await
    endpointsInfo.size shouldBe 1
    endpointsInfo should contain allElementsOf Seq(fullInfo)

  }

}
