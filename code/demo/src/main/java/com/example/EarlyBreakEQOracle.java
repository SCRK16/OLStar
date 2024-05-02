package com.example;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.util.automaton.Automata;
import net.automatalib.word.Word;

public class EarlyBreakEQOracle<I, O> implements MealyEquivalenceOracle<I, O> {

    final private MealyMachine<?, I, ?, O> target;
    final private MealyEquivalenceOracle<I, O> delegate;

    public EarlyBreakEQOracle(MealyMachine<?, I, ?, O> target, MealyEquivalenceOracle<I, O> delegate) {
        this.target = target;
        this.delegate = delegate;
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputAlphabet) {
        Word<I> sep = Automata.findSeparatingWord(target, hypothesis, inputAlphabet);
        if (sep == null) {
            return null;
        }
        return delegate.findCounterExample(hypothesis, inputAlphabet);
    }
}
