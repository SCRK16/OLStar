package com.example;

import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import net.automatalib.automaton.concept.SuffixOutput;
import net.automatalib.word.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SampleSetEQOracle<A extends SuffixOutput<I, D>, I, D> implements EquivalenceOracle<A, I, D> {
   private final boolean removeUnsuccessful;
   private final List<DefaultQuery<I, D>> testQueries;

   public SampleSetEQOracle(boolean removeUnsuccessful) {
      this.removeUnsuccessful = removeUnsuccessful;
      if (!removeUnsuccessful) {
         this.testQueries = new ArrayList<>();
      } else {
         this.testQueries = new LinkedList<>();
      }

   }

   public SampleSetEQOracle<A, I, D> add(Word<I> input, D expectedOutput) {
      this.testQueries.add(new DefaultQuery<>(input, expectedOutput));
      return this;
   }

   @SafeVarargs
   public final SampleSetEQOracle<A, I, D> addAll(MembershipOracle<I, D> oracle, Word<I>... words) {
      return this.addAll(oracle, Arrays.asList(words));
   }

   public SampleSetEQOracle<A, I, D> addAll(MembershipOracle<I, D> oracle, Collection<? extends Word<I>> words) {
      if (words.isEmpty()) {
         return this;
      } else {
         List<DefaultQuery<I, D>> newQueries = new ArrayList<>(words.size());
         Iterator<? extends Word<I>> var4 = words.iterator();

         while(var4.hasNext()) {
            Word<I> w = var4.next();
            newQueries.add(new DefaultQuery<>(w));
         }

         oracle.processQueries(newQueries);
         this.testQueries.addAll(newQueries);
         return this;
      }
   }

   @SafeVarargs
   public final SampleSetEQOracle<A, I, D> addAll(DefaultQuery<I, D>... newTestQueries) {
      return this.addAll(Arrays.asList(newTestQueries));
   }

   public SampleSetEQOracle<A, I, D> addAll(Collection<? extends DefaultQuery<I, D>> newTestQueries) {
      this.testQueries.addAll(newTestQueries);
      return this;
   }

   public @Nullable DefaultQuery<I, D> findCounterExample(A hypothesis, Collection<? extends I> inputs) {
      Iterator<DefaultQuery<I, D>> queryIt = this.testQueries.iterator();

      while(queryIt.hasNext()) {
         DefaultQuery<I, D> query = queryIt.next();
         if (checkInputs(query, inputs)) {
            if (!test(query, hypothesis)) {
               System.out.println("Predetermined Counterexample found");
               return query;
            }

            if (this.removeUnsuccessful) {
               queryIt.remove();
            }
         }
      }

      return null;
   }

   private static <I> boolean checkInputs(Query<I, ?> query, Collection<? extends I> inputs) {
      Iterator var2 = query.getPrefix().iterator();

      Object sym;
      do {
         if (!var2.hasNext()) {
            var2 = query.getSuffix().iterator();

            do {
               if (!var2.hasNext()) {
                  return true;
               }

               sym = var2.next();
            } while(inputs.contains(sym));

            return false;
         }

         sym = var2.next();
      } while(inputs.contains(sym));

      return false;
   }

   private static <I, D> boolean test(DefaultQuery<I, D> query, SuffixOutput<I, D> hypOutput) {
      D hypOut = hypOutput.computeSuffixOutput(query.getPrefix(), query.getSuffix());
      return Objects.equals(hypOut, query.getOutput());
   }
}
