package com.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import de.learnlib.Mapper;
import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.oracle.membership.MappedOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.GrowingAlphabet;
import net.automatalib.alphabet.GrowingMapAlphabet;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.util.automaton.minimizer.hopcroft.HopcroftMinimization;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class GenericDecomposedLearner<I, O> implements MealyLearner<I, O> {

    private final Alphabet<I> inputAlphabet;
    private final GrowingAlphabet<O> outputAlphabet;
    private final MealyCacheOracle<I, O> membershipOracle;
    private final Function<MembershipOracle<I, Word<Integer>>, MealyLearner<I, Integer>> delegateFunction;

    private final Integer components;
    private List<Map<O, Integer>> outputMaps;
    private final List<OutputMapper<Integer>> outputMappers;
    private final List<MembershipOracle<I, Word<Integer>>> delegateOracles;
    private final List<MealyLearner<I, Integer>> delegateLearners;
    private final boolean useOptimalMap;

    // private Integer previousResultSize = 0;

    /**
     * Constructor for using OptimalMap as the decomposition function and a dynamic
     * number of components.
     */
    public GenericDecomposedLearner(Alphabet<I> inputAlphabet, MealyCacheOracle<I, O> membershipOracle,
            Function<MembershipOracle<I, Word<Integer>>, MealyLearner<I, Integer>> delegateFunction) {
        this.inputAlphabet = inputAlphabet;
        this.outputAlphabet = new GrowingMapAlphabet<>();
        this.membershipOracle = membershipOracle;
        this.delegateFunction = delegateFunction;

        this.components = null;
        this.outputMaps = new ArrayList<>();
        this.outputMaps.add(new HashMap<>());
        this.outputMappers = new ArrayList<>();
        this.delegateOracles = new ArrayList<>();
        this.delegateLearners = new ArrayList<>();
        this.useOptimalMap = true;
    }

    /**
     * Concstructor for using OptimalMap with a fixed number of components.
     */
    public GenericDecomposedLearner(Alphabet<I> inputAlphabet, MealyCacheOracle<I, O> membershipOracle,
            Function<MembershipOracle<I, Word<Integer>>, MealyLearner<I, Integer>> delegateFunction, int components) {
        this.inputAlphabet = inputAlphabet;
        this.outputAlphabet = new GrowingMapAlphabet<>();
        this.membershipOracle = membershipOracle;
        this.delegateFunction = delegateFunction;

        this.components = components;
        this.outputMaps = new ArrayList<>();
        this.outputMaps.add(new HashMap<>());
        this.outputMappers = new ArrayList<>();
        this.delegateOracles = new ArrayList<>();
        this.delegateLearners = new ArrayList<>();
        this.useOptimalMap = true;
    }

    /**
     * Constructor for using (partial) pre-defined maps.
     * Mappings for undefined components will be using the identity map.
     * 
     * @implSpec The integers used in the output maps should be in the range 0-#O
     */
    public GenericDecomposedLearner(Alphabet<I> inputAlphabet, MealyCacheOracle<I, O> membershipOracle,
            Function<MembershipOracle<I, Word<Integer>>, MealyLearner<I, Integer>> delegateFunction,
            List<Map<O, Integer>> outputMaps) {
        this.inputAlphabet = inputAlphabet;
        this.outputAlphabet = new GrowingMapAlphabet<>();
        this.outputAlphabet
                .addAll(outputMaps.stream().flatMap(map -> map.keySet().stream()).collect(Collectors.toList()));
        this.membershipOracle = membershipOracle;
        this.delegateFunction = delegateFunction;

        if (outputMaps == null || outputMaps.isEmpty()) {
            this.components = 1;
            this.outputMaps = identityMap();
        } else {
            this.components = outputMaps.size();
            this.outputMaps = outputMaps;
        }
        this.outputMappers = new ArrayList<>();
        this.delegateOracles = new ArrayList<>();
        this.delegateLearners = new ArrayList<>();
        this.useOptimalMap = false;

        assert validOutputMap();
    }

    private boolean validOutputMap() {
        for (Integer i : this.outputMaps.stream().flatMap(map -> map.values().stream()).collect(Collectors.toList())) {
            if (i >= this.outputAlphabet.size()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public MealyMachine<List<Object>, I, List<Object>, O> getHypothesisModel() {
        return this.getHypothesisInternal();
    }

    private GenericProductMealy<Integer> getHypothesisInternal() {
        List<MealyMachine<?, I, ?, Integer>> machines = this.delegateLearners.stream()
                .map(delegate -> delegate.getHypothesisModel()).collect(Collectors.toList());
        return new GenericProductMealy<Integer>(this.inputAlphabet, machines, this.outputMaps);
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {
        this.outputAlphabet.addAll(ce.getOutput().asList());
        this.outputAlphabet
                .addAll(GenericDecomposedLearner.computeOutputAlphabet(this.getHypothesisModel(), this.inputAlphabet));
        this.membershipOracle.answerQuery(ce.getInput());
        if (this.useOptimalMap) {
            refineOptimalMap(ce);
        }
        this.makeComponentConsistent();
        this.makeCacheConsistent();
        return true;
    }

    private void refineOptimalMap(DefaultQuery<I, Word<O>> ce) {
        List<Map<O, Integer>> newMaps = this.optimalMap();
        int oldSize = mapAndMinimize(this.outputMaps);
        int newSize = mapAndMinimize(newMaps);
        if (newSize < oldSize) {
            System.out.println("New better output maps found");
            this.outputMaps = newMaps;
            this.outputMappers.clear();
            this.delegateOracles.clear();
            this.delegateLearners.clear();
            for (int i = 0; i < this.outputMaps.size(); i++) {
                OutputMapper<Integer> mapper = new OutputMapper<>(new AlphabetFunction(this.outputMaps.get(i)));
                this.outputMappers.add(mapper);
                MappedOracle<I, Word<Integer>, I, Word<O>> mappedOracle = new MappedOracle<I, Word<Integer>, I, Word<O>>(
                        this.membershipOracle, mapper);
                this.delegateOracles.add(mappedOracle);
                MealyLearner<I, Integer> delegateLearner = this.delegateFunction.apply(mappedOracle);
                this.delegateLearners.add(delegateLearner);
            }
            for (MealyLearner<I, Integer> delegateLearner : this.delegateLearners) {
                delegateLearner.startLearning();
            }
        }
    }

    private int mapAndMinimize(List<Map<O, Integer>> maps) {
        int maxSize = 0;
        MealyMachine<List<Object>, I, List<Object>, O> hypothesis = getHypothesisInternal();
        for (Map<O, Integer> map : maps) {
            CompactMealy<I, Integer> mealy = HopcroftMinimization.minimizeMealy(new MappedMealy<>(hypothesis, map),
                    inputAlphabet);
            int size = mealy.size();
            if (size > maxSize) {
                maxSize = size;
            }
        }
        return maxSize;
    }

    private void makeCacheConsistent() {
        MealyEquivalenceOracle<I, O> cacheConsistencyOracle = this.membershipOracle.createCacheConsistencyTest();
        MealyMachine<List<Object>, I, List<Object>, O> hypothesis = this.getHypothesisModel();
        DefaultQuery<I, Word<O>> ce = cacheConsistencyOracle.findCounterExample(hypothesis, inputAlphabet);
        while (ce != null) {
            for (int i = 0; i < this.delegateLearners.size(); i++) {
                this.delegateLearners.get(i).refineHypothesis(this.outputMappers.get(i).mapQuery(ce));
            }
            this.makeComponentConsistent();
            hypothesis = this.getHypothesisModel();
            ce = cacheConsistencyOracle.findCounterExample(hypothesis, inputAlphabet);
        }
    }

    private void makeComponentConsistent() {
        GenericProductMealy<Integer> hypothesis = this.getHypothesisInternal();
        Word<I> input = hypothesis.isConsistent();
        while (input != null) {
            DefaultQuery<I, Word<O>> ce = new DefaultQuery<>(input);
            System.out.println("CC: " + ce.toString());
            this.membershipOracle.processQuery(ce);
            for (int i = 0; i < this.delegateLearners.size(); i++) {
                this.delegateLearners.get(i).refineHypothesis(this.outputMappers.get(i).mapQuery(ce));
            }
            hypothesis = this.getHypothesisInternal();
            input = hypothesis.isConsistent();
        }
    }

    @Override
    public void startLearning() {
        assert this.delegateLearners.isEmpty();
        for (Map<O, Integer> map : this.outputMaps) {
            OutputMapper<Integer> mapper = new OutputMapper<>(new AlphabetFunction(map));
            this.outputMappers.add(mapper);
            MembershipOracle<I, Word<Integer>> delegateOracle = new MappedOracle<>(this.membershipOracle, mapper);
            MealyLearner<I, Integer> delegate = this.delegateFunction.apply(delegateOracle);
            this.delegateLearners.add(delegate);
            delegate.startLearning();
        }
        this.outputAlphabet
                .addAll(GenericDecomposedLearner.computeOutputAlphabet(this.getHypothesisModel(), this.inputAlphabet));
    }

    public static <SS, II, TT, OO> Alphabet<OO> computeOutputAlphabet(MealyMachine<SS, II, TT, OO> machine,
            Alphabet<II> inputAlphabet) {
        GrowingAlphabet<OO> outputAlphabet = new GrowingMapAlphabet<>();
        Collection<SS> reach = new HashSet<>();
        Queue<SS> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(machine.getInitialState());
        SS curr;
        while ((curr = bfsQueue.poll()) != null) {
            if (reach.contains(curr))
                continue;

            for (II in : inputAlphabet) {
                outputAlphabet.add(machine.getOutput(curr, in));
                SS succ = machine.getSuccessor(curr, in);
                if (succ == null)
                    continue;

                if (!reach.contains(succ)) {
                    bfsQueue.add(succ);
                }
            }
            reach.add(curr);
        }
        return outputAlphabet;
    }

    private List<Map<O, Integer>> identityMap() {
        Map<O, Integer> map = new HashMap<>();
        for (int i = 0; i < outputAlphabet.size(); i++) {
            map.put(outputAlphabet.getSymbol(i), i);
        }
        List<Map<O, Integer>> result = new ArrayList<>(1);
        result.add(map);
        return result;
    }

    /**
     * Tries to find the optimal weak decomposition of the Mealy machine into
     * components. This is by finding the best weak decomposition for a given number
     * of components using a SAT solver, and increasing the number of components
     * until there is no benefit to using more components.
     * 
     * @return List of maps, corresponding to the optimal weak decomposition
     */
    private List<Map<O, Integer>> optimalMap() {
        System.out.println("Begin SAT");
        DecomposeMealyMachine<O> decomposer = new DecomposeMealyMachine<>(true);
        MealyMachine<List<Object>, I, List<Object>, O> machine = this.getHypothesisModel();
        int states = machine.getStates().size();
        int best, previous = states;
        int cur_components;
        if (this.components == null) {
            cur_components = 1;
        } else {
            cur_components = this.components.intValue();
        }
        List<Map<O, Integer>> best_result = this.identityMap();
        List<Map<O, Integer>> previous_result = best_result;
        try {
            do {
                best = previous;
                best_result = previous_result;
                if (this.components == null) {
                    cur_components += 1;
                }
                System.out.println("Components: " + String.valueOf(components));
                int lower = (int) Math.ceil(Math.pow(states, 1.0 / cur_components)) - 1;
                previous_result = decomposer.decompose(machine, this.inputAlphabet, this.outputAlphabet, cur_components,
                        lower, states);
                previous = decomposer.getResultSize();
            } while (previous < best && this.components == null);
            if (previous < best) {
                best_result = previous_result;
            }
        } catch (ContradictionException | TimeoutException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        System.out.println("End SAT");
        // this.previousResultSize = decomposer.getResultSize();
        System.out.println(best_result.toString());
        return best_result;
    }

    public class GenericProductMealy<D> implements MealyMachine<List<Object>, I, List<Object>, O> {

        Alphabet<I> inputAlphabet;
        List<MealyMachine<Object, I, Object, D>> delegates;
        List<Map<D, Set<O>>> outputIndicator;
        Collection<List<Object>> cachedStates;

        @SuppressWarnings("unchecked")
        public GenericProductMealy(Alphabet<I> inputAlphabet, List<MealyMachine<?, I, ?, D>> delegates,
                List<Map<O, D>> outputMaps) {
            this.inputAlphabet = inputAlphabet;
            this.delegates = (List<MealyMachine<Object, I, Object, D>>) (Object) delegates;
            this.outputIndicator = OutputLstar.computeReverseMap(outputMaps);
        }

        @Override
        public List<Object> getSuccessor(List<Object> transitions) {
            List<Object> suc = new ArrayList<>();
            for (int i = 0; i < this.delegates.size(); i++) {
                suc.add(this.delegates.get(i).getSuccessor(transitions.get(i)));
            }
            return suc;
        }

        @Override
        public Collection<List<Object>> getStates() {
            if (this.cachedStates != null) {
                return this.cachedStates;
            }
            Set<List<Object>> reach = new HashSet<>();
            Queue<List<Object>> bfsQueue = new ArrayDeque<>();

            List<Object> init = getInitialState();

            bfsQueue.add(init);

            List<Object> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    List<Object> succ = getSuccessor(curr, in);
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

        /**
         * Checks if all outputs of the machine are well-defined.
         * If it is, returns {@code null} and stores the state space in the cache.
         * {@code getTransitionOutput} is then guaranteed to never throw an
         * {@code IllegalStateException}.
         * If it is not, returns a sequence of inputs leading to the inconsistency.
         * 
         * @return {@code null} if consistent, otherwise a sequence of inputs leading to
         *         the inconsistency
         */
        public Word<I> isConsistent() {
            Set<List<Object>> reach = new HashSet<>();
            Queue<List<Object>> bfsQueue = new ArrayDeque<>();
            Queue<WordBuilder<I>> inputQueue = new ArrayDeque<>();

            List<Object> init = getInitialState();

            bfsQueue.add(init);
            inputQueue.add(new WordBuilder<>());

            List<Object> curr;
            while ((curr = bfsQueue.poll()) != null) {
                WordBuilder<I> currWord = inputQueue.poll();
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    List<Object> succ = getSuccessor(curr, in);
                    if (succ == null)
                        continue;
                    WordBuilder<I> succWord = new WordBuilder<>(currWord.toWord());
                    succWord.add(in);
                    Set<O> output = getTransitionOutputSet(getTransition(curr, in));
                    if (output.size() != 1) {
                        return succWord.toWord();
                    }

                    if (!reach.contains(succ)) {
                        bfsQueue.add(succ);
                        inputQueue.add(succWord);
                    }
                }
                reach.add(curr);
            }
            cachedStates = reach;
            return null;
        }

        @Override
        public @Nullable List<Object> getInitialState() {
            return this.delegates.stream().map(MealyMachine::getInitialState).collect(Collectors.toList());
        }

        @Override
        public @Nullable List<Object> getTransition(List<Object> states, I input) {
            List<Object> transition = new ArrayList<>();
            for (int i = 0; i < this.delegates.size(); i++) {
                transition.add(this.delegates.get(i).getTransition(states.get(i), input));
            }
            return transition;
        }

        @Override
        public Void getStateProperty(List<Object> states) {
            return null;
        }

        public Set<O> getTransitionOutputSet(List<Object> transition) {
            D firstOutput = delegates.get(0).getTransitionOutput(transition.get(0));
            Set<O> outputs = new HashSet<>(this.outputIndicator.get(0).get(firstOutput));
            for (int i = 1; i < transition.size(); i++) {
                D iOutput = delegates.get(i).getTransitionOutput(transition.get(i));
                outputs.retainAll(this.outputIndicator.get(i).get(iOutput));
            }
            return outputs;
        }

        @Override
        public O getTransitionOutput(List<Object> transition) {
            Set<O> outputs = this.getTransitionOutputSet(transition);
            if (outputs.size() != 1) {
                throw new IllegalStateException(
                        "Output for Mealy machine was not well-defined: " + transition.toString() + " / "
                                + outputs.toString());
            }
            return outputs.iterator().next();
        }
    }

    public class OutputMapper<D> implements Mapper.AsynchronousMapper<I, Word<D>, I, Word<O>> {

        private final Function<O, D> map;

        public OutputMapper(Function<O, D> map) {
            this.map = map;
        }

        @Override
        public I mapInput(I input) {
            return input;
        }

        @Override
        public Word<D> mapOutput(Word<O> output) {
            ArrayList<D> result = new ArrayList<>();
            for (O o : output) {
                result.add(this.map.apply(o));
            }
            return Word.fromList(result);
        }

        public DefaultQuery<I, Word<D>> mapQuery(DefaultQuery<I, Word<O>> query) {
            DefaultQuery<I, Word<D>> result = new DefaultQuery<>(query.getPrefix(), query.getSuffix());
            result.answer(this.mapOutput(query.getOutput()));
            return result;
        }
    }

    private class AlphabetFunction implements Function<O, Integer> {

        Map<O, Integer> map;

        public AlphabetFunction(Map<O, Integer> map) {
            this.map = map;
        }

        @Override
        public Integer apply(O t) {
            if (!map.containsKey(t)) {
                map.put(t, outputAlphabet.addSymbol(t));
            }
            return map.get(t);
        }
    }
}
