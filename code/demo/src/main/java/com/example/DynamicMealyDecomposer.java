package com.example;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.GrowingAlphabet;
import net.automatalib.alphabet.GrowingMapAlphabet;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class DynamicMealyDecomposer<I, O> extends MealyDecomposer<I, O> {
    private final MembershipOracle<I, Word<O>> mqOracle;
    private final AcexAnalyzer analyzer;

    public DynamicMealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer) {
        this(inputAlphabet, mqOracle, analyzer, new GrowingMapAlphabet<>());
    }

    public DynamicMealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle,
            AcexAnalyzer analyzer, GrowingAlphabet<O> outputAlphabet) {
        super(inputAlphabet, mqOracle, analyzer, outputAlphabet, false);
        this.mqOracle = mqOracle;
        this.analyzer = analyzer;
    }

    public static <I, O> DynamicMealyDecomposer<I, O> createDynamicMealyDecomposerWithCache(Alphabet<I> inputAlphabet,
            MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer, GrowingAlphabet<O> outputAlphabet) {
        MembershipOracle<I, Word<O>> cacheOracle = MealyCaches.createCache(inputAlphabet, mqOracle);
        return new DynamicMealyDecomposer<>(inputAlphabet, cacheOracle, analyzer, outputAlphabet);
    }

    public static <I, O> DynamicMealyDecomposer<I, O> createDynamicMealyDecomposerWithCache(Alphabet<I> inputAlphabet,
            MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer) {
        MembershipOracle<I, Word<O>> cacheOracle = MealyCaches.createCache(inputAlphabet, mqOracle);
        return new DynamicMealyDecomposer<>(inputAlphabet, cacheOracle, analyzer);
    }

    @Override
    public void startLearning() {
        for(MealyLearner<I, Boolean> learner : learners) {
            learner.startLearning();
        }
        this.fixReachableDefects();
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {
        return this.refineHypothesis(ce, true);
    }

    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce, boolean fixDefects) {
        boolean refined = false;
        for(O o : ce.getOutput()) {
            if(!this.outputAlphabet.contains(o)) {
                TTTLearnerMealy<I, Boolean> learner = new TTTLearnerMealy<>(this.getInputAlphabet(),
                    new OutputOracle(this.mqOracle, o), this.analyzer);
                learner.startLearning();
                this.learners.add(learner);
                this.outputAlphabet.add(o);
                refined = true;
            }
        }
        for(int i = 0; i < this.learners.size(); i++) {
            WordBuilder<Boolean> wb = new WordBuilder<>();
            for(O o : ce.getOutput()) {
                wb.add(this.outputAlphabet.getSymbol(i).equals(o));
            }
            DefaultQuery<I, Word<Boolean>> query = new DefaultQuery<I, Word<Boolean>>(
                ce.getPrefix(),
                ce.getSuffix(),
                wb.toWord());
            boolean r = this.learners.get(i).refineHypothesis(query);
            refined |= r;
        }
        if(fixDefects && refined) {
            fixReachableDefects();
        }
        return refined;
    }

    @SuppressWarnings("unchecked")
    private DefaultQuery<I, Word<O>> findReachableDefect() {
        /*
         * We try to find a defect without doing equivalence queries by doing a reachability analysis.
         * For each transitions, there must be exactly one component machine that outputs true.
         * If there are 0 or >= 2, then there is at least one component for which we have found a counterexample.
         */
        RecomposedMealyMachine<Object, Object> hypothesis = (RecomposedMealyMachine<Object, Object>) this.getHypothesisModel();

        Set<List<Object>> reach = new HashSet<>();
        Queue<List<Object>> bfsQueue = new ArrayDeque<>();
        List<Object> init = hypothesis.getInitialState();
        bfsQueue.add(init);
        Queue<WordBuilder<I>> accessSequences = new ArrayDeque<>();
        accessSequences.add(new WordBuilder<>());
        List<Object> curr;
        while ((curr = bfsQueue.poll()) != null) {
            WordBuilder<I> wb = accessSequences.poll();
            if(reach.contains(curr)) continue;

            for (I in : this.getInputAlphabet()) {
                WordBuilder<I> wbin = new WordBuilder<>(wb.toWord());
                wbin.add(in);
                List<Object> transition = hypothesis.getTransition(curr, in);
                List<Boolean> outputs = hypothesis.getComponentOutputs(transition);
                long trueCount = Collections.frequency(outputs, true);
                if(trueCount != 1) {// We have found a defect
                    Word<I> w = wbin.toWord();
                    DefaultQuery<I, Word<O>> ce = new DefaultQuery<>(w);
                    ce.answer(this.mqOracle.answerQuery(w));
                    return ce;
                }
                List<Object> succ = hypothesis.getSuccessor(transition);
                if (succ == null) continue;

                if (!reach.contains(succ)) {
                    bfsQueue.add(succ);
                    accessSequences.add(wbin);
                }
            }
            reach.add(curr);
        }
        return null;
    }

    private <S, T> void fixReachableDefects() {
        DefaultQuery<I, Word<O>> ce = this.findReachableDefect();
        while(ce != null) {
            this.refineHypothesis(ce, false);
            ce = this.findReachableDefect();
        }
    }
}
