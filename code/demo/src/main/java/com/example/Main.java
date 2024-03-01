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
//import java.nio.file.Paths;
import java.util.ArrayList;
//import java.util.List;

import de.learnlib.acex.AcexAnalyzers;
import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.oracle.membership.MealySimulatorOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.oracle.equivalence.MealyWpMethodEQOracle;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithm.lstar.closing.ClosingStrategies;
import de.learnlib.algorithm.lstar.mealy.ClassicLStarMealy;
import de.learnlib.util.mealy.MealyUtil;

public class Main {

    public static CompactMealy<Character, Object> constructSUL() {
        Alphabet<Character> alphabet = Alphabets.fromArray('a', 'b');
        return AutomatonBuilders.newMealy(alphabet).withInitial("q0")
                .from("q0")
                .on('a').withOutput('1').to("q1")
                .on('b').withOutput('0').to("r0")
                .from("q1")
                .on('a').withOutput('2').to("q2")
                .on('b').withOutput('1').to("r1")
                .from("q2")
                .on('a').withOutput('0').to("q0")
                .on('b').withOutput('2').to("r2")
                .from("r0")
                .on('a').withOutput('2').to("r2")
                .on('b').withOutput('0').to("q0")
                .from("r1")
                .on('a').withOutput('0').to("r0")
                .on('b').withOutput('1').to("q1")
                .from("r2")
                .on('a').withOutput('1').to("r1")
                .on('b').withOutput('2').to("q2")
                .create();
    }

    public static Alphabet<Object> SULOutputAlphabet() {
        return Alphabets.fromArray('0', '1', '2');
    }

    public static <I> void learnComponents(ArrayList<MealyLearner<I, Boolean>> components, Alphabet<I> inputAlphabet) {
        int componentCount = 0;
        for (MealyLearner<I, Boolean> component : components) {
            System.out.println("\nLearning component: " + String.valueOf(++componentCount));
            MealyMachine<?, I, ?, Boolean> target = component.getHypothesisModel();
            MealySimulatorOracle<I, Boolean> componentOracle = new MealySimulatorOracle<>(target);
            MealyCounterOracle<I, Boolean> componentCounterOracle = new MealyCounterOracle<>(componentOracle);
            MealyCounterOracle<I, Boolean> testingCounterOracle = new MealyCounterOracle<>(componentOracle);
            MealyWpMethodEQOracle<I, Boolean> testingEqOracle = new MealyWpMethodEQOracle<>(testingCounterOracle, 3);
            TTTLearnerMealy<I, Boolean> componentTTT = new TTTLearnerMealy<>(inputAlphabet, componentCounterOracle,
                    AcexAnalyzers.LINEAR_FWD);
            int stage = learnLoop(componentTTT, inputAlphabet, testingEqOracle, false, target);
            System.out.println("Learning: " + componentCounterOracle.getStatisticalData().getSummary());
            // System.out.println("Testing: " +
            // testingCounterOracle.getStatisticalData().getSummary());
            System.out.println("Rounds: " + stage);
        }
    }

    public static <I, O> int learnLoop(MealyLearner<I, O> learner, Alphabet<I> inputAlphabet,
            MealyEquivalenceOracle<I, O> eqOracle, boolean lastSymbol, MealyMachine<?, I, ?, O> target) {
        int stage = 0;
        learner.startLearning();
        while (true) {
            stage++;
            System.out.println("Starting stage: " + stage);
            MealyMachine<?, I, ?, O> hypothesis = learner.getHypothesisModel();

            System.out.println("Number of states at stage " + stage + ": " + hypothesis.size());

            // Quick check to avoid expensive final EQ.
            // We do not use this to find actual counterexamples.
            if (target != null) {
                Word<I> sep = Automata.findSeparatingWord(target, hypothesis, inputAlphabet);
                if (sep == null)
                    break;
            }

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

    public static <I, O> void learn(CompactMealy<I, O> target, String algorithm, boolean visualize, File file,
            String name) throws IOException {
        Alphabet<I> inputAlphabet = target.getInputAlphabet();
        MealySimulatorOracle<I, O> mOracle = new MealySimulatorOracle<>(target);
        MealyCounterOracle<I, O> mOracleForLearning = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> mCacheOracle = MealyCaches.createCache(inputAlphabet, mOracleForLearning);
        MealyCounterOracle<I, O> mOracleForTesting = new MealyCounterOracle<>(mOracle);
        MealyCacheOracle<I, O> testingCacheOracle = MealyCaches.createCache(inputAlphabet, mOracleForTesting);
        MealyWpMethodEQOracle<I, O> eqOracle = new MealyWpMethodEQOracle<>(testingCacheOracle, 3);
        MealyLearner<I, O> learner;
        if (algorithm.equals("Decompose")) {
            learner = DynamicMealyDecomposer.createDynamicMealyDecomposerWithCache(inputAlphabet, mOracleForLearning,
                    AcexAnalyzers.LINEAR_FWD);
        } else if (algorithm.equals("TTT")) {
            learner = new TTTLearnerMealy<>(inputAlphabet, mCacheOracle, AcexAnalyzers.LINEAR_FWD);
        } else if (algorithm.equals("OLstar") || algorithm.equals("OL*")) {
            learner = new OutputLstar<I, O>(inputAlphabet, mCacheOracle, true);
        } else if (algorithm.equals("Lstar") || algorithm.equals("L*")) {
            learner = MealyUtil.wrapSymbolLearner(
                    new ClassicLStarMealy<I, O>(inputAlphabet, MealyUtil.wrapWordOracle(mCacheOracle),
                            ObservationTableCEXHandlers.SUFFIX1BY1, ClosingStrategies.CLOSE_FIRST));
        } else {
            throw new UnsupportedOperationException("Valid algorithms: Decompose / TTT / Lstar");
        }

        int stage = learnLoop(learner, inputAlphabet, eqOracle, false, target);
        System.out.println("Done!");
        System.out.println("Learning: " + mOracleForLearning.getStatisticalData().getSummary());
        System.out.println("Testing: " + mOracleForTesting.getStatisticalData().getSummary());
        System.out.println("Rounds: " + stage);

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
            writer.append("\nComponent sizes: ");
            if (learner instanceof DynamicMealyDecomposer) {
                for (MealyLearner<I, Boolean> component : ((DynamicMealyDecomposer<I, O>) learner).learners) {
                    writer.append(String.valueOf(component.getHypothesisModel().size()));
                    writer.append(" - ");
                }
            }
            writer.append('\n');
            writer.append(mOracleForLearning.getStatisticalData().getSummary());
            writer.append("\n\n");
            writer.close();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            /*
             * System.err.println("Usage: ./Main toy <algorithm> OR ./Main _ <algorithm>");
             * System.exit(1);
             */
            args = new String[] { "_", "OL*" };
        }
        if (args[0].equals("toy")) {
            CompactMealy<Character, Object> target = constructSUL();
            learn(target, args[1], false, null, null);
        } else {
            if (args[0].equals("_")) {
                args[0] = "m41";
            }
            CompactMealy<String, String> target = DOTParsers
                    .mealy()
                    .readModel(new File(
                            "D:\\Models\\models\\benchmarks\\Mealy\\principle\\BenchmarkASMLRERS2019\\" +
                                    args[0]
                                    + ".dot")).model;

            /*
             * if (args[0].equals("_")) {
             * args[0] = "bbsse_minimized";
             * }
             * CircuitParser parser = new CircuitParser(Paths
             * .get("D:\\Models\\models\\benchmarks\\Mealy\\principle\\BenchmarkCircuits\\" + args[0] + "
             * .dot"));
             * CompactMealy<String, List<String>> target = parser.createMachine();
             */
            learn(target, args[1], false, null, null);
        }
    }
}
