# Auction Algorithm Scala

A Scala 3 implementation of [Bertsekas' auction algorithm](http://dspace.mit.edu/bitstream/handle/1721.1/3233/P-2064-24690022.pdf?sequence=1) for the n-by-n assignment problem: optimally assigning n objects to n people given a cost matrix. Sibling of [AuctionAlgorithmCPP](https://github.com/EvanOman/AuctionAlgorithmCPP) — same algorithm, same problem-file format, interchangeable CLIs.

[![CI](https://github.com/EvanOman/AuctionAlgorithmScala/actions/workflows/ci.yml/badge.svg)](https://github.com/EvanOman/AuctionAlgorithmScala/actions/workflows/ci.yml)

Originally written in 2016 (Scala 2.11 + Breeze); rewritten in 2026. The old implementation had the same epsilon-scaling termination bug as the original C++ version (the final scaling phase ran too coarse, so tie-heavy instances came back suboptimal — its own test harness literally printed "Percentage correct"), and its fixed starting epsilon of 0.3 meant it produced **no assignment at all** for n ≤ 2. Full bug taxonomy in the [C++ repo's docs/BUGS.md](https://github.com/EvanOman/AuctionAlgorithmCPP/blob/master/docs/BUGS.md); the old source is preserved at `legacy/`.

## What's here

- **`Auction.solve(costs, n, options)`** — the solver. Row-major `IndexedSeq[Long]` costs, `Minimize` (default) or `Maximize`, exact optimality for integer costs (the final scaling phase runs with n·ε < 1). An optional **parallel bidding mode** (`Options(parallel = true)`) computes each round's bids concurrently with deterministic results — the feature that distinguished the 2016 version, kept and made correct.
- **`Problem`** — parser/formatter for the text problem format shared with the C++ repo.
- **CLI** — `solve` / `generate` subcommands mirroring the C++ `auction` binary.
- **Tests** — munit suite: brute-force cross-checks over hundreds of random instances, Machol–Wien closed-form cases, the tie-heavy regression instance, n ≤ 2 regressions, parallel-equals-sequential.

## Usage

Requires JDK 11+ and [sbt](https://www.scala-sbt.org/) ([just](https://github.com/casey/just) optional but recommended).

```bash
just test                       # run the test suite
just solve problem.apf          # solve a problem file
just generate 10 --seed 42      # emit a random 10x10 instance
```

Problem file format (`#` comments and blank lines ignored; `objective` defaults to `min`):

```
objective min
n 3
4 1 3
2 0 5
3 2 2
```

Output:

```
objective: min
n: 3
total_cost: 5
phases: 3
rounds: 7
assignment:
  0 -> 1
  1 -> 0
  2 -> 2
```

Add `--json` for machine-readable output, `--parallel` for parallel bidding.

As a library:

```scala
import com.evan.auctionalgorithm.*

val costs = Vector[Long](4, 1, 3, 2, 0, 5, 3, 2, 2)
val result = Auction.solve(costs, 3) // minimize by default
// result.assignment == Vector(1, 0, 2), result.totalCost == 5
```

## Development

`just fc` before committing (format + tests). CI runs `scalafmtCheckAll` and the test suite on every push. Compiler warnings are fatal (`-Werror`).
