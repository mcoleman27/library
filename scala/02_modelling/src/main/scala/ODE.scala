import java.nio.file.{Path, Paths}

import demoODESolver.{Parameters, SolutionPoint}
import org.apache.commons.io.FileUtils
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator
import org.apache.commons.math3.ode.sampling.{FixedStepHandler, StepNormalizer}
import play.api.libs.json.{JsValue, Json, OFormat}
import sampler.r.script.RScript

import scala.collection.mutable

trait ODE extends FirstOrderDifferentialEquations {

  def solve(initialValue: Array[Double], startTime: Double, endTime: Double, stepSize: Double): Seq[(Double, Array[Double])] = {
    assume(initialValue.length == this.getDimension, "Dimension of initial condition does not match dimension of ODE")

    val relTol = 1e-10
    val absTol = 1e-10
    val minStep = 1e-8
    val maxStep = 100.0

    val steps = mutable.Buffer[(Double, Array[Double])]()

    val stepHandler: FixedStepHandler = new FixedStepHandler {
      def init(t0: Double, y0: Array[Double], t: Double): Unit = {}

      def handleStep(t: Double, y: Array[Double], yDot: Array[Double], isLast: Boolean): Unit = {
        val rounded = y.map { value => if (math.abs(value) < 1e-2) 0 else value }
        steps.append((t, rounded))
      }
    }

    val stepNormalizer = new StepNormalizer(stepSize, stepHandler)
    val dp853 = new DormandPrince853Integrator(minStep, maxStep, absTol, relTol)
    val out = Array.fill(getDimension)(0.0)

    dp853.addStepHandler(stepNormalizer)

    // Nothing is returned in this call as the stepHander is mutated during integration
    dp853.integrate(this, startTime, initialValue, endTime, out)

    steps
  }
}

object demoODESolver extends App {

  case class Parameters(alpha: Double)

  case class SolutionPoint(time: Double, x: Double, y: Double)
  object SolutionPoint{
    implicit val dataWrite: OFormat[SolutionPoint] = Json.format[SolutionPoint]
  }

  case class MyODE(p: Parameters) extends ODE {
    override def getDimension: Int = 2

    override def computeDerivatives(t: Double, y: Array[Double], yDot: Array[Double]): Unit = {
      yDot(0) = p.alpha * y(1)
      yDot(1) = -p.alpha * y(0)
    }
  }

  val y0 = Array(10.0, 20.0)
  val p = Parameters(2.2)
  val startTime = 0
  val endTime = 5
  val stepSize = 0.2

  val ode = MyODE(p)
  val odeSolution: Seq[SolutionPoint] = ode
      .solve(y0, startTime, endTime, stepSize)
      .map { case (t, arr) => SolutionPoint(t, arr(0), arr(1)) }

  CompareSolutionWithR.saveOutput(odeSolution)

  CompareSolutionWithR.plotODESolutions(p, y0, startTime, endTime, stepSize)
}

object CompareSolutionWithR {
  val outDir: Path = Paths.get("out")
  val scalaResultsFileName = "commons-math-result.json"

  def saveOutput(odeSolution: Seq[SolutionPoint]): Unit = {
    FileUtils.writeStringToFile(
      outDir.resolve(scalaResultsFileName).toFile,
      Json.prettyPrint(Json.toJson(odeSolution))
    )
  }

  def plotODESolutions(p: Parameters, y0: Array[Double], startTime: Double, endTime: Double, stepSize: Double): Unit = {
    val lineWidth = 1.5
    val script =
      s"""
         |lapply(c("ggplot2", "reshape2", "deSolve", "jsonlite","plyr"), require, character.only=T)
         |pdf("compareODESolver.pdf", width=8, height=4, title = "compare ODE Solutions")
         |
         |output = (fromJSON("${scalaResultsFileName}"))
         |scalaData = melt(output,id=c("time"))
         |scalaData$$solver = "Scala"
         |
         |params = c(
         |  alpha = ${p.alpha}
         |)
         |
         |Y0 = c(
         |  x = ${y0(0)},
         |  y = ${y0(1)}
         |)
         |dY <-function(t, state, parameters) {
         |  with(as.list(c(state, parameters)),{
         |    dx <- alpha * y
         |    dy <- -alpha * x
         |
         |list(c(dx, dy))
         |  })
         |}
         |
         |times = seq(${startTime}, ${endTime}, by = ${stepSize})
         |out <- ode(y = Y0, times = times, func = dY, parms = params)
         |rData = melt(as.data.frame(out), id="time")
         |rData$$solver = "R"
         |
         |data = rbind(scalaData,rData)
         |
         |ggplot(data, aes(x=time, y=value,colour=variable)) +
         |  geom_line(size=$lineWidth) +
         |  facet_grid(solver~.) +
         |  theme(text = element_text(size = 20)) +
         |  scale_x_continuous(breaks = c($startTime : $endTime), labels = c($startTime : $endTime)) +
         |  ggtitle("Comparison of solutions by R and Scala")
         |
         |#Some ugly hacking going on here with the time variable to make sure dcast works
         |data$$time = paste(data$$time)
         |wide_data = dcast(data,time+variable~solver,value.var="value")
         |wide_data$$error = wide_data$$R - wide_data$$Scala
         |wide_data$$time = as.numeric(wide_data$$time)
         |
         |ggplot(wide_data,aes(x=time,y=error,colour=variable)) +
         |  geom_line(size=$lineWidth) +
         |  theme(text = element_text(size = 20)) +
         |  scale_x_continuous(breaks = c($startTime : $endTime), labels = c($startTime : $endTime)) +
         |  ggtitle("Error between R and Scala solution")
      """.stripMargin
    RScript(script, outDir.resolve("myScript.R"))
  }
}