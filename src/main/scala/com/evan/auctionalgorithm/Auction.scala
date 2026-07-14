package com.evan.auctionalgorithm

import scala.collection.parallel.CollectionConverters.*

/** Which way to optimize the total cost of the assignment. */
enum Objective:
  case Minimize, Maximize

/** @param objective
  *   minimize (default) or maximize total cost
  * @param epsilonInit
  *   starting value for the epsilon-scaling schedule; <= 0 selects automatically (half the largest
  *   absolute cost, at least 1)
  * @param scalingFactor
  *   factor epsilon is divided by between scaling phases; must be > 1
  * @param parallel
  *   compute each round's bids in parallel across unassigned persons (results are identical to the
  *   sequential path; bid aggregation stays sequential and deterministic)
  */
final case class Options(
    objective: Objective = Objective.Minimize,
    epsilonInit: Double = 0.0,
    scalingFactor: Double = 4.0,
    parallel: Boolean = false
)

/** @param assignment
  *   assignment(i) = object assigned to person i (a permutation of 0 until n)
  * @param totalCost
  *   total cost of the assignment in the caller's original cost terms
  * @param prices
  *   final auction prices per object (diagnostic; dual variables)
  * @param phases
  *   number of epsilon-scaling phases run
  * @param rounds
  *   total bidding rounds across all phases
  */
final case class Result(
    assignment: Vector[Int],
    totalCost: Long,
    prices: Vector[Double],
    phases: Long,
    rounds: Long
)

/** Bertsekas' auction algorithm for the n-by-n assignment problem.
  *
  * `costs` is row-major: costs(i * n + j) is the cost of assigning object j to person i. Costs must
  * satisfy |c| <= 2^30 so that epsilon-scaled arithmetic stays well clear of double-precision
  * rounding.
  *
  * The returned assignment is exactly optimal (not just epsilon-optimal): the final scaling phase
  * runs with n * epsilon < 1, which for integer costs guarantees optimality.
  */
object Auction:

  val MaxAbsCost: Long = 1L << 30

  private val Unassigned = -1

  def solve(costs: IndexedSeq[Long], n: Int, options: Options = Options()): Result =
    require(n >= 1, "n must be >= 1")
    require(costs.size == n.toLong * n, s"costs.size must equal n * n (${n.toLong * n})")
    require(options.scalingFactor > 1.0, "scalingFactor must be > 1")
    require(options.epsilonInit.isFinite, "epsilonInit must be finite")
    val maxAbs = costs.foldLeft(0L)((acc, c) => math.max(acc, math.abs(c)))
    require(maxAbs <= MaxAbsCost, "|cost| must be <= 2^30")

    // The auction maximizes total benefit; minimization negates the costs.
    val maximize = options.objective == Objective.Maximize
    val benefits = new Array[Double](n * n)
    var k = 0
    while k < benefits.length do
      benefits(k) = if maximize then costs(k).toDouble else -costs(k).toDouble
      k += 1

    val prices = new Array[Double](n)
    val assignment = Array.fill(n)(Unassigned)

    // Epsilon scaling. Each phase reuses the prices of the previous one. For
    // integer costs the assignment is exactly optimal once a full phase has
    // run with n * epsilon < 1, so the loop breaks only after a phase at
    // epsilon < 1/n has completed. (The 2016 implementation exited before
    // running that final phase — and never ran any phase at all for n <= 2,
    // since its epsilon started at a fixed 0.3.)
    var epsilon =
      if options.epsilonInit > 0 then options.epsilonInit
      else math.max(1.0, maxAbs.toDouble / 2.0)
    val threshold = 1.0 / n
    var phases = 0L
    var rounds = 0L
    var done = false
    while !done do
      rounds += runPhase(benefits, n, epsilon, prices, assignment, options.parallel)
      phases += 1
      if epsilon < threshold then done = true
      else epsilon /= options.scalingFactor

    var total = 0L
    var i = 0
    while i < n do
      total += costs(i * n + assignment(i))
      i += 1

    Result(assignment.toVector, total, prices.toVector, phases, rounds)

  /** One epsilon phase of the Jacobi auction: starting from an empty assignment (but keeping the
    * prices learned by earlier phases), rounds of simultaneous bidding run until every person is
    * assigned. Returns the number of rounds.
    */
  private def runPhase(
      benefits: Array[Double],
      n: Int,
      epsilon: Double,
      prices: Array[Double],
      assignment: Array[Int],
      parallel: Boolean
  ): Long =
    java.util.Arrays.fill(assignment, Unassigned)
    val owner = Array.fill(n)(Unassigned)
    var unassigned = Vector.tabulate(n)(identity)

    val bestBid = new Array[Double](n)
    val bestBidder = Array.fill(n)(Unassigned)

    var rounds = 0L
    while unassigned.nonEmpty do
      rounds += 1
      java.util.Arrays.fill(bestBidder, Unassigned)

      // Bidding: every unassigned person bids on their most valuable object,
      // offering the best/second-best value gap plus epsilon. The parallel
      // path computes bids concurrently; aggregation below is sequential and
      // in person order either way, so results are deterministic.
      val bids =
        if parallel then unassigned.par.map(i => computeBid(benefits, n, prices, epsilon, i)).seq
        else unassigned.map(i => computeBid(benefits, n, prices, epsilon, i))

      for (i, bestJ, increment) <- bids do
        if bestBidder(bestJ) == Unassigned || increment > bestBid(bestJ) then
          bestBidder(bestJ) = i
          bestBid(bestJ) = increment

      // Awarding: each object that received bids goes to its highest bidder;
      // the displaced previous owner rejoins the unassigned pool.
      val displaced = Vector.newBuilder[Int]
      var j = 0
      while j < n do
        val winner = bestBidder(j)
        if winner != Unassigned then
          prices(j) += bestBid(j)
          val prev = owner(j)
          if prev != Unassigned then
            assignment(prev) = Unassigned
            displaced += prev
          owner(j) = winner
          assignment(winner) = j
        j += 1

      // Next round's bidders: this round's losers plus the displaced.
      unassigned = unassigned.filter(assignment(_) == Unassigned) ++ displaced.result()
    rounds

  /** The best object for person i and the increment to bid on it. */
  private def computeBid(
      benefits: Array[Double],
      n: Int,
      prices: Array[Double],
      epsilon: Double,
      i: Int
  ): (Int, Int, Double) =
    var best = Double.NegativeInfinity
    var second = Double.NegativeInfinity
    var bestJ = 0
    val base = i * n
    var j = 0
    while j < n do
      val value = benefits(base + j) - prices(j)
      if value > best then
        second = best
        best = value
        bestJ = j
      else if value > second then second = value
      j += 1
    // With a single object there is no second-best; any positive increment is
    // valid since there is no competition to outbid.
    val increment =
      if second == Double.NegativeInfinity then 1.0 + epsilon else best - second + epsilon
    (i, bestJ, increment)
