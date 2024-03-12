package com.example;

import de.learnlib.oracle.MembershipOracle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class OutputLstar<I, O> implements MealyLearner<I, O> {

    private OutputObservationTable<I, O> table;
    private final MembershipOracle<I, Word<O>> mqOracle;
    private final Alphabet<I> inputAlphabet;
    private final boolean checkConsistency;
    public int inconsistentCount = 0;
    public int zeroOutputsCount = 0;
    public int twoOutputsCount = 0;

    public OutputLstar(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> membershipOracle, boolean checkConsistency) {
        this.inputAlphabet = inputAlphabet;
        this.mqOracle = membershipOracle;
        this.checkConsistency = checkConsistency;
        this.table = new OutputObservationTable<>(inputAlphabet, membershipOracle);
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return new OutputMealyMachine(inputAlphabet, this.table.getOutputAlphabet(), this.table.getShortPrefixRows());
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

    @Override
    public void startLearning() {
        List<Word<I>> prefixes = Collections.singletonList(Word.epsilon());
        List<Word<I>> suffixes = this.inputAlphabet.stream().map(Word::fromLetter).toList();
        this.table.initialize(prefixes, suffixes);
        this.closeTable();
        this.fixReachableDefects();
    }

    public OutputObservationTable<I, O> getObservationTable() {
        return this.table;
    }

    private boolean closeTable() {
        boolean refined = false;
        if(this.table.isRegularClosed()) {
            return false;
        }
        List<List<OutputRow<I, O>>> unclosed =  this.table.findUnclosedRows();
        while (!unclosed.isEmpty()) {
            OutputRow<I, O> newShortRow = this.selectClosingRow(unclosed);
            this.table.makeShort(newShortRow);
            refined = true;
            if(this.table.isRegularClosed()) {
                return true;
            }
            unclosed = this.table.findUnclosedRows();
        }
        if(checkConsistency) {
            Word<I> inconsistency = this.table.findInconsistentRows();
            if(inconsistency != null) {
                this.inconsistentCount += 1;
                System.out.println(String.valueOf(this.table.getShortPrefixRows().size()) + " / Inconsistency: " + inconsistency.toString());
                this.table.addSuffix(inconsistency);
                refined = true;
                this.closeTable();
            }
        }
        return refined;
    }

    private DefaultQuery<I, Word<O>> findTwoOutputs(ProjectedOutputMealyMachine hypothesis) {
        Set<Pair<OutputRow<I, O>, OutputRow<I, O>>> reach = new HashSet<>();
        Queue<Pair<OutputRow<I, O>, OutputRow<I, O>>> bfsQueue = new ArrayDeque<>();
        Pair<OutputRow<I, O>, OutputRow<I, O>> init = hypothesis.getInitialState();
        bfsQueue.add(init);
        Queue<WordBuilder<I>> accessSequences = new ArrayDeque<>();
        accessSequences.add(new WordBuilder<>());

        Pair<OutputRow<I, O>, OutputRow<I, O>> curr;
        while ((curr = bfsQueue.poll()) != null) {
            WordBuilder<I> wb = accessSequences.poll();
            if (reach.contains(curr)) {
                continue;
            }

            for (I in : this.inputAlphabet) {
                WordBuilder<I> wbin = new WordBuilder<>(wb.toWord());
                wbin.add(in);
                Pair<Pair<Boolean, OutputRow<I, O>>, Pair<Boolean, OutputRow<I, O>>> transition = hypothesis.getTransition(curr, in);
                if (transition.getFirst().getFirst() && transition.getSecond().getFirst()) {// We have found a defect
                    this.twoOutputsCount += 1;
                    Word<I> w = wbin.toWord();
                    DefaultQuery<I, Word<O>> ce = new DefaultQuery<>(w);
                    ce.answer(this.mqOracle.answerQuery(w));
                    this.table.findAllInconsistentRows(); //TODO: Remove this line
                    return ce;
                }
                Pair<OutputRow<I, O>, OutputRow<I, O>> succ = hypothesis.getSuccessor(transition);
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

    private DefaultQuery<I, Word<O>> findMultipleOutputs() {
        OutputMealyMachine hypothesis = new OutputMealyMachine(inputAlphabet, this.table.getOutputAlphabet(),
                this.table.getShortPrefixRows());
        int n = this.table.getOutputAlphabet().size();
        for (int firstIndex = 0; firstIndex < n - 1; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < n; secondIndex++) {
                ProjectedOutputMealyMachine currentMachine = hypothesis.project(firstIndex, secondIndex);
                DefaultQuery<I, Word<O>> ce = this.findTwoOutputs(currentMachine);
                if(ce != null) {
                    return ce;
                }
            }
        }
        return null;
    }

    private DefaultQuery<I, Word<O>> findZeroOutputs() {
        OutputMealyMachine hypothesis = new OutputMealyMachine(
                inputAlphabet, this.table.getOutputAlphabet(), this.table.getShortPrefixRows());
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
                List<Pair<Boolean, OutputRow<I, O>>> transition = hypothesis.getTransition(curr, in);
                List<Boolean> outputs = transition.stream().map(Pair::getFirst).toList();
                long trueCount = Collections.frequency(outputs, true);
                if (trueCount == 0) {// We have found a defect
                    this.zeroOutputsCount += 1;
                    Word<I> w = wbin.toWord();
                    DefaultQuery<I, Word<O>> ce = new DefaultQuery<>(w);
                    ce.answer(this.mqOracle.answerQuery(w));
                    this.table.findAllInconsistentRows(); //TODO: Remove this line
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
        DefaultQuery<I, Word<O>> multipleOutputs = this.findMultipleOutputs();
        return multipleOutputs != null ? multipleOutputs : this.findZeroOutputs();
    }

    private boolean retryDefect(DefaultQuery<I, Word<O>> ce) {
        OutputMealyMachine hypothesis = new OutputMealyMachine(
                inputAlphabet, this.table.getOutputAlphabet(), this.table.getShortPrefixRows());
        List<OutputRow<I, O>> state = hypothesis.getInitialState();
        for (I in : ce.getInput()) {
            List<Pair<Boolean, OutputRow<I, O>>> transition = hypothesis.getTransition(state, in);
            List<Boolean> outputs = transition.stream().map(Pair::getFirst).toList();
            long trueCount = Collections.frequency(outputs, true);
            if (trueCount != 1) {
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

    public class OutputMealyMachine
            implements MealyMachine<List<OutputRow<I, O>>, I, List<Pair<Boolean, OutputRow<I, O>>>, O> {

        Alphabet<I> inputAlphabet;
        Alphabet<O> outputAlphabet;
        /**
         * Short prefix rows. This is the underlying state space of the automaton.
         */
        List<OutputRow<I, O>> rows;
        Collection<List<OutputRow<I, O>>> cachedStates;

        public OutputMealyMachine(Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet, List<OutputRow<I, O>> rows) {
            if (!rows.get(0).getLabel().isEmpty()) {
                throw new IllegalStateException(
                        "OutputMealyMachine: The first element in the rows list should correspond to the empty word");
            }
            this.inputAlphabet = inputAlphabet;
            this.outputAlphabet = outputAlphabet;
            this.rows = rows;
        }

        @Override
        public List<OutputRow<I, O>> getSuccessor(List<Pair<Boolean, OutputRow<I, O>>> transition) {
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
        public @Nullable List<Pair<Boolean, OutputRow<I, O>>> getTransition(List<OutputRow<I, O>> state, I input) {
            int inputIndex = inputAlphabet.getSymbolIndex(input);
            ArrayList<Pair<Boolean, OutputRow<I, O>>> transition = new ArrayList<>();
            for (int i = 0; i < state.size(); i++) {
                OutputRow<I, O> currentRow = state.get(i);
                OutputRow<I, O> nextRow = currentRow.getSuccessor(inputIndex);
                O currentOutput = outputAlphabet.getSymbol(i);
                Boolean nextOutput = currentRow.getOutput(inputIndex).equals(currentOutput);
                transition.add(Pair.of(nextOutput, nextRow.getShortRow(i)));
            }
            return transition;
        }

        @Override
        public Void getStateProperty(List<OutputRow<I, O>> state) {
            return null;
        }

        @Override
        public O getTransitionOutput(List<Pair<Boolean, OutputRow<I, O>>> transition) {
            for (int i = 0; i < transition.size(); i++) {
                if (transition.get(i).getFirst()) {
                    return this.outputAlphabet.getSymbol(i);
                }
            }
            return null;
        }

        public ProjectedOutputMealyMachine project(int firstIndex, int secondIndex) {
            return new ProjectedOutputMealyMachine(this.inputAlphabet, this.outputAlphabet, this.rows,
                    firstIndex, secondIndex);
        }
    }

    public class ProjectedOutputMealyMachine
            implements
            MealyMachine<Pair<OutputRow<I, O>, OutputRow<I, O>>, I, Pair<Pair<Boolean, OutputRow<I, O>>, Pair<Boolean, OutputRow<I, O>>>, O> {

        Alphabet<I> inputAlphabet;
        O firstOutput;
        O secondOutput;
        /**
         * Short prefix rows. This is the underlying state space of the automaton.
         */
        List<OutputRow<I, O>> rows;
        Collection<Pair<OutputRow<I, O>, OutputRow<I, O>>> cachedStates;
        int firstIndex;
        int secondIndex;

        public ProjectedOutputMealyMachine(Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet,
                List<OutputRow<I, O>> rows, int firstIndex, int secondIndex) {
            this.inputAlphabet = inputAlphabet;
            this.firstOutput = outputAlphabet.getSymbol(firstIndex);
            this.secondOutput = outputAlphabet.getSymbol(secondIndex);
            this.rows = rows;
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
        }

        @Override
        public Pair<OutputRow<I, O>, OutputRow<I, O>> getSuccessor(
                Pair<Pair<Boolean, OutputRow<I, O>>, Pair<Boolean, OutputRow<I, O>>> transition) {
            return Pair.of(transition.getFirst().getSecond(), transition.getSecond().getSecond());
        }

        @Override
        public Collection<Pair<OutputRow<I, O>, OutputRow<I, O>>> getStates() {
            if (cachedStates != null) {
                return cachedStates;
            }
            Set<Pair<OutputRow<I, O>, OutputRow<I, O>>> reach = new HashSet<>();
            Queue<Pair<OutputRow<I, O>, OutputRow<I, O>>> bfsQueue = new ArrayDeque<>();

            Pair<OutputRow<I, O>, OutputRow<I, O>> init = getInitialState();

            bfsQueue.add(init);

            Pair<OutputRow<I, O>, OutputRow<I, O>> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if (reach.contains(curr))
                    continue;

                for (I in : this.inputAlphabet) {
                    Pair<OutputRow<I, O>, OutputRow<I, O>> succ = getSuccessor(curr, in);
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
        public @Nullable Pair<OutputRow<I, O>, OutputRow<I, O>> getInitialState() {
            List<OutputRow<I, O>> initial = this.rows.get(0).getShortRows();
            return Pair.of(initial.get(firstIndex), initial.get(secondIndex));
        }

        @Override
        public @Nullable Pair<Pair<Boolean, OutputRow<I, O>>, Pair<Boolean, OutputRow<I, O>>> getTransition(
                Pair<OutputRow<I, O>, OutputRow<I, O>> state, I input) {
            int inputIndex = inputAlphabet.getSymbolIndex(input);
            OutputRow<I, O> firstSuccessor = state.getFirst().getSuccessor(inputIndex).getShortRow(this.firstIndex);
            Boolean firstTrue = state.getFirst().getOutput(inputIndex).equals(this.firstOutput);
            OutputRow<I, O> secondSuccessor = state.getSecond().getSuccessor(inputIndex).getShortRow(this.secondIndex);
            Boolean secondTrue = state.getSecond().getOutput(inputIndex).equals(this.secondOutput);
            return Pair.of(Pair.of(firstTrue, firstSuccessor), Pair.of(secondTrue, secondSuccessor));
        }

        @Override
        public Void getStateProperty(Pair<OutputRow<I, O>, OutputRow<I, O>> state) {
            return null;
        }

        @Override
        public O getTransitionOutput(Pair<Pair<Boolean, OutputRow<I, O>>, Pair<Boolean, OutputRow<I, O>>> transition) {
            if (transition.getFirst().getFirst()) {
                return this.firstOutput;
            }
            if (transition.getSecond().getFirst()) {
                return this.secondOutput;
            }
            return null;
        }
    }
}
