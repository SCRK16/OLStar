package com.example;

import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

public class StaticMealyDecomposer<I, O> extends MealyDecomposer<I, O> {

    public StaticMealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer, Alphabet<O> outputAlphabet) {
        super(inputAlphabet, mqOracle, analyzer, outputAlphabet);
    }

    public StaticMealyDecomposer(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle, AcexAnalyzer analyzer, Alphabet<O> outputAlphabet, boolean useCache) {
        super(inputAlphabet, mqOracle, analyzer, outputAlphabet, useCache);
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {
        boolean refined = false;
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
        return refined;
    }

}
