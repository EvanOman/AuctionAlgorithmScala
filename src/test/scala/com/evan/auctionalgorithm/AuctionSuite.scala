package com.evan.auctionalgorithm

class AuctionSuite extends munit.FunSuite:

  private def maxOpts = Options(objective = Objective.Maximize)

  private def isPermutation(assignment: Vector[Int], n: Int): Boolean =
    assignment.size == n && assignment.sorted == Vector.tabulate(n)(identity)

  /** Exhaustive reference solver for small n. */
  private def bruteForce(costs: IndexedSeq[Long], n: Int, objective: Objective): Long =
    val totals = (0 until n).permutations.map { perm =>
      perm.zipWithIndex.foldLeft(0L) { case (acc, (j, i)) => acc + costs(i * n + j) }
    }
    if objective == Objective.Maximize then totals.max else totals.min

  /** Deterministic PRNG so tests are reproducible across runs and platforms. */
  private final class Lcg(seed: Long):
    private var state = seed
    def next(lo: Long, hi: Long): Long =
      state = state * 6364136223846793005L + 1442695040888963407L
      lo + java.lang.Long.remainderUnsigned(state >>> 33, hi - lo + 1)

  test("n=1 assigns the only person to the only object") {
    // Regression: the 2016 implementation started epsilon at a fixed 0.3, so
    // for n <= 2 the scaling loop never ran and no assignment was produced.
    val result = Auction.solve(Vector(7L), 1)
    assertEquals(result.assignment, Vector(0))
    assertEquals(result.totalCost, 7L)
  }

  test("n=2 solves (regression: 2016 loop never ran for n <= 2)") {
    val result = Auction.solve(Vector(5L, 1L, 2L, 7L), 2)
    assertEquals(result.totalCost, 3L)
    assert(isPermutation(result.assignment, 2))
  }

  test("regression: tie-heavy 3x3 the C++ original solved suboptimally") {
    // Same instance pinned in the C++ sibling repo: maximizing, the buggy
    // epsilon-scaling returned total 6; the optimum is 7.
    val costs = Vector[Long](1, 3, 3, 2, 3, 1, 1, 1, 2)
    val result = Auction.solve(costs, 3, maxOpts)
    assertEquals(result.totalCost, 7L)
  }

  test("known 3x3 minimization") {
    val costs = Vector[Long](4, 1, 3, 2, 0, 5, 3, 2, 2)
    val result = Auction.solve(costs, 3)
    assertEquals(result.totalCost, 5L)
    assert(isPermutation(result.assignment, 3))
  }

  test("Machol-Wien instances: cost(i)(j) = (i+1)*(j+1)") {
    for n <- Seq(2, 3, 5, 8, 16) do
      val costs = Vector.tabulate(n * n)(k => ((k / n + 1).toLong * (k % n + 1)))
      val minExpected = (0 until n).map(i => (i + 1).toLong * (n - i)).sum
      val maxExpected = (0 until n).map(i => (i + 1).toLong * (i + 1)).sum
      assertEquals(Auction.solve(costs, n).totalCost, minExpected, s"min n=$n")
      assertEquals(Auction.solve(costs, n, maxOpts).totalCost, maxExpected, s"max n=$n")
  }

  test("all-equal costs: any permutation is optimal") {
    val n = 6
    val result = Auction.solve(Vector.fill(n * n)(5L), n)
    assert(isPermutation(result.assignment, n))
    assertEquals(result.totalCost, 30L)
  }

  test("negative costs") {
    val costs = Vector[Long](-5, -1, -2, -7)
    assertEquals(Auction.solve(costs, 2).totalCost, -12L)
    assertEquals(Auction.solve(costs, 2, maxOpts).totalCost, -3L)
  }

  test("maximize on C equals negated minimize on -C") {
    val rng = Lcg(99)
    val n = 7
    val costs = Vector.fill(n * n)(rng.next(-50, 50))
    val negated = costs.map(-_)
    assertEquals(Auction.solve(costs, n, maxOpts).totalCost, -Auction.solve(negated, n).totalCost)
  }

  test("matches brute force on random instances (sequential and parallel)") {
    val rng = Lcg(2016)
    for
      n <- Seq(2, 3, 4, 5, 6, 7)
      trial <- 0 until 25
    do
      // Narrow ranges force ties, the regime that exposed the epsilon bug.
      val hi = if trial % 2 == 0 then n.toLong else 100L
      val costs = Vector.fill(n * n)(rng.next(1, hi))
      for objective <- Seq(Objective.Minimize, Objective.Maximize) do
        val expected = bruteForce(costs, n, objective)
        val seq = Auction.solve(costs, n, Options(objective = objective))
        val par = Auction.solve(costs, n, Options(objective = objective, parallel = true))
        assert(isPermutation(seq.assignment, n))
        assertEquals(seq.totalCost, expected, s"seq n=$n trial=$trial $objective")
        assertEquals(par.totalCost, expected, s"par n=$n trial=$trial $objective")
  }

  test("parallel path is deterministic and equals sequential") {
    val rng = Lcg(7)
    val n = 40
    val costs = Vector.fill(n * n)(rng.next(1, 30))
    val first = Auction.solve(costs, n, Options(parallel = true))
    val second = Auction.solve(costs, n, Options(parallel = true))
    val seq = Auction.solve(costs, n)
    assertEquals(first.assignment, second.assignment)
    assertEquals(first.totalCost, seq.totalCost)
  }

  test("custom epsilon options still reach the exact optimum") {
    val costs = Vector[Long](1, 3, 3, 2, 3, 1, 1, 1, 2)
    val result =
      Auction.solve(costs, 3, maxOpts.copy(epsilonInit = 40.0, scalingFactor = 2.0))
    assertEquals(result.totalCost, 7L)
  }

  test("input validation") {
    intercept[IllegalArgumentException](Auction.solve(Vector.empty[Long], 0))
    intercept[IllegalArgumentException](Auction.solve(Vector(1L, 2L, 3L), 2))
    intercept[IllegalArgumentException](
      Auction.solve(Vector(1L, 2L, 3L, 4L), 2, Options(scalingFactor = 1.0))
    )
    intercept[IllegalArgumentException](Auction.solve(Vector((1L << 30) + 1), 1))
  }
