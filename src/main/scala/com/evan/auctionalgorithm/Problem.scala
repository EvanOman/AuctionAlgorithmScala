package com.evan.auctionalgorithm

/** An assignment problem parsed from the text problem format (identical to the format used by the
  * C++ sibling repo, github.com/EvanOman/AuctionAlgorithmCPP):
  *
  * {{{
  * # Blank lines and lines starting with '#' are ignored.
  * objective min        # optional: "min" (default) or "max"
  * n 3                  # required, before the matrix rows
  * 4 1 3                # then n rows of n integer costs
  * 2 0 5                # row i, column j = cost of object j for person i
  * 3 2 2
  * }}}
  */
final case class Problem(n: Int, objective: Objective, costs: Vector[Long])

final class ProblemParseException(message: String) extends Exception(message)

object Problem:

  def parse(text: String): Problem =
    var n = -1
    var objective: Option[Objective] = None
    val costs = Vector.newBuilder[Long]
    var rowsRead = 0

    def fail(lineNumber: Int, message: String): Nothing =
      throw ProblemParseException(s"line $lineNumber: $message")

    val lines = text.linesIterator.zipWithIndex
    var lastLine = 0
    for (rawLine, index) <- lines do
      val lineNumber = index + 1
      lastLine = lineNumber
      val line = rawLine.takeWhile(_ != '#').trim
      if line.nonEmpty then
        val tokens = line.split("\\s+").toVector
        tokens.head match
          case "objective" =>
            if objective.isDefined then fail(lineNumber, "duplicate 'objective' line")
            if rowsRead > 0 then fail(lineNumber, "'objective' must come before matrix rows")
            tokens.lift(1) match
              case Some("min") => objective = Some(Objective.Minimize)
              case Some("max") => objective = Some(Objective.Maximize)
              case Some(other) =>
                fail(lineNumber, s"objective must be 'min' or 'max', got '$other'")
              case None => fail(lineNumber, "expected 'min' or 'max' after 'objective'")
          case "n" =>
            if n >= 0 then fail(lineNumber, "duplicate 'n' line")
            val value = tokens.lift(1).flatMap(_.toIntOption).getOrElse(-1)
            if value < 1 then fail(lineNumber, "expected a positive integer after 'n'")
            n = value
          case _ =>
            if n < 0 then fail(lineNumber, "expected 'n <size>' before matrix rows")
            if rowsRead == n then fail(lineNumber, "more matrix rows than n")
            val row =
              tokens.map(t => t.toLongOption.getOrElse(fail(lineNumber, s"invalid integer '$t'")))
            if row.size != n then
              fail(lineNumber, s"expected $n integers in matrix row, got ${row.size}")
            costs ++= row
            rowsRead += 1

    if n < 0 then fail(lastLine, "missing 'n <size>' line")
    if rowsRead != n then fail(lastLine, s"expected $n matrix rows, got $rowsRead")
    Problem(n, objective.getOrElse(Objective.Minimize), costs.result())

  def format(problem: Problem): String =
    val sb = StringBuilder()
    sb.append("objective ")
      .append(if problem.objective == Objective.Maximize then "max" else "min")
      .append('\n')
    sb.append("n ").append(problem.n).append('\n')
    for i <- 0 until problem.n do
      sb.append(problem.costs.slice(i * problem.n, (i + 1) * problem.n).mkString(" ")).append('\n')
    sb.result()
