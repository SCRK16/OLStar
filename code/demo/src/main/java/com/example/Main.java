package com.example;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.serialization.dot.DOTParsers;
import net.automatalib.visualization.Visualization;
import net.automatalib.word.Word;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import de.learnlib.acex.AcexAnalyzers;
import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.oracle.membership.MealySimulatorOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.oracle.equivalence.MealyRandomWpMethodEQOracle;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithm.lstar.closing.ClosingStrategies;
import de.learnlib.algorithm.lstar.mealy.ClassicLStarMealy;
import de.learnlib.util.mealy.MealyUtil;

public class Main {

    /**
     * Create the toy example.
     *
     * @param n The number of states of each component
     * @return The toy example
     */
    public static CompactMealy<Character, Object> constructSUL(int n) {
        Alphabet<Character> alphabet = Alphabets.fromArray('a', 'b');
        var result = AutomatonBuilders.newMealy(alphabet).withInitial(0);
        for (int i = 0; i < n; i++) {
            result.from(i)
                    .on('a').withOutput((i + 1) % n).to((i + 1) % n)
                    .on('b').withOutput(i).to(i + n);
            result.from(i + n)
                    .on('a').withOutput((i + n - 1) % n).to((i + n - 1) % n + n)
                    .on('b').withOutput(i).to(i);
        }
        return result.create();
    }

    /**
     * Create the output alphabet of the toy example. Can be used to provide an
     * algorithm with the output alphabet up front.
     *
     * @param n The number of states of each component in the toy example
     * @return The output alphabet of the toy example
     */
    public static Alphabet<Object> SULOutputAlphabet(int n) {
        ArrayList<Integer> outputs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            outputs.add(i);
        }
        return Alphabets.fromList(outputs);
    }

    /**
     * Create a machine which shows that an output-closed and output-consistent
     * observation table can still result in a component-inconsistent family of
     * Mealy machines.
     *
     * @return The machine
     */
    public static CompactMealy<Character, Object> constructComponentInconsistentSUL() {
        Alphabet<Character> alphabet = Alphabets.fromArray('a', 'b');
        return AutomatonBuilders.newMealy(alphabet).withInitial("qe")
                .from("qe")
                .on('a').withOutput('0').to("qa")
                .on('b').withOutput('1').to("qb")
                .from("qa")
                .on('a').withOutput('1').to("qaa")
                .on('b').withOutput('2').to("qaa")
                .from("qb")
                .on('a').withOutput('0').to("qaa")
                .on('b').withOutput('2').to("qaa")
                .from("qaa")
                .on('a').withOutput('2').to("qaa")
                .on('b').withOutput('0').to("qaa")
                .create();
    }

    /**
     * Main loop of active automata learning.
     *
     * @param <I>           Input alphabet type
     * @param <O>           Output alphabet type
     * @param learner       The learning algorithm
     * @param inputAlphabet The input alphabet of the target
     * @param eqOracle      the equivelance oracle
     * @param target        The target Mealy machine, used to check if an
     *                      equivalence query is necessary
     * @return The number of rounds needed to learn the Mealy machine
     */
    public static <I, O> int learnLoop(MealyLearner<I, O> learner, Alphabet<I> inputAlphabet,
            MealyEquivalenceOracle<I, O> eqOracle, MealyMachine<?, I, ?, O> target) {
        if (target == null) {
            throw new IllegalStateException("Target cannot be null");
        }
        int stage = 0;
        learner.startLearning();
        while (true) {
            stage++;
            System.out.println("Starting stage: " + stage);
            MealyMachine<?, I, ?, O> hypothesis = learner.getHypothesisModel();

            System.out.println("Number of states at stage " + stage + ": " + hypothesis.size());

            // Quick check to avoid expensive final EQ.
            // We do not use this to find actual counterexamples.
            Word<I> sep = Automata.findSeparatingWord(target, hypothesis, inputAlphabet);
            if (sep == null)
                break;

            // Find counterexample.
            DefaultQuery<I, Word<O>> ce = eqOracle.findCounterExample(hypothesis, inputAlphabet);
            if (ce == null)
                throw new IllegalStateException(
                        "Equivalence Oracle couldn't find counterexample, even though a separating word exists.");
            System.out.println(
                    "Counterexample: " + ce.toString() + ", hypothesis: " + hypothesis.computeOutput(ce.getInput()));
            learner.refineHypothesis(ce);
        }
        return stage;
    }

    /**
     * Learns the target using the specified algorithm
     *
     * @param <I>       The input alphabet type of the target
     * @param <O>       The output alphabet type of the target
     * @param target    The target to be learned
     * @param algorithm The name of the algorithm to be used
     * @param visualize Set to true to visualize the results (works poorly when
     *                  target has many states)
     * @param file      The file to store the results in, set to null if results
     *                  should not be stored
     * @param name      The name of the file to store the results in
     * @throws IOException
     */
    public static <I, O> void learn(CompactMealy<I, O> target, String algorithm, boolean visualize, File file,
            String name) throws IOException {
        Alphabet<I> inputAlphabet = target.getInputAlphabet();
        MealySimulatorOracle<I, O> mOracle = new MealySimulatorOracle<>(target);
        MealyCounterOracle<I, O> mOracleForLearning = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> mCacheOracle = MealyCaches.createTreeCache(inputAlphabet, mOracleForLearning);
        MealyCounterOracle<I, O> mOracleForTesting = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> testingCacheOracle = MealyCaches.createTreeCache(inputAlphabet, mOracleForTesting);
        MealyRandomWpMethodEQOracle<I, O> eqOracle = new MealyRandomWpMethodEQOracle<>(testingCacheOracle, 2, 10);
        MealyLearner<I, O> learner;
        if (algorithm.equals("Decompose")) {
            learner = DynamicMealyDecomposer.createDynamicMealyDecomposerWithCache(inputAlphabet, mOracleForLearning,
                    AcexAnalyzers.LINEAR_FWD);
        } else if (algorithm.equals("TTT")) {
            learner = new TTTLearnerMealy<>(inputAlphabet, mCacheOracle, AcexAnalyzers.LINEAR_FWD);
        } else if (algorithm.equals("OLstar") || algorithm.equals("OL*")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, true, false);
        } else if (algorithm.equals("Lstar") || algorithm.equals("L*")) {
            learner = MealyUtil.wrapSymbolLearner(
                    new ClassicLStarMealy<I, O>(inputAlphabet, MealyUtil.wrapWordOracle(mCacheOracle),
                            ObservationTableCEXHandlers.SUFFIX1BY1, ClosingStrategies.CLOSE_FIRST));
        } else {
            throw new UnsupportedOperationException("Valid algorithms: Decompose / TTT / OLstar / Lstar");
        }

        int stage = learnLoop(learner, inputAlphabet, eqOracle, target);
        System.out.println("Done!");
        System.out.println("Learning: " + mOracleForLearning.getStatisticalData().getSummary());
        System.out.println("Testing: " + mOracleForTesting.getStatisticalData().getSummary());
        System.out.println("Rounds: " + stage);
        if (learner instanceof OutputLstar) {
            OutputLstar<I, O> outputLearner = (OutputLstar<I, O>) learner;
            System.out.println("Inconsistent count: " + String.valueOf(outputLearner.inconsistentCount));
            System.out.println("Zero outputs count: " + String.valueOf(outputLearner.zeroOutputsCount));
            System.out.println("Two outputs count: " + String.valueOf(outputLearner.twoOutputsCount));
        }

        if (visualize) {
            Visualization.visualize(learner.getHypothesisModel(), inputAlphabet, true);
        }

        if (file != null) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            writer.append("Model learned: ");
            writer.append(name);
            writer.append("\nNumber of stages: ");
            writer.append(String.valueOf(stage));
            writer.append("\nNumber of states found: ");
            writer.append(String.valueOf(learner.getHypothesisModel().size()));
            if (learner instanceof DynamicMealyDecomposer) {
                writer.append("\nComponent sizes: ");
                for (MealyLearner<I, Boolean> component : ((DynamicMealyDecomposer<I, O>) learner).learners) {
                    writer.append(String.valueOf(component.getHypothesisModel().size()));
                    writer.append(" - ");
                }
            }
            if (learner instanceof OutputLstar) {
                OutputLstar<I, O> outputLearner = (OutputLstar<I, O>) learner;
                writer.append("\nNumber of short rows: "
                        + String.valueOf(outputLearner.getObservationTable().getShortPrefixRows().size()));
                writer.append("\nInconsistent count: " + String.valueOf(outputLearner.inconsistentCount));
                writer.append("\nZero outputs count: " + String.valueOf(outputLearner.zeroOutputsCount));
                writer.append("\nTwo outputs count: " + String.valueOf(outputLearner.twoOutputsCount));
            }
            writer.append("\nLearning: ");
            writer.append(mOracleForLearning.getStatisticalData().getSummary());
            writer.append("\nTesting: ");
            writer.append(mOracleForTesting.getStatisticalData().getSummary());
            writer.append("\n\n");
            writer.close();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            /*
             * System.err.println("Usage: ./Main toy <algorithm> OR ./Main _ <algorithm>" OR ./Main all <algorithm>);
             * System.exit(1);
             */
            args = new String[] { "_", "OL*" };
        }
        if (args[0].equals("toy")) {
            CompactMealy<Character, Object> target = constructSUL(3);
            learn(target, args[1], false, null, null);
        } else if (args[0].equals("all")) {
            File file = new File("results\\rerun.txt");
            try (Stream<Path> paths = Files.walk(Paths.get("models"))) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    CompactMealy<String, String> target = DOTParsers
                            .mealy()
                            .readModel(path.toFile()).model;
                    learn(target, args[1], false, file, path.toString());
                }
            }
        } else {
            if (args[0].equals("_")) {
                args[0] = "models\\random-2-5-1.dot";
            }
            CompactMealy<String, String> target = DOTParsers
                    .mealy()
                    .readModel(new File(args[0])).model;
            learn(target, args[1], false, null, null);
        }
    }
}
