package com.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.algorithm.LearningAlgorithm;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.common.util.Triple;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class InputLstar<I, O> implements LearningAlgorithm.MealyLearner<I, O> {

    final private Alphabet<I> inputAlphabet;
    final private MealyMembershipOracle<I, O> mqOracle;
    final private MealyEquivalenceOracle<I, O> eqOracle;
    final private AcexAnalyzer acexAnalyzer;

    private List<Alphabet<I>> subAlphabets;
    private List<MealyLearner<I, O>> learners;

    public InputLstar(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle,
            MealyEquivalenceOracle<I, O> eqOracle, AcexAnalyzer acexAnalyzer) {
        this.inputAlphabet = inputAlphabet;
        this.mqOracle = MealyCaches.createTreeCache(inputAlphabet, mqOracle);
        this.eqOracle = eqOracle;
        this.acexAnalyzer = acexAnalyzer;
        this.subAlphabets = new ArrayList<>();
        this.learners = new ArrayList<>();
        for (I input : this.inputAlphabet) {
            Alphabet<I> singleInputAlphabet = Alphabets.singleton(input);
            this.subAlphabets.add(singleInputAlphabet);
            this.learners.add(new TTTLearnerMealy<I, O>(singleInputAlphabet, this.mqOracle, this.acexAnalyzer));
        }
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return new ParallelInterleavingMachine<>(
                this.learners.stream().map(MealyLearner::getHypothesisModel).toList(),
                subAlphabets,
                inputAlphabet);
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {
        Pair<DefaultQuery<I, Word<O>>, List<Integer>> analyzed = this.analyzeCounterexample(ce);
        ce = analyzed.getFirst();
        List<Integer> alphabetIndexList = analyzed.getSecond();
        // Collect learners not involved in counterexample
        List<Integer> complementIndexList = this.complementList(alphabetIndexList, this.subAlphabets.size());
        List<Alphabet<I>> nextAlphabets = this.getAll(this.subAlphabets, complementIndexList);
        List<MealyLearner<I, O>> nextLearners = this.getAll(this.learners, complementIndexList);
        // Merge alphabets, create new learner
        List<Alphabet<I>> currentAlphabetList = this.getAll(this.subAlphabets, alphabetIndexList);
        Alphabet<I> mergedAlphabet = this.mergeAlphabets(currentAlphabetList);
        nextAlphabets.add(mergedAlphabet);
        TTTLearnerMealy<I, O> mergedLearner = new TTTLearnerMealy<>(mergedAlphabet, this.mqOracle, this.acexAnalyzer);
        nextLearners.add(mergedLearner);

        this.subAlphabets = nextAlphabets;
        this.learners = nextLearners;

        this.learnComponent(this.learners.size() - 1);
        return true;
    }

    private Pair<DefaultQuery<I, Word<O>>, List<Integer>> analyzeCounterexample(DefaultQuery<I, Word<O>> ce) {
        MealyMachine<?, I, ?, O> hypothesis = this.getHypothesisModel();
        ce = this.shortestPrefixCounterexample(ce);
        List<Integer> alphabetIndexList = this.involvedAlphabets(ce);
        for (int k = 2; k <= alphabetIndexList.size(); k++) {
            List<Integer> indexList = this.firstCombination(k);
            while (indexList != null) {
                List<Integer> currentAlphabetIndexList = this.getAll(alphabetIndexList, indexList);
                List<Alphabet<I>> currentAlphabetList = this.getAll(this.subAlphabets, currentAlphabetIndexList);
                Alphabet<I> currentAlphabet = this.mergeAlphabets(currentAlphabetList);
                DefaultQuery<I, Word<O>> projectedQuery = this.projectQuery(ce, currentAlphabet);
                if (!projectedQuery.getOutput().equals(hypothesis.computeOutput(projectedQuery.getInput()))) {
                    return Pair.of(projectedQuery, currentAlphabetIndexList);
                }
                indexList = this.nextCombination(indexList, alphabetIndexList.size() - 1);
            }
        }
        throw new IllegalStateException("Given input was not a counterexample");
    }

    private List<Integer> firstCombination(int k) {
        List<Integer> result = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            result.add(i);
        }
        return result;
    }

    private List<Integer> nextCombination(List<Integer> indexList, int max) {
        int incrementIndex = -1;
        int current = max;
        boolean nobreak = true;
        for (int i = indexList.size() - 1; i >= 0; i--) {
            if (indexList.get(i) != current) {
                incrementIndex = i;
                current = indexList.get(i) + 1;
                nobreak = false;
                break;
            } else {
                current -= 1;
            }
        }
        if (nobreak) {
            return null;
        }
        List<Integer> result = new ArrayList<>(indexList.subList(0, incrementIndex));
        for (int i = incrementIndex; i < indexList.size(); i++) {
            result.add(current);
            current += 1;
        }
        return result;
    }

    private <T> List<T> getAll(List<T> items, List<Integer> indexList) {
        List<T> result = new ArrayList<>();
        for (Integer i : indexList) {
            result.add(items.get(i));
        }
        return result;
    }

    /**
     * @param indexList List of indices to be excluded
     * @param bound     Upper bound (exclusive) of integers to include
     * @return List of indices smaller than bound, not in indexList
     * @implSpec Assumes that indexList is sorted from low to high
     *           and only contains integers >= 0
     */
    private List<Integer> complementList(List<Integer> indexList, int bound) {
        List<Integer> result = new ArrayList<>();
        int currentIndex = 0;
        Integer current = indexList.get(0);
        for (int i = 0; i < bound; i++) {
            if (i != current) {
                result.add(i);
            } else {
                currentIndex += 1;
                if (currentIndex < indexList.size()) {
                    current = indexList.get(currentIndex);
                }
            }
        }
        return result;
    }

    private DefaultQuery<I, Word<O>> projectQuery(DefaultQuery<I, Word<O>> query, Alphabet<I> alphabet) {
        Word<I> input = query.getInput();
        WordBuilder<I> wb = new WordBuilder<>();
        for (I symbol : input) {
            if (alphabet.containsSymbol(symbol)) {
                wb.add(symbol);
            }
        }
        DefaultQuery<I, Word<O>> result = new DefaultQuery<>(wb.toWord());
        result.answer(this.mqOracle.answerQuery(result.getInput()));
        return result;
    }

    private Alphabet<I> mergeAlphabets(List<Alphabet<I>> alphabets) {
        List<I> inputs = new ArrayList<>();
        for (Alphabet<I> alphabet : alphabets) {
            inputs.addAll(alphabet);
        }
        return Alphabets.fromList(inputs);
    }

    private List<Integer> involvedAlphabets(DefaultQuery<I, Word<O>> ce) {
        ArrayList<Integer> involvedIndexList = new ArrayList<>();
        for (int i = 0; i < this.subAlphabets.size(); i++) {
            Alphabet<I> subAlphabet = this.subAlphabets.get(i);
            for (I input : ce.getInput()) {
                if (subAlphabet.containsSymbol(input)) {
                    involvedIndexList.add(i);
                    break;
                }
            }
        }
        return involvedIndexList;
    }

    private DefaultQuery<I, Word<O>> shortestPrefixCounterexample(DefaultQuery<I, Word<O>> ce) {
        MealyMachine<?, I, ?, O> hypothesis = this.getHypothesisModel();
        Word<I> input = ce.getInput();
        Word<O> output = ce.getOutput();
        for (int i = 1; i <= input.length(); i++) {
            Word<I> inputPrefix = input.prefix(i);
            Word<O> outputPrefix = output.prefix(i);
            if (!hypothesis.computeOutput(inputPrefix).equals(outputPrefix)) {
                return new DefaultQuery<I, Word<O>>(inputPrefix, outputPrefix);
            }
        }
        throw new IllegalStateException("Given input was not a counterexample");
    }

    @Override
    public void startLearning() {
        for (int i = 0; i < this.learners.size(); i++) {
            this.learnComponent(i);
        }
    }

    private void learnComponent(int index) {
        MealyLearner<I, O> learner = this.learners.get(index);
        learner.startLearning();
        while (true) {
            MealyMachine<?, I, ?, O> hypothesis = learner.getHypothesisModel();
            DefaultQuery<I, Word<O>> ce = this.eqOracle.findCounterExample(hypothesis, this.subAlphabets.get(index));
            if (ce == null) {
                break;
            }
            learner.refineHypothesis(ce);
        }
    }

    public class ParallelInterleavingMachine<S, T> implements MealyMachine<List<S>, I, Triple<List<S>, Integer, T>, O> {

        private List<MealyMachine<S, I, T, O>> components;
        private List<Alphabet<I>> subAlphabets;
        private Alphabet<I> inputAlphabet;
        private Collection<List<S>> cachedStates;

        public ParallelInterleavingMachine(List<MealyMachine<S, I, T, O>> components,
                List<Alphabet<I>> subAlphabets, Alphabet<I> inputAlphabet) {
            this.components = components;
            this.subAlphabets = subAlphabets;
            this.inputAlphabet = inputAlphabet;
        }

        @Override
        public List<S> getSuccessor(Triple<List<S>, Integer, T> transition) {
            ArrayList<S> state = new ArrayList<>(transition.getFirst());
            Integer index = transition.getSecond();
            S nextState = this.components.get(index).getSuccessor(transition.getThird());
            state.set(index, nextState);
            return state;
        }

        @Override
        public Collection<List<S>> getStates() {
            if (cachedStates != null) {
                return cachedStates;
            }
            Set<List<S>> reach = new HashSet<>();
            Queue<List<S>> bfsQueue = new ArrayDeque<>();

            List<S> init = getInitialState();

            bfsQueue.add(init);

            List<S> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    List<S> succ = getSuccessor(curr, in);
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
        public @Nullable List<S> getInitialState() {
            return this.components.stream().map(MealyMachine::getInitialState).toList();
        }

        @Override
        public @Nullable Triple<List<S>, Integer, T> getTransition(List<S> states, I input) {
            int index = -1;
            for (int i = 0; i < subAlphabets.size(); i++) {
                if (subAlphabets.get(i).containsSymbol(input)) {
                    index = i;
                }
            }
            if (index == -1) {
                throw new IllegalArgumentException("None of the subalphabets contains the input " + input.toString());
            }
            T transition = this.components.get(index).getTransition(states.get(index), input);
            return Triple.of(states, index, transition);
        }

        @Override
        public Void getStateProperty(List<S> state) {
            return null;
        }

        @Override
        public O getTransitionOutput(Triple<List<S>, Integer, T> transition) {
            return this.components.get(transition.getSecond()).getTransitionOutput(transition.getThird());
        }

    }

}
