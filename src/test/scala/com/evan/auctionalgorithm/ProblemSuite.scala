package com.evan.auctionalgorithm

class ProblemSuite extends munit.FunSuite:

  test("parses a minimal problem") {
    val p = Problem.parse("n 2\n1 2\n3 4\n")
    assertEquals(p.n, 2)
    assertEquals(p.objective, Objective.Minimize)
    assertEquals(p.costs, Vector[Long](1, 2, 3, 4))
  }

  test("parses objective, comments, blank lines, negative costs") {
    val p = Problem.parse(
      "# a comment\n\nobjective max\nn 2\n-1 2   # trailing comment\n  30 -4\n"
    )
    assertEquals(p.objective, Objective.Maximize)
    assertEquals(p.costs, Vector[Long](-1, 2, 30, -4))
  }

  test("format round-trips") {
    val p = Problem(2, Objective.Maximize, Vector(5L, -3L, 0L, 9L))
    val back = Problem.parse(Problem.format(p))
    assertEquals(back, p)
  }

  test("rejects malformed input") {
    val bad = Seq(
      "", // empty
      "1 2\n3 4\n", // no n
      "n 2\n1 2\n", // too few rows
      "n 2\n1 2\n3 4\n5 6\n", // too many rows
      "n 2\n1\n3 4\n", // short row
      "n 2\n1 2 9\n3 4\n", // long row
      "n 0\n", // bad n
      "n -3\n", // bad n
      "n two\n", // bad n
      "objective sideways\nn 1\n1\n", // bad objective
      "n 1\nobjective max\nobjective min\n1\n", // duplicate objective
      "n 1\n1 x\n" // trailing junk
    )
    for text <- bad do intercept[ProblemParseException](Problem.parse(text))
  }

  test("error messages carry line numbers") {
    val e = intercept[ProblemParseException](Problem.parse("n 2\n1 2\nbogus row\n"))
    assert(e.getMessage.contains("line 3"), e.getMessage)
  }

  test("solving a parsed problem end to end") {
    val text = "objective min\nn 3\n4 1 3\n2 0 5\n3 2 2\n"
    val p = Problem.parse(text)
    val result = Auction.solve(p.costs, p.n, Options(objective = p.objective))
    assertEquals(result.totalCost, 5L)
  }
