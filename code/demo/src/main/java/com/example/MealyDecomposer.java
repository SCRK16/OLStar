package com.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Lists;

import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.algorithm.LearningAlgorithm;
import de.learnlib.algorithm.ttt.mealy.TTTLearnerMealy;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.Query;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public abstract class MealyDecomposer<I, O> implements LearningAlgorithm.MealyLearner<I, O> {
    final private Alphabet<I> inputAlphabet;

    final Alphabet<O> outputAlphabet;
    final ArrayList<MealyLearner<I, Boolean>> learners;

    public MealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer, Alphabet<O> outputAlphabet, boolean useCache) {
        this.inputAlphabet = inputAlphabet;
        this.outputAlphabet = outputAlphabet;
        this.learners = Lists.newArrayListWithCapacity(outputAlphabet.size());
        if(useCache) {
            MembershipOracle<I, Word<O>> cacheOracle = MealyCaches.createCache(inputAlphabet, mqOracle);
            for(O o : outputAlphabet) {
                this.learners.add(new TTTLearnerMealy<I, Boolean>(inputAlphabet, new OutputOracle(cacheOracle, o), analyzer));
            }
        } else {
            for(O o : outputAlphabet) {
                this.learners.add(new TTTLearnerMealy<I, Boolean>(inputAlphabet, new OutputOracle(mqOracle, o), analyzer));
            }
        }
    }

    public MealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer, Alphabet<O> outputAlphabet) {
        this(inputAlphabet, mqOracle, analyzer, outputAlphabet, true);
    }

    public Alphabet<I> getInputAlphabet() {
        return this.inputAlphabet;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        List<MealyMachine<Object, I, Object, Boolean>> components = Lists.newArrayListWithCapacity(this.learners.size());
        for(MealyLearner<I, Boolean> l : this.learners) {
            components.add((MealyMachine<Object, I, Object, Boolean>) l.getHypothesisModel()); // This cast is fine
        }
        return new RecomposedMealyMachine<>(components, this.inputAlphabet, this.outputAlphabet);
    }

    @Override
    public void startLearning() {
        for(MealyLearner<I, Boolean> learner : learners) {
            learner.startLearning();
        }
    }

    public class OutputOracle implements MembershipOracle<I, Word<Boolean>> {
        final private MembershipOracle<I, Word<O>> delegate;
        final private O output;

        public OutputOracle(MembershipOracle<I, Word<O>> delegate, O output) {
            this.delegate = delegate;
            this.output = output;
        }

        @Override
        public void processQueries(Collection<? extends Query<I, Word<Boolean>>> queries) {
            Collection<WrappedQuery> wrappedQueries = Lists.newArrayListWithCapacity(queries.size());
            for(Query<I, Word<Boolean>> q : queries) {
                wrappedQueries.add(new WrappedQuery(q, this.output));
            }
            delegate.processQueries(wrappedQueries);
        }

        class WrappedQuery extends Query<I, Word<O>> {
            final private Query<I, Word<Boolean>> original;
            final private O wrappedOutput;

            public WrappedQuery(Query<I, Word<Boolean>> original, O output) {
                this.original = original;
                this.wrappedOutput = output;
            }

            @Override
            public void answer(Word<O> output) {
                WordBuilder<Boolean> wb = new WordBuilder<>();
                for(O o : output) {
                    wb.add(this.wrappedOutput.equals(o));
                }
                this.original.answer(wb.toWord());
            }

            @Override
            public Word<I> getPrefix() {
                return this.original.getPrefix();
            }

            @Override
            public Word<I> getSuffix() {
                return this.original.getSuffix();
            }

        }

    }

    public class RecomposedMealyMachine<S, T> implements MealyMachine<List<S>, I, List<T>, O> {

        final List<MealyMachine<S, I, T, Boolean>> components;
        final Alphabet<I> inputAlphabet;
        final Alphabet<O> outputAlphabet;
        private Collection<List<S>> cachedStates;

        public RecomposedMealyMachine(List<MealyMachine<S, I, T, Boolean>> components, Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet) {
            this.components = components;
            this.inputAlphabet = inputAlphabet;
            this.outputAlphabet = outputAlphabet;
        }

        public List<MealyMachine<S, I, T, Boolean>> getComponents() {
            return this.components;
        }

        @Override
        public List<S> getSuccessor(List<T> transitions) {
            List<S> result = Lists.newArrayListWithCapacity(transitions.size());
            for(int i = 0; i < transitions.size(); i++) {
                result.add(components.get(i).getSuccessor(transitions.get(i)));
            }
            return result;
        }

        @Override
        public Collection<List<S>> getStates() {
            if(cachedStates != null) {
                return cachedStates;
            }
            Set<List<S>> reach = new HashSet<>();
            Queue<List<S>> bfsQueue = new ArrayDeque<>();

            List<S> init = getInitialState();

            bfsQueue.add(init);

            List<S> curr;
            while ((curr = bfsQueue.poll()) != null) {
                if(reach.contains(curr)) continue;

                for (I in : this.inputAlphabet) {
                    List<S> succ = getSuccessor(curr, in);
                    if (succ == null) continue;

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
        public List<S> getInitialState() {
            ArrayList<S> result = Lists.newArrayListWithCapacity(components.size());
            for(MealyMachine<S, I, T, Boolean> component : this.components) {
                result.add(component.getInitialState());
            }
            return result;
        }

        @Override
        public List<T> getTransition(List<S> states, I input) {
            ArrayList<T> result = Lists.newArrayListWithCapacity(states.size());
            for(int i = 0; i < states.size(); i++) {
                result.add(components.get(i).getTransition(states.get(i), input));
            }
            return result;
        }

        @Override
        public O getTransitionOutput(List<T> transitions) {
            for(int i = 0; i < transitions.size(); i++) {
                if(components.get(i).getTransitionOutput(transitions.get(i))) {
                    return outputAlphabet.getSymbol(i);
                }
            }
            return null;
        }

        public List<Boolean> getComponentOutputs(List<T> transitions) {
            ArrayList<Boolean> outputs = Lists.newArrayListWithCapacity(transitions.size());
            for(int i = 0; i < transitions.size(); i++) {
                outputs.add(components.get(i).getTransitionOutput(transitions.get(i)));
            }
            return outputs;
        }

    }
}
