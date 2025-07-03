package com.example;

import java.util.Collection;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automaton.transducer.MealyMachine;

public class MappedMealy<S, I, T, O, D> implements MealyMachine<S, I, T, D> {

    private final MealyMachine<S, I, T, O> delegate;
    private final Map<O, D> outputMap;
    private Collection<S> cachedStates;

    public MappedMealy(MealyMachine<S, I, T, O> delegate, Map<O, D> outputMap) {
        this.delegate = delegate;
        this.outputMap = outputMap;
    }

    @Override
    public S getSuccessor(T t) {
        return this.delegate.getSuccessor(t);
    }

    @Override
    public Collection<S> getStates() {
        if (this.cachedStates == null) {
            this.cachedStates = this.delegate.getStates();
        }
        return this.cachedStates;
    }

    @Override
    public @Nullable S getInitialState() {
        return this.delegate.getInitialState();
    }

    @Override
    public @Nullable T getTransition(S s, I i) {
        return this.delegate.getTransition(s, i);
    }

    @Override
    public Void getStateProperty(S s) {
        return null;
    }

    @Override
    public D getTransitionOutput(T t) {
        O o = this.delegate.getTransitionOutput(t);
        return this.outputMap.get(o);
    }

}
