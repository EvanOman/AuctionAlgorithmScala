# Auction Algorithm Scala

This is a repo contains a `Scala` implementation of [Bertsekas's Auction Algorithm](http://dspace.mit.edu/bitstream/handle/1721.1/3233/P-2064-24690022.pdf?sequence=1). The algorithm solves the problem of optimally assigning M objects to N people given the preferences specified in a given cost matrix.

After a bit of optimization, this implementation can solve a size 500 assignment problem in ~.125 seconds. I have also implemented a parallel version, which seems to outperform the sequential version once the problem size is over 5000. This implementation is still being optimized. 