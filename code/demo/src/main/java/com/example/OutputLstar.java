package com.example;

import de.learnlib.oracle.MembershipOracle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.GrowingAlphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class OutputLstar<I, O> implements MealyLearner<I, O> {

    private OutputObservationTable<I, O, Integer> table;
    private final MembershipOracle<I, Word<O>> mqOracle;
    private final Alphabet<I> inputAlphabet;
    private final boolean checkConsistency;
    private final boolean useFirstInconsistency;
    public int inconsistentCount = 0;
    public int zeroOutputsCount = 0;
    public int twoOutputsCount = 0;

    /**
     * Constructor for OL*
     *
     * @param inputAlphabet         The input alphabet of the target
     * @param membershipOracle      The oracle to be used for membership queries
     * @param checkConsistency      True if OL* should check for output-consistency
     *                              (if false, more component-inconsistencies may
     *                              happen)
     * @param useFirstInconsistency True if OL* should fix the first inconsistency
     *                              found, false if OL* should find all
     *                              inconsistencies and pick the word that would fix
     *                              the most inconsistencies at the same time
     */
    public OutputLstar(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> membershipOracle,
            boolean checkConsistency, boolean useFirstInconsistency) {
        this.inputAlphabet = inputAlphabet;
        this.mqOracle = membershipOracle;
        this.checkConsistency = checkConsistency;
        this.useFirstInconsistency = useFirstInconsistency;
        this.table = new OutputObservationTable<>(inputAlphabet, membershipOracle);
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return this.getHypothesisInternal();
    }

    private OutputMealyMachine<Integer> getHypothesisInternal() {
        List<Map<O, Integer>> outputMaps = this.table.getOutputMaps();
        return new OutputMealyMachine<>(inputAlphabet, this.table.getShortPrefixRows(),
                outputMaps, this.computeReverseMap(outputMaps));
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {
        return this.refineHypothesis(ce, true);
    }

    private boolean refineHypothesis(DefaultQuery<I, Word<O>> ce, boolean fixDefects) {
        boolean refined;
        Word<I> ceWord = ce.getInput();
        do {
            refined = false;
            for (int i = 1; i <= ceWord.length(); i++) {
                Word<I> suffix = ceWord.suffix(i);
                refined = table.addSuffix(suffix);
                if (refined) {
                    break;
                }
            }
            refined |= this.closeTable();
        } while (this.isCounterexample(ce));
        if (fixDefects && refined) {
            this.fixReachableDefects();
        }
        return refined;
    }

    /**
     * Checks if the provided query is a counterexample for the current hypothesis
     *
     * @param ce The query to be checked
     * @return True if the query is a counterexample
     */
    private boolean isCounterexample(DefaultQuery<I, Word<O>> ce) {
        MealyMachine<?, I, ?, O> hypothesis = this.getHypothesisModel();
        Word<O> output = hypothesis.computeSuffixOutput(ce.getPrefix(), ce.getSuffix());
        return !output.equals(ce.getOutput());
    }

    /**
     * Chooses the row which should be moved from a long prefix row to
     * a short prefix row
     *
     * @param unclosed The list of equivalence classes of unclosed rows
     * @return The row with which to close the list
     * @implNote The row which would close the most defects on its own is chosen.
     *           This may not always lead to the smallest total number of rows we
     *           need to choose. However, this is NP-complete, since it is the
     *           (implicit) hitting set problem.
     */
    private OutputRow<I, O> selectClosingRow(List<List<OutputRow<I, O>>> unclosed) {
        OutputRow<I, O> bestRow = null;
        Integer bestCount = 0;
        HashMap<Word<I>, Integer> rowCounts = new HashMap<>();
        for (List<OutputRow<I, O>> rows : unclosed) {
            for (OutputRow<I, O> row : rows) {
                Integer rowCount = rowCounts.getOrDefault(row.getLabel(), 0) + 1;
                rowCounts.put(row.getLabel(), rowCount);
                if (rowCount > bestCount) {
                    bestRow = row;
                    bestCount = rowCount;
                }
            }
        }
        return bestRow;
    }

    private <T> T mostCommon(List<T> list) {
        T best = null;
        Integer bestCount = 0;
        HashMap<T, Integer> counts = new HashMap<>();
        for (T item : list) {
            Integer count = counts.getOrDefault(item, 0) + 1;
            counts.put(item, count);
            if (count > bestCount) {
                best = item;
                bestCount = count;
            }
        }
        return best;
    }

    @Override
    public void startLearning() {
        List<Word<I>> prefixes = Collections.singletonList(Word.epsilon());
        List<Word<I>> suffixes = this.inputAlphabet.stream().map(Word::fromLetter).toList();
        this.table.initialize(prefixes, suffixes);
        this.closeTable();
        this.fixReachableDefects();
    }

    public OutputObservationTable<I, O, Integer> getObservationTable() {
        return this.table;
    }

    /**
     * Makes sure the observation table is output-closed (and output-consistent)
     *
     * @return True if and only if the table was refined (there were new rows or
     *         columns added to the table)
     */
    private boolean closeTable() {
        boolean refined = false;
        if (this.table.isRegularClosed()) {
            return false;
        }
        this.table.setOutputMaps(this.singleOutputMap());
        List<List<OutputRow<I, O>>> unclosed = this.table.findUnclosedRows();
        while (!unclosed.isEmpty()) {
            OutputRow<I, O> newShortRow = this.selectClosingRow(unclosed);
            this.table.makeShort(newShortRow);
            refined = true;
            if (this.table.isRegularClosed()) {
                return true;
            }
            this.table.setOutputMaps(this.singleOutputMap());
            unclosed = this.table.findUnclosedRows();
        }
        if (checkConsistency && useFirstInconsistency) {
            Word<I> inconsistency = this.table.findInconsistentRows();
            if (inconsistency != null) {
                this.inconsistentCount += 1;
                System.out.println(String.valueOf(this.table.getShortPrefixRows().size()) + " / Inconsistency: "
                        + inconsistency.toString());
                this.table.addSuffix(inconsistency);
                refined = true;
                this.closeTable();
            }
        } else if (checkConsistency) {
            List<Word<I>> inconsistencies = this.table.findAllInconsistentRows();
            if (!inconsistencies.isEmpty()) {
                Word<I> toFix = mostCommon(inconsistencies);
                this.table.addSuffix(toFix);
                refined = true;
                this.inconsistentCount += 1;
                System.out.println(String.valueOf(this.table.getShortPrefixRows().size()) + " / Inconsistency: "
                        + toFix.toString());
                inconsistencies.clear();
                this.closeTable();
            }
        }
        return refined;
    }

    /**
     * For each transition, there must be exactly one output. If there are
     * 0 or >= 2, then there is at least one output for which we have a
     * counterexample. This function finds such counterexamples by enumerating the
     * reachable 'states' of the automaton that would be created from this table.
     * The states are represented by lists of rows of the table.
     * 
     * @return The found counterexample, or null if none could be found
     * @implSpec Assumes that the table is closed before this is called
     */
    public DefaultQuery<I, Word<O>> findReachableDefect() {
        OutputMealyMachine<Integer> hypothesis = this.getHypothesisInternal();
        System.out.println("States: " + hypothesis.getStates().size());
        Set<List<OutputRow<I, O>>> reach = new HashSet<>();
        Queue<List<OutputRow<I, O>>> bfsQueue = new ArrayDeque<>();
        List<OutputRow<I, O>> init = hypothesis.getInitialState();
        bfsQueue.add(init);
        Queue<WordBuilder<I>> accessSequences = new ArrayDeque<>();
        accessSequences.add(new WordBuilder<>());

        List<OutputRow<I, O>> curr;
        while ((curr = bfsQueue.poll()) != null) {
            WordBuilder<I> wb = accessSequences.poll();
            if (reach.contains(curr)) {
                continue;
            }

            for (I in : this.inputAlphabet) {
                WordBuilder<I> wbin = new WordBuilder<>(wb.toWord());
                wbin.add(in);
                List<Pair<Integer, OutputRow<I, O>>> transition = hypothesis.getTransition(curr, in);
                Set<O> outputs = hypothesis.getTransitionOutputSet(transition);
                if (outputs.size() != 1) {// We have found a defect
                    if (outputs.size() == 1) {
                        this.zeroOutputsCount += 1;
                    } else {
                        this.twoOutputsCount += 1;
                    }
                    Word<I> w = wbin.toWord();
                    DefaultQuery<I, Word<O>> ce = new DefaultQuery<>(w);
                    ce.answer(this.mqOracle.answerQuery(w));
                    return ce;
                }
                List<OutputRow<I, O>> succ = hypothesis.getSuccessor(transition);
                if (succ == null)
                    continue;

                if (!reach.contains(succ)) {
                    bfsQueue.add(succ);
                    accessSequences.add(wbin);
                }
            }
            reach.add(curr);
        }
        return null;
    }

    /**
     * Retry a previous defect to see if it still occurs
     * 
     * @param ce The query for which the defect happened
     * @return True if the defect still occurs
     */
    private boolean retryDefect(DefaultQuery<I, Word<O>> ce) {
        OutputMealyMachine<Integer> hypothesis = this.getHypothesisInternal();
        List<OutputRow<I, O>> state = hypothesis.getInitialState();
        for (I in : ce.getInput()) {
            List<Pair<Integer, OutputRow<I, O>>> transition = hypothesis.getTransition(state, in);
            Set<O> outputs = hypothesis.getTransitionOutputSet(transition);
            if (outputs.size() != 1) {
                System.out.println("Same defect");
                return true;
            }
            state = hypothesis.getSuccessor(transition);
        }
        return false;
    }

    /**
     * Finds and fixes reachable defects until there are none remaining.
     */
    private void fixReachableDefects() {
        System.out.println("Short rows: " + this.table.getShortPrefixRows().size());
        DefaultQuery<I, Word<O>> ce = this.findReachableDefect();
        while (ce != null) {
            System.out.println("Defect found: " + ce.toString());
            System.out.println("Short rows: " + this.table.getShortPrefixRows().size());
            do {
                this.refineHypothesis(ce, false);
            } while (this.retryDefect(ce));
            ce = this.findReachableDefect();
        }
        System.out.println("No more defects");
    }

    private List<Map<O, Integer>> singleOutputMap() {
        List<Map<O, Integer>> result = new ArrayList<>();
        GrowingAlphabet<O> outputAlphabet = this.table.getOutputAlphabet();
        for (O o : outputAlphabet) {
            HashMap<O, Integer> oMap = new HashMap<>();
            oMap.put(o, 1);
            for (O c : outputAlphabet) {
                if (!c.equals(o)) {
                    oMap.put(c, 0);
                }
            }
            result.add(oMap);
        }
        return result;
    }

    private List<Map<Integer, Set<O>>> computeReverseMap(List<Map<O, Integer>> outputMap) {
        List<Map<Integer, Set<O>>> result = new ArrayList<>();
        GrowingAlphabet<O> outputAlphabet = this.table.getOutputAlphabet();
        for (Map<O, Integer> oMap : outputMap) {
            Map<Integer, Set<O>> curMap = new HashMap<>();
            for (O o : outputAlphabet) {
                Integer i = oMap.get(o);
                Set<O> curList = curMap.getOrDefault(i, new HashSet<>());
                curList.add(o);
                curMap.put(i, curList);
            }
            result.add(curMap);
        }
        return result;
    }

    /**
     * Creates a Mealy machine from a list of rows
     */
    public class OutputMealyMachine<D>
            implements MealyMachine<List<OutputRow<I, O>>, I, List<Pair<D, OutputRow<I, O>>>, O> {

        Alphabet<I> inputAlphabet;
        /**
         * Short prefix rows. This is the underlying state space of the automaton.
         */
        List<OutputRow<I, O>> rows;
        List<Map<O, D>> outputMaps;
        List<Map<D, Set<O>>> outputIndicator;
        Collection<List<OutputRow<I, O>>> cachedStates;

        public OutputMealyMachine(Alphabet<I> inputAlphabet, List<OutputRow<I, O>> rows,
                List<Map<O, D>> outputMaps, List<Map<D, Set<O>>> outputIndicator) {
            if (!rows.get(0).getLabel().isEmpty()) {
                throw new IllegalStateException(
                        "OutputMealyMachine: The first element in the rows list should correspond to the empty word");
            }
            this.inputAlphabet = inputAlphabet;
            this.rows = rows;
            this.outputMaps = outputMaps;
            this.outputIndicator = outputIndicator;
        }

        @Override
        public List<OutputRow<I, O>> getSuccessor(List<Pair<D, OutputRow<I, O>>> transition) {
            return transition.stream().map(Pair::getSecond).toList();
        }

        @Override
        public Collection<List<OutputRow<I, O>>> getStates() {
            if (cachedStates != null) {
                return cachedStates;
            }
            Set<List<OutputRow<I, O>>> reach = new HashSet<>();
            Queue<List<OutputRow<I, O>>> bfsQueue = new ArrayDeque<>();

            List<OutputRow<I, O>> init = getInitialState();

            bfsQueue.add(init);

            List<OutputRow<I, O>> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    List<OutputRow<I, O>> succ = getSuccessor(curr, in);
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
        public @Nullable List<OutputRow<I, O>> getInitialState() {
            return this.rows.get(0).getShortRows();
        }

        @Override
        public @Nullable List<Pair<D, OutputRow<I, O>>> getTransition(List<OutputRow<I, O>> state, I input) {
            int inputIndex = inputAlphabet.getSymbolIndex(input);
            ArrayList<Pair<D, OutputRow<I, O>>> transition = new ArrayList<>();
            for (int i = 0; i < state.size(); i++) {
                OutputRow<I, O> currentRow = state.get(i);
                OutputRow<I, O> nextRow = currentRow.getSuccessor(inputIndex);
                D nextOutput = this.outputMaps.get(i).get(currentRow.getOutput(inputIndex));
                transition.add(Pair.of(nextOutput, nextRow.getShortRow(i)));
            }
            return transition;
        }

        @Override
        public Void getStateProperty(List<OutputRow<I, O>> state) {
            return null;
        }

        public Set<O> getTransitionOutputSet(List<Pair<D, OutputRow<I, O>>> transition) {
            D firstOutput = transition.get(0).getFirst();
            Set<O> outputs = new HashSet<>(this.outputIndicator.get(0).get(firstOutput));
            for (int i = 1; i < transition.size(); i++) {
                D iOutput = transition.get(i).getFirst();
                outputs.retainAll(this.outputIndicator.get(i).get(iOutput));
            }
            return outputs;
        }

        @Override
        public O getTransitionOutput(List<Pair<D, OutputRow<I, O>>> transition) {
            Set<O> outputs = this.getTransitionOutputSet(transition);
            if (outputs.size() != 1) {
                throw new IllegalStateException("Output for Mealy machine was not well-defined: " + transition.toString());
            }
            return outputs.iterator().next();
        }
    }
}
