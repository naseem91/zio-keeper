package zio.keeper.membership

import zio.keeper.membership.GossipState.StateDiff

import scala.collection.immutable.SortedSet

case class GossipState(members: SortedSet[Member]) extends AnyVal {

  def addMember(member: Member): GossipState =
    copy(members = this.members + member)

  def diff(other: GossipState): StateDiff =
    StateDiff(
      this.members.diff(other.members),
      other.members.diff(this.members)
    )

  def merge(other: GossipState): GossipState =
    copy(members = this.members ++ other.members)

  def removeMember(member: Member): GossipState =
    copy(members = this.members - member)

  override def toString: String = s"GossipState[${members.mkString(",")}] "
}

object GossipState {
  val Empty = GossipState(SortedSet())

  final case class StateDiff(local: SortedSet[Member], remote: SortedSet[Member])

}
