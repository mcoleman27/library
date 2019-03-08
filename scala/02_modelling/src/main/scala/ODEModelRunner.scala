import org.apache.commons.math3.ode.FirstOrderDifferentialEquations
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator
import org.apache.commons.math3.ode.sampling.{FixedStepHandler, StepNormalizer}

import scala.collection.mutable

trait ODE extends FirstOrderDifferentialEquations {
  type State
//  trait State

//  val params: P
  val y0: State

  def toArray(s: State): Array[Double]
  def toState(arr: Array[Double]): State
//  override def getDimension: Int = y0.toArray.length
}

trait State {
  def toArray: Array[Double]
}

object ODESolver {
//  val timeSpan: Range
//  val ode: ODE

//  def stateFromArray(a: Array[Double]): ODE.State

  def test(ode: ODE): ode.State = ???

  def solve[C <: ODE](ode: C, timeSpan: Range): Map[Int, ode.State] = {
    val relTol = 1e-11
    val absTol = 1e-11
    val minStep = 1e-8
    val maxStep = 100.0
    val timeZero = timeSpan.min
    val numDays = timeSpan.length + 1

    val steps = mutable.Buffer[ode.State]()

    val stepHandler: FixedStepHandler = new FixedStepHandler {
      override def init(t0: Double, y0: Array[Double], t: Double): Unit = {}

      override def handleStep(t: Double, y: Array[Double], yDot: Array[Double], isLast: Boolean): Unit = {
        steps.append(ode.toState(y))
      }
    }

    val stepNormalizer = new StepNormalizer(1.0, stepHandler)
    val dp853 = new DormandPrince853Integrator(minStep, maxStep, absTol, relTol)
    val out = Array.fill(ode.getDimension)(0.0)

    dp853.addStepHandler(stepNormalizer)
    dp853.integrate(ode, timeZero, ode.toArray(ode.y0), numDays, out)

    val integrated: List[ode.State] = steps.toList

    timeSpan.foldLeft(Map[Int, ode.State]()) { case (map, t) => map.updated(t, integrated(t)) }
  }

  def thing(ode: ODE): Unit ={
    val t: ode.State = test(ode)
  }

  def solveAndPrint[C <: ODE](ode: C, range: Range): Unit = {
//    type T = ode.State
//    val t: ode.State = test(ode)
    val sortedTimeSeries: Seq[(Int, C#State)] = solve(ode, range).toSeq.sortBy(_._1)
    for (state <- sortedTimeSeries) {
      println(s"t = ${state._1}, ${state._2}")
    }
  }
}