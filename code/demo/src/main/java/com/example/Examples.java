package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.CompactTransition;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.serialization.dot.DOTParsers;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.util.automaton.builder.MealyBuilder;
import net.automatalib.util.automaton.minimizer.hopcroft.HopcroftMinimization;
import net.automatalib.visualization.Visualization;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class Examples {
    /**
     * Create the toy example.
     *
     * @param n The number of states of each component
     * @return The toy example
     */
    public static CompactMealy<Character, Object> constructSUL(int n) {
        Alphabet<Character> alphabet = Alphabets.fromArray('a', 'b');
        MealyBuilder<Integer, Character, CompactTransition<Object>, Object, CompactMealy<Character, Object>> result = AutomatonBuilders
                .newMealy(alphabet);
        for (int i = 0; i < n; i++) {
            result.from(i)
                    .on('a').withOutput((i + 1) % n).to((i + 1) % n)
                    .on('b').withOutput(i).to(i + n);
            result.from(i + n)
                    .on('a').withOutput((i + n - 1) % n).to((i + n - 1) % n + n)
                    .on('b').withOutput(i).to(i);
        }
        return result.withInitial(0).create();
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
     * Mealy machines. Assumes bitwise decomposition.
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

    public static ProductMealy<Integer, Integer, Character, CompactTransition<Object>, CompactTransition<Object>, Object, Object> constructExampleSUL() {
        Alphabet<Character> alphabet = Alphabets.fromArray('a', 'b', 'c', 'd');
        CompactMealy<Character, Object> first = AutomatonBuilders.newMealy(alphabet).withInitial("q0")
                .from("q0")
                .on('a').withOutput('0').to("q0")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('0').to("q0")
                .on('d').withOutput('0').to("q1")
                .from("q1")
                .on('a').withOutput('0').to("q1")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('0').to("q0")
                .on('d').withOutput('0').to("q2")
                .from("q2")
                .on('a').withOutput('0').to("q2")
                .on('b').withOutput('0').to("q1")
                .on('c').withOutput('0').to("q0")
                .on('d').withOutput('0').to("q3")
                .from("q3")
                .on('a').withOutput('1').to("q3")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('0').to("q0")
                .on('d').withOutput('1').to("q3")
                .create();
        CompactMealy<Character, Object> second = AutomatonBuilders.newMealy(alphabet).withInitial("q0")
                .from("q0")
                .on('a').withOutput('0').to("q0")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('0').to("q1")
                .on('d').withOutput('0').to("q0")
                .from("q1")
                .on('a').withOutput('0').to("q1")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('0').to("q2")
                .on('d').withOutput('0').to("q0")
                .from("q2")
                .on('a').withOutput('0').to("q2")
                .on('b').withOutput('0').to("q1")
                .on('c').withOutput('0').to("q3")
                .on('d').withOutput('0').to("q0")
                .from("q3")
                .on('a').withOutput('1').to("q3")
                .on('b').withOutput('0').to("q0")
                .on('c').withOutput('1').to("q3")
                .on('d').withOutput('0').to("q0")
                .create();
        return new ProductMealy<Integer, Integer, Character, CompactTransition<Object>, CompactTransition<Object>, Object, Object>(
                alphabet, first, second);
    }

    public static class ProductMealy<S1, S2, I, T1, T2, O1, O2>
            implements MealyMachine<Pair<S1, S2>, I, Pair<T1, T2>, Pair<O1, O2>> {

        private final Alphabet<I> inputAlphabet;
        private final MealyMachine<S1, I, T1, O1> first;
        private final MealyMachine<S2, I, T2, O2> second;
        private Collection<Pair<S1, S2>> cachedStates;

        public ProductMealy(Alphabet<I> inputAlphabet, MealyMachine<S1, I, T1, O1> first,
                MealyMachine<S2, I, T2, O2> second) {
            this.inputAlphabet = inputAlphabet;
            this.first = first;
            this.second = second;
        }

        @Override
        public Pair<S1, S2> getSuccessor(Pair<T1, T2> transition) {
            return Pair.of(first.getSuccessor(transition.getFirst()), second.getSuccessor(transition.getSecond()));
        }

        @Override
        public Collection<Pair<S1, S2>> getStates() {
            if (cachedStates != null) {
                return cachedStates;
            }
            Set<Pair<S1, S2>> reach = new HashSet<>();
            Queue<Pair<S1, S2>> bfsQueue = new ArrayDeque<>();

            Pair<S1, S2> init = getInitialState();

            bfsQueue.add(init);

            Pair<S1, S2> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    Pair<S1, S2> succ = getSuccessor(curr, in);
                    if (succ == null)
                        continue;

                    if (!reach.contains(succ)) {
                        bfsQueue.add(succ);
                    }
                }
                reach.add(curr);
            }
            cachedStates = reach;
            return cachedStates;
        }

        @Override
        public @Nullable Pair<S1, S2> getInitialState() {
            return Pair.of(first.getInitialState(), second.getInitialState());
        }

        @Override
        public @Nullable Pair<T1, T2> getTransition(Pair<S1, S2> state, I input) {
            return Pair.of(first.getTransition(state.getFirst(), input),
                    second.getTransition(state.getSecond(), input));
        }

        @Override
        public Void getStateProperty(Pair<S1, S2> state) {
            return null;
        }

        @Override
        public Pair<O1, O2> getTransitionOutput(Pair<T1, T2> transition) {
            return Pair.of(first.getTransitionOutput(transition.getFirst()),
                    second.getTransitionOutput(transition.getSecond()));
        }

    }

    public static <I, O> MealyMachine<Integer, I, CompactTransition<O>, O> randomMealy(int size,
            Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet) {
        Random rnd = new Random();
        MealyBuilder<Integer, I, CompactTransition<O>, O, CompactMealy<I, O>> mealyBuilder = AutomatonBuilders
                .newMealy(inputAlphabet);
        for (int currentState = 0; currentState < size; currentState++) {
            for (I input : inputAlphabet) {
                O output = outputAlphabet.getSymbol(rnd.nextInt(outputAlphabet.size()));
                Integer targetState = rnd.nextInt(size);
                mealyBuilder.from(currentState).on(input).withOutput(output).to(targetState);
            }
        }
        return mealyBuilder.withInitial(0).create();
    }

    private static <S, I, T, O> Set<S> reachable(MealyMachine<S, I, T, O> machine, Alphabet<I> inputAlphabet) {
        Set<S> reach = new HashSet<>();
        Queue<S> bfsQueue = new ArrayDeque<>();

        S init = machine.getInitialState();

        bfsQueue.add(init);

        S curr;
        while ((curr = bfsQueue.poll()) != null) {
            if (reach.contains(curr))
                continue;

            for (I in : inputAlphabet) {
                S succ = machine.getSuccessor(curr, in);
                if (succ == null)
                    continue;

                if (!reach.contains(succ)) {
                    bfsQueue.add(succ);
                }
            }
            reach.add(curr);
        }
        return reach;
    }

    public static <S, I, T, O> Pair<Integer, List<Map<O, Integer>>> decompose(MealyMachine<S, I, T, O> machine,
            Alphabet<I> inputs,
            Alphabet<O> outputs) {
        DecomposeMealyMachine<O> decomposer = new DecomposeMealyMachine<>(false);
        int components = 1, best, previous = machine.size();
        List<Map<O, Integer>> best_result = null;
        List<Map<O, Integer>> previous_result = best_result;
        try {
            do {
                best = previous;
                best_result = previous_result;
                components += 1;
                previous_result = decomposer.decompose(machine, inputs, outputs, components, 0, best);
                previous = decomposer.getResultSize();
            } while (previous < best);
        } catch (ContradictionException | TimeoutException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return Pair.of(previous, previous_result);
    }

    @SuppressWarnings("unused")
    public static void find3Decomposition() throws IOException {
        int count = 3;
        int wantedSize = 4;
        while (count <= 10) {
            Alphabet<Character> inputs = Alphabets.fromArray('a', 'b');
            Alphabet<Character> outputs = Alphabets.fromArray('x', 'y', 'z');
            MealyMachine<Integer, Character, CompactTransition<Character>, Character> fsm1;
            int fsm1size;
            do {
                fsm1 = randomMealy(wantedSize, inputs, outputs);
                fsm1size = reachable(fsm1, inputs).size();
            } while (fsm1size != wantedSize);
            Pair<Integer, ?> fsm1sizedecomposed = decompose(fsm1, inputs, outputs);
            if (fsm1sizedecomposed.getFirst() < wantedSize) {
                System.out.println("fsm1 was decomposable\n");
                continue;
            }
            MealyMachine<Integer, Character, CompactTransition<Character>, Character> fsm2;
            int fsm2size;
            do {
                fsm2 = randomMealy(wantedSize - 1, inputs, outputs);
                fsm2size = reachable(fsm2, inputs).size();
            } while (fsm2size != wantedSize - 1);
            ProductMealy<Integer, Integer, Character, CompactTransition<Character>, CompactTransition<Character>, Character, Character> product = new ProductMealy<>(
                    inputs, fsm1, fsm2);
            Pair<Integer, List<Map<Pair<Character, Character>, Integer>>> productsize = decompose(product, inputs,
                    GenericDecomposedLearner.computeOutputAlphabet(product, inputs));
            if (productsize.getFirst() < wantedSize) {
                System.out.println(productsize);
                FileWriter fsm1File = new FileWriter(
                        "C:\\Users\\rk9\\Desktop\\Examples\\Example-" + String.valueOf(count) + "-L.dot");
                FileWriter fsm2File = new FileWriter(
                        "C:\\Users\\rk9\\Desktop\\Examples\\Example-" + String.valueOf(count) + "-R.dot");
                GraphDOT.write(fsm1, inputs, fsm1File);
                GraphDOT.write(fsm2, inputs, fsm2File);
                File decompositionFile = new File(
                        "C:\\Users\\rk9\\Desktop\\Examples\\Example-" + String.valueOf(count) + "-Decomposition.txt");
                FileWriter decompositionFileWriter = new FileWriter(decompositionFile);
                decompositionFileWriter.write(productsize.getSecond().toString());
                decompositionFileWriter.close();
                System.out.println(productsize);
                count += 1;
            }
        }
        System.exit(0);
    }

    @SuppressWarnings("unchecked")
    public void inspectDecomposition(File left_file, File right_file) throws IOException {
        Alphabet<String> inputAlphabet = Alphabets.fromArray("a", "b");
        MealyMachine<Object, String, Object, String> left = (MealyMachine<Object, String, Object, String>) (Object) DOTParsers
                .mealy().readModel(left_file).model;
        MealyMachine<Object, String, Object, String> right = (MealyMachine<Object, String, Object, String>) (Object) DOTParsers
                .mealy().readModel(right_file).model;
        Examples.ProductMealy<?, ?, String, ?, ?, String, String> product = new ProductMealy<>(inputAlphabet, left,
                right);
        Alphabet<Pair<String, String>> outputAlphabet = GenericDecomposedLearner.computeOutputAlphabet(product,
                inputAlphabet);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(left, inputAlphabet), inputAlphabet, true);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(right, inputAlphabet), inputAlphabet, true);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(product, inputAlphabet), inputAlphabet, true);
        Pair<Integer, List<Map<Pair<String, String>, Integer>>> result = Examples.decompose(product, inputAlphabet,
                outputAlphabet);
        System.out.println("\n");
        System.out.println(Examples
                .decompose(left, inputAlphabet, GenericDecomposedLearner.computeOutputAlphabet(left, inputAlphabet))
                .toString());
        System.out.println("\n");
        System.out.println(result.toString());
        System.out.println("\n");
        List<Map<Pair<String, String>, Integer>> maps = result.getSecond();
        MealyMachine<Object, String, Object, Integer> firstMealy = (MealyMachine<Object, String, Object, Integer>) (Object) HopcroftMinimization
                .minimizeMealy(new MappedMealy<>(product, maps.get(0)), inputAlphabet);
        MealyMachine<Object, String, Object, Integer> secondMealy = (MealyMachine<Object, String, Object, Integer>) (Object) HopcroftMinimization
                .minimizeMealy(new MappedMealy<>(product, maps.get(1)), inputAlphabet);
        MealyMachine<Object, String, Object, Integer> thirdMealy = (MealyMachine<Object, String, Object, Integer>) (Object) HopcroftMinimization
                .minimizeMealy(new MappedMealy<>(product, maps.get(2)), inputAlphabet);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(firstMealy, inputAlphabet), inputAlphabet, true);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(secondMealy, inputAlphabet), inputAlphabet, true);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(thirdMealy, inputAlphabet), inputAlphabet, true);
        MealyMachine<Pair<Object, Object>, String, Pair<Object, Object>, Pair<Integer, Integer>> firstProduct = new ProductMealy<>(
                inputAlphabet, firstMealy, secondMealy);
        MealyMachine<?, String, ?, ?> secondProduct = new ProductMealy<>(inputAlphabet, firstProduct, thirdMealy);
        Visualization.visualize(HopcroftMinimization.minimizeMealy(secondProduct, inputAlphabet), inputAlphabet, true);
        List<Map<Integer, Set<Pair<String, String>>>> reverseMap = OutputLstar.computeReverseMap(maps);
        System.out.println(reverseMap.toString());
    }

    private static DefaultQuery<String, Word<String>> parseLine(String line) {
        String[] parts = line.split(" / ");
        String inputStr = parts[0].substring(8);
        WordBuilder<String> input = new WordBuilder<>();
        for (String s : inputStr.split("'")) {
            input.add(s);
        }
        String outputStr = parts[1].split("]")[0];
        WordBuilder<String> output = new WordBuilder<>();
        for (String s : outputStr.split("'")) {
            output.add(s);
        }
        DefaultQuery<String, Word<String>> result = new DefaultQuery<>(input.toWord());
        result.answer(output.toWord());
        return result;
    }

    public static List<DefaultQuery<String, Word<String>>> parseEQs(File file) throws IOException {
        ArrayList<DefaultQuery<String, Word<String>>> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                result.add(parseLine(line));
            }
        }
        return result;
    }
}
