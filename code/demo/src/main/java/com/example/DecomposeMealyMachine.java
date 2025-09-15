package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.TimeoutException;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.common.util.Triple;

/**
 * Decomposes an observation table into multiple (smaller) components.
 * Uses a weak parallel decomposition.
 * Usage: initialize and then just call {@code decompose}.
 * 
 * @implNote For now, only uses decompositions {f_i : O -> Integer}.
 */
public class DecomposeMealyMachine<O> {//TODO: Change all JavaDoc

    private ISolver solver;
    private Map<String, Integer> variableMap;
    private Map<Integer, Triple<Integer, O, O>> outputMap;
    private Map<Integer, Pair<Integer, Integer>> representativeMap;
    private int resultSize;
    private boolean verbose;

    public DecomposeMealyMachine(boolean verbose) {
        this.solver = SolverFactory.newDefault();
        this.variableMap = new HashMap<>();
        this.outputMap = new HashMap<>();
        this.representativeMap = new HashMap<>();
        this.verbose = verbose;
    }

    /**
     * @return The number of states of the largest component in the decomposition.
     * @implSpec Only returns a valid result size after an observation table has
     *           been decomposed with {@code decompose}.
     */
    public int getResultSize() {
        return this.resultSize;
    }

    /**
     * Set-up function, which creates propositional variables for an observation
     * table
     * 
     * @param <I>   Type of the input alphabet
     * @param <D>   Type of the range of the decomposition {f_i : O -> D}
     * @param table The observation table
     * @param n     The number of components to table should be decomposed into
     */
    private <S, I, T> void setup(MealyMachine<S, I, T, O> machine, List<S> states, Alphabet<O> outputAlphabet, int n) {
        this.variableMap.put("true", this.solver.nextFreeVarId(true));
        this.variableMap.put("false", this.solver.nextFreeVarId(true));
        // output relations
        for (int id = 0; id < n; id++) {
            for (O o1 : outputAlphabet) {
                String o1str = o1.toString();
                for (O o2 : outputAlphabet) {
                    String o2str = o2.toString();
                    if (o1str.compareTo(o2str) < 0) {
                        int varid = this.solver.nextFreeVarId(true);
                        this.variableMap.put("rel" + String.valueOf(id) + ":" + o1str + "=" + o2str, varid);
                        this.outputMap.put(varid, Triple.of(id, o1, o2));
                    }
                }
            }
        }
        // row relations
        for (int id = 0; id < n; id++) {
            for (S s1 : states) {
                String s1str = s1.toString();
                for (S s2 : states) {
                    String s2str = s2.toString();
                    if (s1str.compareTo(s2str) < 0) {
                        this.variableMap.put("state-rel" + String.valueOf(id) + ":" + s1str + "=" + s2str,
                                this.solver.nextFreeVarId(true));
                    }
                }
            }
        }

        // representatives
        for (int id = 0; id < n; id++) {
            for (S s : states) {
                int varid = this.solver.nextFreeVarId(true);
                this.variableMap.put("state-rep" + String.valueOf(id) + ":" + s.toString(), varid);
                this.representativeMap.put(varid, Pair.of(id, s.hashCode()));
            }
        }
    }

    /**
     * Helper function to get the variable for output-relations
     * 
     * @param id Component number
     * @param o1 First output
     * @param o2 Second output
     * @return The variable which states that o1 is related to o2 in component id
     */
    private Integer get_rel(int id, O o1, O o2) {
        String o1str = o1.toString();
        String o2str = o2.toString();
        if (o1str.equals(o2str)) {
            return this.variableMap.get("true");
        } else if (o1str.compareTo(o2str) < 0) {
            return this.variableMap.get("rel" + String.valueOf(id) + ":" + o1str + "=" + o2str);
        } else {
            return this.variableMap.get("rel" + String.valueOf(id) + ":" + o2str + "=" + o1str);
        }
    }

    /**
     * Helper function to get the variable for row-relations
     * 
     * @param <I> Input alphabet type
     * @param id  Component number
     * @param r1  First row
     * @param r2  Second row
     * @return The variable which states that r1 is related to r2 in component id
     */
    private <S> Integer get_state_rel(int id, S s1, S s2) {
        String s1str = s1.toString();
        String s2str = s2.toString();
        if (s1str.equals(s2str)) {
            return this.variableMap.get("true");
        } else if (s1str.compareTo(s2str) < 0) {
            return this.variableMap.get("state-rel" + String.valueOf(id) + ":" + s1str + "=" + s2str);
        } else {
            return this.variableMap.get("state-rel" + String.valueOf(id) + ":" + s2str + "=" + s1str);
        }
    }

    /**
     * Helper function to get the variable for row-representatives
     * 
     * @param <I> Input alphabet type
     * @param id  Component number
     * @param r   Row
     * @return The variable which states that r is a representative for its
     *         equivalence class in component id
     */
    private <S> Integer get_state_rep(int id, S s) {
        return this.variableMap.get("state-rep" + String.valueOf(id) + ":" + s.toString());
    }

    /**
     * Lets the SAT-solver know that that "true" is true and "false" is false
     */
    private void addConstants() {
        try {
            IVecInt clause = new VecInt();
            clause.push(this.variableMap.get("true"));
            this.solver.addClause(clause);
            clause.clear();
            clause.push(-this.variableMap.get("false"));
            this.solver.addClause(clause);
            clause.clear();
        } catch (ContradictionException e) {
            e.printStackTrace();
            System.err
                    .println("This should never happen. If you see this message, something has seriously gone wrong.");
            System.exit(-1);
        }
    }

    /**
     * Lets the SAT-solver know that the output-relations are transitive.
     * 
     * @param outputAlphabet The output alphabet
     * @param n              The number of components
     * @throws ContradictionException Thrown if the output relation cannot be
     *                                transitive
     */
    private void addOutputRelTransitive(Alphabet<O> outputAlphabet, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (O o1 : outputAlphabet) {
                for (O o2 : outputAlphabet) {
                    for (O o3 : outputAlphabet) {
                        clause.push(-this.get_rel(id, o1, o2));
                        clause.push(-this.get_rel(id, o2, o3));
                        clause.push(this.get_rel(id, o1, o3));
                        this.solver.addClause(clause);
                        clause.clear();
                    }
                }
            }
        }
    }

    /**
     * Lets the SAT-solver know that the row-relations are transitive
     * 
     * @param <I>  Input alphabet type
     * @param rows List of rows to be used in the relation
     * @param n    Number of components
     * @throws ContradictionException Thrown if the row relation cannot be transtive
     * @implNote This function does not need to be called. The row relations are
     *           automatically transitive because the output relations are
     *           transitive and outputs and rows are related.
     */
    @SuppressWarnings("unused")
    private <S, I> void addStateRelTransitive(List<S> states, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (S s1 : states) {
                for (S s2 : states) {
                    for (S s3 : states) {
                        clause.push(-this.get_state_rel(id, s1, s2));
                        clause.push(-this.get_state_rel(id, s2, s3));
                        clause.push(this.get_state_rel(id, s1, s3));
                        this.solver.addClause(clause);
                        clause.clear();
                    }
                }
            }
        }
    }

    /**
     * Lets the SAT-solver know that the output relations need to be joint
     * injective.
     * 
     * @param outputAlphabet The output alphabet
     * @param n              The number of components
     * @throws ContradictionException Thrown if the output relations cannot be joint
     *                                injective
     */
    private void addOutputRelInjective(Alphabet<O> outputAlphabet, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (O o1 : outputAlphabet) {
            for (O o2 : outputAlphabet) {
                if (o1.toString().compareTo(o2.toString()) < 0) {
                    for (int id = 0; id < n; id++) {
                        clause.push(-this.get_rel(id, o1, o2));
                    }
                    this.solver.addClause(clause);
                    clause.clear();
                }
            }
        }
    }

    /**
     * Lets the SAT-solver know that the output relations and the row relations are
     * related. To be concrete: All outputs in two rows are related iff the rows
     * themselves are related.
     * 
     * @param <I>            Input alphabet type
     * @param <D>            Type of the range of the decomposition {f_i : O -> D}
     * @param table          Observation table to be decomposed
     * @param outputAlphabet The output alphabet
     * @param rows           List of rows to be used in the relation
     * @param n              The number of components
     * @throws ContradictionException Thrown if outputs and rows cannot be related
     */
    private <S, I, T> void addOutputStateRel(MealyMachine<S, I, T, O> machine, Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet,
            List<S> states, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (S s1 : states) {
                for (S s2 : states) {
                    for (I input : inputAlphabet) {
                        // s1 ~ s2 => lambda(s1, input) ~ lambda(s2, input)
                        O o1 = machine.getOutput(s1, input);
                        O o2 = machine.getOutput(s2, input);
                        clause.push(-this.get_state_rel(id, s1, s2));
                        clause.push(this.get_rel(id, o1, o2));
                        this.solver.addClause(clause);
                        clause.clear();

                        // s1 ~ s2 => delta(s1, input) ~ delta(s2, input)
                        S n1 = machine.getSuccessor(s1, input);
                        S n2 = machine.getSuccessor(s2, input);
                        clause.push(-this.get_state_rel(id, s1, s2));
                        clause.push(this.get_state_rel(id, n1, n2));
                        this.solver.addClause(clause);
                        clause.clear();
                    }
                    // Note: Inverse missing: all outputs and successors equivalent => states equivalent
                }
            }
        }
    }

    /**
     * Lets the SAT-solver know that each equivalence class has exactly one
     * representative. To be concrete:
     * We choose representatives as early in rows as possible. Each element needs to
     * be either a representative, or related to some earlier element in rows. If
     * two elements are related, they cannot both be representatives.
     * 
     * @param <I>  The input alphabet type
     * @param rows List of rows to be used in the relation
     * @param n    The number of components of the decomposition
     * @throws ContradictionException Thrown if representatives cannot be made
     *                                unique
     */
    private <S> void addRepresentativesUnique(List<S> states, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        // Only one representative per relation per equivalence class
        for (int id = 0; id < n; id++) {
            for (int si1 = 0; si1 < states.size(); si1++) {
                S s1 = states.get(si1);
                clause.push(this.get_state_rep(id, s1));
                for (int si2 = 0; si2 < si1; si2++) {
                    S s2 = states.get(si2);
                    clause.push(this.get_state_rel(id, s1, s2));
                }
                this.solver.addClause(clause);
                clause.clear();

                for (int si2 = 0; si2 < si1; si2++) {
                    S s2 = states.get(si2);
                    clause.push(-this.get_state_rep(id, s1));
                    clause.push(-this.get_state_rep(id, s2));
                    clause.push(-this.get_state_rel(id, s1, s2));
                    this.solver.addClause(clause);
                    clause.clear();
                }
            }
        }
    }

    /**
     * Main workhorse. Using binary search, try to find a decomposition with the
     * least number of representatives for the largest component (a weak
     * decomposition). This number, k, should satisfy lower < k <= upper.
     * 
     * @param <I>   The input alphabet type
     * @param rows  List of rows to be used in the relation
     * @param n     The number of components of the decomposition
     * @param lower Lower bound for the size of the largest component, decomposition
     *              should be impossible for this size
     * @param upper Upper bound for the size of the largest component, decomposition
     *              should be possible for this size
     * @throws ContradictionException Thrown if the table could not be decomposed
     *                                into n components where the largest component
     *                                has size k satisfying lower < k <= upper.
     * @throws TimeoutException       Thrown if finding the decomposition takes too
     *                                much time
     */
    private <S> void optimizeRepresentatives(List<S> states, int n, int lower, int upper)
            throws ContradictionException, TimeoutException {
        IVecInt[] clauses = new VecInt[n];
        for (int id = 0; id < n; id++) {
            clauses[id] = new VecInt();
            for (S s : states) {
                clauses[id].push(this.get_state_rep(id, s));
            }
        }
        while (upper - lower > 1) {
            int current = lower + ((upper - lower) >> 1); // (lower+upper)/2, but without integer overflow
            if (this.verbose) {
                System.out.println("current: " + current);
            }
            IConstr[] constrs = new IConstr[n];
            try {
                for (int id = 0; id < n; id++)
                    constrs[id] = this.solver.addAtMost(clauses[id], current);
                if (this.solver.isSatisfiable()) {
                    int representatives_count = this.countRepresentatives(n);
                    System.out.println("Current: " + String.valueOf(current) + ", Real: " + String.valueOf(representatives_count));
                    upper = representatives_count;
                } else {
                    lower = current;
                }
                for (int id = 0; id < n; id++)
                    this.solver.removeConstr(constrs[id]);
            } catch (ContradictionException e) {
                lower = current;
            }
        }
        if (this.verbose) {
            System.out.println("Result: " + upper);
        }
        for (int id = 0; id < n; id++) {
            this.solver.addAtMost(clauses[id], upper);
            clauses[id].clear();
        }
        this.solver.findModel();
        this.resultSize = upper;
    }

    private <S> int isDecomposable(List<S> states, int n, int upper)
            throws ContradictionException, TimeoutException {
        IVecInt[] clauses = new VecInt[n];
        for (int id = 0; id < n; id++) {
            clauses[id] = new VecInt();
            for (S s : states) {
                clauses[id].push(this.get_state_rep(id, s));
            }
        }
        IConstr[] constrs = new IConstr[n];
        try {
            for (int id = 0; id < n; id++)
                constrs[id] = this.solver.addAtMost(clauses[id], upper - 1);
            if (this.solver.isSatisfiable()) {
                int representatives_count = this.countRepresentatives(n);
                System.out.println("Upper: " + String.valueOf(upper));
                System.out.println("Real: " + String.valueOf(representatives_count));
                for (int id = 0; id < n; id++)
                    this.solver.removeConstr(constrs[id]);
                return representatives_count;
            } else {
                this.resultSize = upper;
                return upper;
            }
        } catch (ContradictionException e) {
            this.resultSize = upper;
            return upper;
        }
    }

    /**
     * Decompose an observation table into n components.
     * Tries to find a weak decomposition where
     * the size of the largest component k satisfies lower < k <= upper.
     * 
     * @param <I>   The input alphabet type
     * @param <D>   Type of the range of the decomposition {f_i : O -> D}
     * @param table The observation table
     * @param n     The number of components of the decomposition
     * @param lower Lower bound for the size of the largest component, decomposition
     *              should be impossible for this size
     * @param upper Upper bound for the size of the largest component, decomposition
     *              should be possible for this size
     * @return The decomposition of the observation table into n components
     * @throws ContradictionException Thrown if the table could not be decomposed
     *                                into n components where the largest component
     *                                has size k satisfying lower < k <= upper.
     * @throws TimeoutException       Thrown if finding the decomposition takes too
     *                                much time.
     */
    public <S, I, T> List<Map<O, Integer>> decompose(MealyMachine<S, I, T, O> machine, Alphabet<I> inputAlphabet,
            Alphabet<O> outputAlphabet, int n, int lower, int upper)
            throws ContradictionException, TimeoutException {
        this.solver.reset();
        List<S> states = machine.getStates().stream().collect(Collectors.toList());

        setup(machine, states, outputAlphabet, n);

        this.addConstants();
        this.addOutputRelTransitive(outputAlphabet, n);
        // this.addRowRelTransitive(rows, n); // Not necessary, see documentation for
        // addRowRelTransitive
        this.addOutputRelInjective(outputAlphabet, n);
        this.addOutputStateRel(machine, inputAlphabet, outputAlphabet, states, n);
        this.addRepresentativesUnique(states, n);
        int cur = this.isDecomposable(states, n, upper);
        if (cur < upper) {
            this.optimizeRepresentatives(states, n, lower, cur);
            return convertResultToMaps(outputAlphabet, n);
        } else {
            System.out.println("Machine is not decomposable");
            return null;
        }
    }

    private <I> int countRepresentatives(int n) throws TimeoutException {
        if (!this.solver.isSatisfiable()) {
            return -1;
        }
        int[] representatives = new int[n];
        int[] model = this.solver.findModel();
        for (int i = 0; i < n; i++) {
            representatives[i] = 0;
        }
        for (int var : model) {
            if (var < 0) {
                continue;
            }
            Pair<Integer, Integer> state = this.representativeMap.get(var);
            if (state == null)
                continue;
            representatives[state.getFirst()] += 1;
        }
        int max = 0;
        for (int cur : representatives) {
            if (cur > max)
                max = cur;
        }
        return max;
    }

    /**
     * Converts the variable assignment of the SAT-solver to a list of functions.
     * 
     * @param <I>            The input alphabet type
     * @param <D>            Type of the range of the decomposition {f_i : O -> D}
     * @param outputAlphabet The output alphabet
     * @param n              The number of components of the decomposition
     * @return A list of functions [f_i : O -> D]
     */
    private <I, D> List<Map<O, Integer>> convertResultToMaps(Alphabet<O> outputAlphabet, int n) {
        try {
            if (!this.solver.isSatisfiable()) {
                return null;
            }
        } catch (TimeoutException e) {
            e.printStackTrace();
            System.err
                    .println("This should never happen. If you see this message, something has seriously gone wrong.");

        }
        int[] model = this.solver.model();
        List<Map<O, Integer>> result = new ArrayList<>();
        int[] fresh = new int[n];
        for (int id = 0; id < n; id++) {
            result.add(new HashMap<>());
            fresh[id] = 0;
        }
        for (int var : model) {
            if (var < 0) {
                continue;
            }
            Triple<Integer, O, O> os = this.outputMap.get(var);
            if (os == null) {
                continue;
            }
            Integer id = os.getFirst();
            O o1 = os.getSecond();
            O o2 = os.getThird();
            if (result.get(id).containsKey(o1)) {
                result.get(id).put(o2, result.get(id).get(o1));
            } else if (result.get(id).containsKey(o2)) {
                result.get(id).put(o1, result.get(id).get(o2));
            } else {
                Integer key = fresh[id];
                fresh[id] = key + 1;
                result.get(id).put(o1, key);
                result.get(id).put(o2, key);
            }
        }
        for (int id = 0; id < n; id++) {
            for (O o : outputAlphabet) {
                if (!result.get(id).containsKey(o)) {
                    Integer key = fresh[id];
                    fresh[id] = key + 1;
                    result.get(id).put(o, key);
                }
            }
        }
        return result;
    }
}
