#!/bin/bash
set -eu
# this is a script for generating tests for the CUTs
# in nl.uu.maze.benchmarks
budget=30
strategy=BFS
depth=400
#depth=200
MAZE=./target/maze-1.2.0-jar-with-dependencies.jar
BM=(AckermannPeter
    BinarySearch
    BinaryTree
    BitwiseManipulator
    BracketBalancer
    ConnectedComponents
    ConvergingPaths
    Dijkstra
    ExprEvaluator
    FloatStatistics
    GraphTraversal
    HeapSort
    IntUtils
    MatrixAnalyzer
    NestedLoops
    QuickSort
    SinglyLinkedList
    StringPatternMatcher
    StringUtils
    TriangleClassifier)
#BM=(AckermannPeter)

for CUT in "${BM[@]}"; do
   echo "=== Generating tests for ${CUT}"
   java -Xmx2500m -jar "${MAZE}" --classpath=./target/classes --class-name=nl.uu.maze.benchmarks.${CUT} --minimization=true -s=${strategy} -b=${budget} --constrain-fp-params-to-normal-numbers=true --check-division-by-zero=true --max-array-size=10 --max-depth=${depth} --path-length-coverage=1 --output-path=./tmp/
   echo 'package nl.uu.tests.maze.generated.benchmarks;' | cat - ./tmp/${CUT}Test.java > ./tmp/haha && mv ./tmp/haha ./tmp/${CUT}Test.java
done
