package lila.study

import akka.actor.ActorRef

import chess.format.{ Forsyth, FEN }
import lila.hub.actorApi.map.Tell
import lila.hub.Sequencer
import lila.user.User

final class StudyApi(
    repo: StudyRepo,
    jsonView: JsonView,
    sequencers: ActorRef,
    socketHub: akka.actor.ActorRef) {

  def byId = repo byId _

  def create(user: User): Fu[Study] = {
    val study = Study.make(
      owner = user.id,
      setup = Chapter.Setup(
        gameId = none,
        variant = chess.variant.Standard,
        initialFen = FEN(Forsyth.initial),
        orientation = chess.White))
    repo insert study inject study
  }

  def locationByRef(ref: Location.Ref): Fu[Option[Location]] =
    byId(ref.studyId) map (_ flatMap (_ location ref.chapterId))

  def locationById(id: Location.Ref.ID): Fu[Option[Location]] =
    (Location.Ref parseId id) ?? locationByRef

  def setOwnerPath(refId: Location.Ref.ID, path: Path) = sequenceRef(refId) {
    repo.setOwnerPath(_, path)
  }

  def addNode(refId: Location.Ref.ID, node: Node) = sequenceRef(refId) {
    ???
  }

  private def sequenceRef(refId: Location.Ref.ID)(f: Location.Ref => Funit): Funit =
    Location.Ref.parseId(refId) ?? { ref =>
      sequence(ref.studyId) {
        f(ref)
      }
    }

  private def sequence(studyId: String)(f: => Funit): Funit = {
    val promise = scala.concurrent.Promise[Unit]
    val work = Sequencer.work(f, promise.some)
    sequencers ! Tell(studyId, work)
    promise.future
  }
}
