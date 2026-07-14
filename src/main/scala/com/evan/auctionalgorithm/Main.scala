package com.evan.auctionalgorithm

import scala.io.Source
import scala.util.Random

object Main:

  private val usageText =
    """auction - solve n-by-n assignment problems (Bertsekas auction algorithm)
      |
      |Usage:
      |  auction solve [FILE] [--json] [--parallel]   Solve a problem file (stdin if FILE
      |                                               is omitted or '-').
      |  auction generate N [options]                 Emit a random N-by-N problem file.
      |    --lo A          minimum cost (default 1)
      |    --hi B          maximum cost (default N)
      |    --seed S        RNG seed (default 12345)
      |    --objective D   'min' (default) or 'max'
      |  auction --help
      |
      |Problem file format (same as the C++ sibling repo):
      |  # comment lines and blank lines are ignored
      |  objective min    # optional: min (default) or max
      |  n 3              # problem size, before the matrix
      |  4 1 3            # n rows of n integer costs;
      |  2 0 5            # row i, column j = cost of object j for person i
      |  3 2 2
      |""".stripMargin

  def main(args: Array[String]): Unit =
    val exitCode = run(args.toList)
    if exitCode != 0 then sys.exit(exitCode)

  def run(args: List[String]): Int = args match
    case Nil                             => System.err.println(usageText); 2
    case ("--help" | "-h" | "help") :: _ => println(usageText); 0
    case "solve" :: rest                 => solveCommand(rest)
    case "generate" :: rest              => generateCommand(rest)
    case other :: _ =>
      System.err.println(s"error: unknown command '$other'\n")
      System.err.println(usageText)
      2

  private final case class SolveArgs(json: Boolean, parallel: Boolean, path: Option[String])

  private def parseSolveArgs(args: List[String]): Either[String, SolveArgs] =
    args.foldLeft[Either[String, SolveArgs]](Right(SolveArgs(false, false, None))) {
      case (Right(acc), "--json")                => Right(acc.copy(json = true))
      case (Right(acc), "--parallel")            => Right(acc.copy(parallel = true))
      case (Right(acc), arg) if acc.path.isEmpty => Right(acc.copy(path = Some(arg)))
      case (Right(_), arg)                       => Left(s"unexpected argument '$arg'")
      case (left, _)                             => left
    }

  private def solveCommand(args: List[String]): Int =
    parseSolveArgs(args) match
      case Left(message) =>
        System.err.println(s"error: $message")
        2
      case Right(SolveArgs(json, parallel, path)) =>
        readInput(path) match
          case Left(message) =>
            System.err.println(s"error: $message")
            2
          case Right(text) =>
            solveText(text, json, parallel)

  private def readInput(path: Option[String]): Either[String, String] =
    try
      path match
        case None | Some("-") => Right(Source.stdin.mkString)
        case Some(file)       => Right(Source.fromFile(file).mkString)
    catch
      case e: java.io.IOException => Left(s"cannot open '${path.getOrElse("-")}': ${e.getMessage}")

  private def solveText(text: String, json: Boolean, parallel: Boolean): Int =
    val parsed =
      try Right(Problem.parse(text))
      catch case e: ProblemParseException => Left(e.getMessage)
    parsed match
      case Left(message) =>
        System.err.println(s"error: invalid problem file: $message")
        2
      case Right(problem) =>
        val result =
          Auction.solve(
            problem.costs,
            problem.n,
            Options(objective = problem.objective, parallel = parallel)
          )
        printResult(problem, result, json)
        0

  private def printResult(problem: Problem, result: Result, json: Boolean): Unit =
    val objectiveName = if problem.objective == Objective.Maximize then "max" else "min"
    if json then
      val assignment = result.assignment.mkString(", ")
      println(
        s"""{"n": ${problem.n}, "objective": "$objectiveName", "total_cost": ${result.totalCost}, """ +
          s""""phases": ${result.phases}, "rounds": ${result.rounds}, "assignment": [$assignment]}"""
      )
    else
      println(s"objective: $objectiveName")
      println(s"n: ${problem.n}")
      println(s"total_cost: ${result.totalCost}")
      println(s"phases: ${result.phases}")
      println(s"rounds: ${result.rounds}")
      println("assignment:")
      for i <- 0 until problem.n do println(s"  $i -> ${result.assignment(i)}")

  private final case class GenArgs(lo: Long, hi: Long, seed: Long, objective: Objective)

  @annotation.tailrec
  private def parseGenArgs(args: List[String], acc: GenArgs): Either[String, GenArgs] = args match
    case Nil => Right(acc)
    case "--lo" :: value :: tail =>
      value.toLongOption match
        case Some(v) => parseGenArgs(tail, acc.copy(lo = v))
        case None    => Left("invalid value for --lo")
    case "--hi" :: value :: tail =>
      value.toLongOption match
        case Some(v) => parseGenArgs(tail, acc.copy(hi = v))
        case None    => Left("invalid value for --hi")
    case "--seed" :: value :: tail =>
      value.toLongOption match
        case Some(v) => parseGenArgs(tail, acc.copy(seed = v))
        case None    => Left("invalid value for --seed")
    case "--objective" :: "min" :: tail =>
      parseGenArgs(tail, acc.copy(objective = Objective.Minimize))
    case "--objective" :: "max" :: tail =>
      parseGenArgs(tail, acc.copy(objective = Objective.Maximize))
    case "--objective" :: _ => Left("--objective must be 'min' or 'max'")
    case other :: _         => Left(s"unknown option '$other'")

  private def generateCommand(args: List[String]): Int =
    val n = args.headOption.flatMap(_.toIntOption).getOrElse(0)
    if n < 1 then
      System.err.println("error: generate requires a positive integer size N")
      return 2

    parseGenArgs(
      args.tail,
      GenArgs(lo = 1L, hi = n.toLong, seed = 12345L, Objective.Minimize)
    ) match
      case Left(message) =>
        System.err.println(s"error: $message")
        2
      case Right(GenArgs(lo, hi, seed, objective)) if lo > hi =>
        System.err.println("error: --lo must be <= --hi")
        2
      case Right(GenArgs(lo, hi, seed, objective)) =>
        val rng = Random(seed)
        val costs = Vector.fill(n * n)(lo + math.floorMod(rng.nextLong(), hi - lo + 1))
        println(s"# random assignment problem: n=$n costs in [$lo, $hi] seed=$seed")
        print(Problem.format(Problem(n, objective, costs)))
        0
