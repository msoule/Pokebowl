package pokebowl.model.play.offense

import pokebowl.game.GameState
import pokebowl.model.play.PlayResult._
import pokebowl.model.play.defense.{ZoneCoverage, DefensivePlay}
import pokebowl.model.play.{Play, PlayResult}
import pokebowl.model.team.StatGlossary
import pokebowl.model.team.StatGlossary.StatGlossary
import pokebowl.model.team.{Player, StatGlossary, Team}

import scala.util.Random

/**
  * @author Mark Soule on 1/28/16.
  */
class ScreenPass extends OffensivePlay {
  override def calculateOdds(offense: Team, defense: Team, defensePlay: DefensivePlay): Array[PlayResult] = {
    defensePlay match {
      case play: ZoneCoverage => calculateOdds(offense, defense, play)
      // default be average
      case _ => Array.fill(100)(PlayResult.Average)
    }
  }

  private def calculateOdds(offense: Team, defense: Team, defensePlay: ZoneCoverage): Array[PlayResult] = {
    val resultArray = new Array[PlayResult](100)

    // interceptions
    val defenders = defense.safety ++ defense.cornerBack ++ defense.middleLinebacker ++ defense.outsideLinebacker
    val totalInterceptions = totalStats(defenders, StatGlossary.Int)
    val offenseInterceptionP = offense.quarterBack.stats(StatGlossary.IntP)
    val padding = 3
    val interceptionIndex = (offenseInterceptionP / 100) * totalInterceptions * padding

    // fumbles
    val totalFumbles = totalStats(offense.wideReceiver, StatGlossary.Fum)
    val fumblesRecovered = totalStats(defenders, StatGlossary.Ff)
    val fumblePadding = 0.8
    val fumbleIndex = (totalFumbles + fumblesRecovered) * fumblePadding

    // incomplete
    val passPadding = 15
    val incompleteIndex = 100 - offense.quarterBack.stats(StatGlossary.Pct) - passPadding

    resultArray(0) = PlayResult.Terrible
    resultArray(interceptionIndex.asInstanceOf[Int]) = PlayResult.Terrible
    resultArray(interceptionIndex.asInstanceOf[Int] + 1) = PlayResult.VeryBad
    resultArray((interceptionIndex + fumbleIndex).asInstanceOf[Int]) = PlayResult.VeryBad
    resultArray((interceptionIndex + fumbleIndex).asInstanceOf[Int] + 1) = PlayResult.Bad
    resultArray((interceptionIndex + fumbleIndex + incompleteIndex).asInstanceOf[Int]) = PlayResult.Bad
    resultArray((interceptionIndex + fumbleIndex + incompleteIndex).asInstanceOf[Int] + 1) = PlayResult.Average
    resultArray(99) = PlayResult.Great

    fillArray(resultArray, 0)
  }

  private def totalStats(players: Seq[Player], stat: StatGlossary): Int = {
    var total: Double = 0
    players.foreach{total += _.stats(stat)}
    total.asInstanceOf[Int]
  }

  /**
    * Fill the empty slots of resultArray. It will fill empty slots with the value of
    * the previous slot. So only border and terminal slots need to be set.
    */
  private def fillArray(resultArray: Array[PlayResult], index: Int): Array[PlayResult] = index match {
    case i if i >= 100 => resultArray
    // assumes the 0th position is filled
    case i if i == 0 => fillArray(resultArray, i + 1)
    case i =>
      if (resultArray(i) == null)
        resultArray(i) = resultArray(i - 1)
      fillArray(resultArray, i + 1)
  }

  override def getDisplayText: String = "try a Screen Pass"

  override def getName: String = "SCREEN PASS"

  override def calculateResult(state: GameState, result: PlayResult): Seq[String] = {
    var messages = Seq[String]()
    result match {
      case PlayResult.Terrible =>
        messages = messages :+ s"${state.possession.quarterBack.last} passes the ball..."
        val interceptor: Player = state.getNonPossessingTeam.safety(GameState.rand.nextInt(state.getNonPossessingTeam.safety.size))
        messages = messages :+ s"Intercepted by ${interceptor.last}!"
        state.changePossession()
        messages = messages ++ state.changeLineOfScrimmage(state.MAX_YARDS - state.lineOfScrimmage + (interceptor.stats(StatGlossary.Yds)/16).asInstanceOf[Int])
        messages = messages :+ s"${state.possession.name} get possession"
      case PlayResult.VeryBad =>
        messages = messages :+ s"${state.possession.quarterBack.last} passes the ball..."
        val fumbler: Player = state.possession.wideReceiver(GameState.rand.nextInt(state.possession.wideReceiver.size))
        messages = messages :+ s"Fumbled by ${fumbler.last}!"
        messages = messages ++ state.advanceDowns()
      case PlayResult.Bad =>
        messages = messages :+ s"${state.possession.quarterBack.last} passes the ball..."
        messages = messages :+ s"Incomplete"
        messages = messages ++ state.advanceDowns()
      case PlayResult.Average =>
        messages = messages :+ s"${state.possession.quarterBack.last} passes the ball..."
        val receptor: Player = state.possession.wideReceiver(GameState.rand.nextInt(state.possession.wideReceiver.size))
        val balanceScaling = (GameState.rand.nextInt(10) + 2) * .1
        val yards = (receptor.stats(StatGlossary.Avg) * balanceScaling).asInstanceOf[Int]
        messages = messages :+ s"Caught by ${receptor.last} for $yards yards"
        val firstDown = state.lineOfScrimmage + yards > state.firstDownMarker
        messages = messages ++ state.changeLineOfScrimmage(state.lineOfScrimmage + yards)
        if(!firstDown)
          messages = messages ++ state.advanceDowns()
      case _ =>
        messages = messages :+ s"${state.possession.quarterBack.last} passes the ball..."
        val receptor: Player = state.possession.wideReceiver(GameState.rand.nextInt(state.possession.wideReceiver.size))
        messages = messages :+ s"Caught by ${receptor.last}..."
        messages = messages ++ state.changeLineOfScrimmage(state.MAX_YARDS)
    }
    messages
  }

}
