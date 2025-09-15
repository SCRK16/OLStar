package com.example;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.OutputLstar.OutputMapChoice;

import de.learnlib.acex.AcexAnalyzers;
import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.oracle.membership.MealySimulatorOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.equivalence.MealyRandomWpMethodEQOracle;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithm.lstar.closing.ClosingStrategies;
import de.learnlib.algorithm.lstar.mealy.ClassicLStarMealy;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.util.mealy.MealyUtil;

public class Main {

    /**
     * Wrapper for ClassicLStarMealy so it has the same type as the other learning
     * algorithms
     * 
     * @param <I>           Input alphabet type
     * @param <O>           Output alphabet type
     * @param inputAlphabet The input alphabet of the target
     * @param mqOracle      Oracle for membership queries
     * @return
     */
    public static <I, O> MealyLearner<I, O> wrappedClassicLstarMealy(Alphabet<I> inputAlphabet,
            MembershipOracle<I, Word<O>> mqOracle) {
        return MealyUtil.wrapSymbolLearner(
                new ClassicLStarMealy<I, O>(inputAlphabet, MealyUtil.wrapWordOracle(mqOracle),
                        ObservationTableCEXHandlers.SUFFIX1BY1, ClosingStrategies.CLOSE_FIRST));
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
     * @param ceLogWriter   Writer for recording counterexamples
     * @return The number of rounds needed to learn the Mealy machine
     */
    public static <I, O> int learnLoop(MealyLearner<I, O> learner, Alphabet<I> inputAlphabet,
            EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> eqOracle, MealyMachine<?, I, ?, O> target,
            BufferedWriter ceLogWriter) throws IOException {
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

            // Find counterexample.
            DefaultQuery<I, Word<O>> ce = eqOracle.findCounterExample(hypothesis, inputAlphabet);
            if (ce == null) {
                break;
            }
            if (ceLogWriter != null) {
                System.out.println(ce.toString());
                ceLogWriter.append(ce.toString() + "\n");
            }
            if (ce != null)
                System.out.println(
                        "Counterexample: " + ce.toString() + ", hypothesis: "
                                + hypothesis.computeOutput(ce.getInput()));
            learner.refineHypothesis(ce);
        }
        return stage;
    }

    /**
     * Learns the target using the specified algorithm
     *
     * @param <I>               The input alphabet type of the target
     * @param <O>               The output alphabet type of the target
     * @param target            The target to be learned
     * @param algorithm         The name of the algorithm to be used
     * @param visualize         Set to true to visualize the results (works poorly
     *                          when target has many states)
     * @param resultFile        The file to store the results in, set to null if
     *                          results should not be stored
     * @param name              The name of the file to store the results in
     * @param outputMapSupplier The supplier of the output map for OL*. If null, use
     *                          the single output map. Unused for other learning
     *                          algorithms
     * @throws IOException
     */
    public static <I, O> void learn(MealyMachine<?, I, ?, O> target, Alphabet<I> inputAlphabet, String algorithm,
            boolean visualize, File resultFile,
            String name, Supplier<List<Map<O, Integer>>> outputMapSupplier,
            SampleSetEQOracle<MealyMachine<?, I, ?, O>, I, Word<O>> predeterminedEqOaracle, BufferedWriter ceLogWriter)
            throws IOException {
        MealySimulatorOracle<I, O> mOracle = new MealySimulatorOracle<>(target);
        MealyCounterOracle<I, O> mOracleForLearning = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> mCacheOracle = MealyCaches.createTreeCache(inputAlphabet, mOracleForLearning);
        MealyCounterOracle<I, O> mOracleForTesting = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> testingCacheOracle = MealyCaches.createTreeCache(inputAlphabet, mOracleForTesting);
        EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> eqOracle;
        if (predeterminedEqOaracle == null) {
            System.out.println("Using random Wp Method");
            eqOracle = new EarlyBreakEQOracle<>(target, new MealyRandomWpMethodEQOracle<>(testingCacheOracle, 2, 10));
        } else {
            System.out.println("Using predefined counterexamples");
            eqOracle = new EarlyBreakEQOracle<>(target, predeterminedEqOaracle);
        }

        MealyLearner<I, O> learner;
        if (algorithm.equals("Bitwise")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, true, false, OutputMapChoice.Bitwise, null);
        } else if (algorithm.equals("Lstar") || algorithm.equals("L*") || algorithm.equals("identity")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, false, true, OutputMapChoice.Lstar, null);
        } else if (algorithm.equals("Optimal")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, true, false, OutputMapChoice.Optimal, null);
        } else if (algorithm.equals("Precomputed")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, true, false, OutputMapChoice.Precomputed,
                    outputMapSupplier);
        } else if (algorithm.equals("ILstar") || algorithm.equals("IL*")) {
            Function<Alphabet<I>, MealyLearner<I, O>> learnerSupplier = alphabet -> wrappedClassicLstarMealy(alphabet,
                    mCacheOracle);
            learner = new InputDecomposer<I, O>(inputAlphabet, learnerSupplier, mCacheOracle, eqOracle);
        } else if (algorithm.equals("Generic")) {
            int components = Integer.parseInt(name.split("-")[1].substring(0, 1));
            Function<MembershipOracle<I, Word<Integer>>, MealyLearner<I, Integer>> learnerSupplier = integerOracle -> new TTTLearnerMealy<I, Integer>(
                    inputAlphabet, integerOracle, AcexAnalyzers.LINEAR_FWD);
            learner = new GenericDecomposedLearner<I, O>(inputAlphabet, mCacheOracle, learnerSupplier, components);
        } else if (algorithm.equals("TTT")) {
            learner = new TTTLearnerMealy<I, O>(inputAlphabet, mCacheOracle, AcexAnalyzers.LINEAR_FWD);
        } else {
            throw new UnsupportedOperationException(
                    "Valid decompositions: Lstar, Bitwise, Optimal, Precomputed, ILstar");
        }

        int stage = learnLoop(learner, inputAlphabet, eqOracle, target, ceLogWriter);
        System.out.println("Done!");
        if (learner instanceof OutputLstar) {
            System.out.println("Recomputing maps");
            OutputLstar<I, O> outputLearner = (OutputLstar<I, O>) learner;
            outputLearner.recomputeOptimal = true;
            List<Map<O, Integer>> outputMaps = outputLearner.outputMapSupplier.get();
            System.out.println(outputMaps.toString());
        }
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

        if (resultFile != null) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile, true));
            writer.append("Model learned: ");
            writer.append(name);
            writer.append("\nNumber of stages: ");
            writer.append(String.valueOf(stage));
            writer.append("\nNumber of states found: ");
            writer.append(String.valueOf(learner.getHypothesisModel().size()));
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

    private static void walk(String algorithm, Path modelPath, boolean visualize,
            File results, int repetitions,
            SampleSetEQOracle<MealyMachine<?, String, ?, String>, String, Word<String>> predeterminedEqOaracle,
            BufferedWriter ceLogWriter) throws IOException {
        Stream<Path> paths = Files.walk(modelPath);
        for (Path path : paths.filter(Files::isRegularFile).toList()) {
            System.out.println(path.toString());
            if (path.toString().compareTo("D:\\Code\\OLStar\\models\\random-2-30-4.dot") < 0) {
                continue;
            }
            CompactMealy<String, String> target = DOTParsers.mealy().readModel(path.toFile()).model;
            String decomposition = "decompositions\\" + path.getFileName().toString().replace(".dot", ".txt");
            Supplier<List<Map<String, Integer>>> outputMapSupplier = OutputMapSuppliers.from(decomposition);
            for (int i = 0; i < repetitions; i++)
                learn(target, target.getInputAlphabet(), algorithm, visualize, results, path.toString(),
                        outputMapSupplier, predeterminedEqOaracle, ceLogWriter);
        }
        paths.close();
        paths = Files.walk(modelPath);
        for (Path path : paths.filter(Files::isDirectory).collect(Collectors.toList())) {
            walk(algorithm, path, visualize, results, repetitions, predeterminedEqOaracle, ceLogWriter);
        }
        paths.close();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println(
                    "Usage: ./Main <algorithm> <model-file> <result-file> <repetitions> <visualize> <ce-input-file> <ce-output-file>");
            System.exit(1);
        }
        File results = null;
        if (args.length >= 3) {
            results = new File(args[2]);
        }
        int repetitions = 1;
        if (args.length >= 4) {
            repetitions = Integer.parseInt(args[3]);
        }
        boolean visualize = false;
        if (args.length >= 5) {
            visualize = args[4].equals("true");
        }
        SampleSetEQOracle<MealyMachine<?, String, ?, String>, String, Word<String>> eqOracle = null;
        if (args.length >= 6) {
            if (args[5].contains("\\")) {
                File predeterminedEQFile = new File(args[5]);
                List<DefaultQuery<String, Word<String>>> queries = Examples.parseEQs(predeterminedEQFile);
                for (DefaultQuery<String, Word<String>> query : queries) {
                    System.out.println(query);
                }
                eqOracle = new SampleSetEQOracle<>(false);
                eqOracle.addAll(queries);
            }
        }
        BufferedWriter ceLogWriter = null;
        if (args.length >= 7) {
            ceLogWriter = new BufferedWriter(new FileWriter(new File(args[6]), true));
        }
        if (args[1].equals("toy")) {
            MealyMachine<?, Character, ?, Pair<Object, Object>> target = Examples.constructExampleSUL(); // Examples.constructSUL(3);
            learn(target, Alphabets.fromArray('a', 'b', 'c', 'd'), args[0], visualize, results, "toy", null, null,
                    ceLogWriter);
        } else {
            Path modelPath = Paths.get(args[1]);
            walk(args[0], modelPath, visualize, results, repetitions, eqOracle, ceLogWriter);
        }
        if (ceLogWriter != null) {
            ceLogWriter.append("\n");
            ceLogWriter.close();
        }
    }
}
