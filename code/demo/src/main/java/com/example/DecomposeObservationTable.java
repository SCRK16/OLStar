package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.TimeoutException;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.GrowingAlphabet;
import net.automatalib.common.util.Pair;
import net.automatalib.common.util.Triple;
import net.automatalib.word.Word;

/**
 * Decomposes an observation table into multiple (smaller) components.
 * Uses a weak parallel decomposition.
 * Usage: initialize and then just call {@code decompose}.
 * 
 * @implNote For now, only uses decompositions {f_i : O -> Integer}.
 */
public class DecomposeObservationTable<O> {

    private ISolver solver;
    private Map<String, Integer> variableMap;
    private Map<Integer, Triple<Integer, O, O>> outputMap;
    private Map<Integer, Pair<Integer, Integer>> representativeMap; // Map from var to (component, row id)
    private int resultSize;

    public DecomposeObservationTable() {
        this.solver = SolverFactory.newDefault();
        this.variableMap = new HashMap<>();
        this.outputMap = new HashMap<>();
        this.representativeMap = new HashMap<>();
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
    private <I, D> void setup(OutputObservationTable<I, O, D> table, int n) {
        this.variableMap.put("true", this.solver.nextFreeVarId(true));
        this.variableMap.put("false", this.solver.nextFreeVarId(true));
        // output relations
        GrowingAlphabet<O> outputAlphabet = table.getOutputAlphabet();
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
        List<OutputRow<I, O>> rows = table.getShortPrefixRows();
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r1 : rows) {
                String r1str = r1.toString();
                for (OutputRow<I, O> r2 : rows) {
                    String r2str = r2.toString();
                    if (r1str.compareTo(r2str) < 0) {
                        this.variableMap.put("row-rel" + String.valueOf(id) + ":" + r1str + "=" + r2str,
                                this.solver.nextFreeVarId(true));
                    }
                }
            }
        }

        // representatives
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r : rows) {
                int varid = this.solver.nextFreeVarId(true);
                this.variableMap.put("row-rep" + String.valueOf(id) + ":" + r.toString(), varid);
                this.representativeMap.put(varid, Pair.of(id, r.getRowId()));
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
    private <I> Integer get_row_rel(int id, OutputRow<I, O> r1, OutputRow<I, O> r2) {
        String r1str = r1.toString();
        String r2str = r2.toString();
        if (r1str.equals(r2str)) {
            return this.variableMap.get("true");
        } else if (r1str.compareTo(r2str) < 0) {
            return this.variableMap.get("row-rel" + String.valueOf(id) + ":" + r1str + "=" + r2str);
        } else {
            return this.variableMap.get("row-rel" + String.valueOf(id) + ":" + r2str + "=" + r1str);
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
    private <I> Integer get_row_rep(int id, OutputRow<I, O> r) {
        return this.variableMap.get("row-rep" + String.valueOf(id) + ":" + r.toString());
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
    private <I> void addRowRelTransitive(List<OutputRow<I, O>> rows, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r1 : rows) {
                for (OutputRow<I, O> r2 : rows) {
                    for (OutputRow<I, O> r3 : rows) {
                        clause.push(-this.get_row_rel(id, r1, r2));
                        clause.push(-this.get_row_rel(id, r2, r3));
                        clause.push(this.get_row_rel(id, r1, r3));
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
     * Helper function which transforms a list of words into a list of output
     * symbols
     * 
     * @param words List of words
     * @return List of output symbols in the words
     */
    private List<O> flattenWordList(List<Word<O>> words) {
        List<O> result = new ArrayList<>();
        for (Word<O> w : words) {
            for (O o : w) {
                result.add(o);
            }
        }
        return result;
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
    private <I, D> void addOutputRowRel(OutputObservationTable<I, O, D> table, Alphabet<O> outputAlphabet,
            List<OutputRow<I, O>> rows, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r1 : rows) {
                for (OutputRow<I, O> r2 : rows) {
                    // outputs equivalent => row equivalent
                    clause.push(this.get_row_rel(id, r1, r2));
                    List<O> r1os = this.flattenWordList(table.getRowContents(r1));
                    List<O> r2os = this.flattenWordList(table.getRowContents(r2));
                    for (int i = 0; i < r1os.size(); i++) {
                        clause.push(-this.get_rel(id, r1os.get(i), r2os.get(i)));
                    }
                    this.solver.addClause(clause);
                    clause.clear();

                    // row equivalent => outputs equivalent
                    for (int i = 0; i < r1os.size(); i++) {
                        clause.push(-this.get_row_rel(id, r1, r2));
                        clause.push(this.get_rel(id, r1os.get(i), r2os.get(i)));
                        this.solver.addClause(clause);
                        clause.clear();
                    }
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
    private <I> void addRepresentativesUnique(List<OutputRow<I, O>> rows, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        // Only one representative per relation per equivalence class
        for (int id = 0; id < n; id++) {
            for (int ri1 = 0; ri1 < rows.size(); ri1++) {
                OutputRow<I, O> r1 = rows.get(ri1);
                clause.push(this.get_row_rep(id, r1));
                for (int ri2 = 0; ri2 < ri1; ri2++) {
                    OutputRow<I, O> r2 = rows.get(ri2);
                    clause.push(this.get_row_rel(id, r1, r2));
                }
                this.solver.addClause(clause);
                clause.clear();

                for (int ri2 = 0; ri2 < ri1; ri2++) {
                    OutputRow<I, O> r2 = rows.get(ri2);
                    clause.push(-this.get_row_rep(id, r1));
                    clause.push(-this.get_row_rep(id, r2));
                    clause.push(-this.get_row_rel(id, r1, r2));
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
    private <I> void optimizeRepresentatives(List<OutputRow<I, O>> rows, int n, int lower, int upper)
            throws ContradictionException, TimeoutException {
        IVecInt[] clauses = new VecInt[n];
        for (int id = 0; id < n; id++) {
            clauses[id] = new VecInt();
            for (OutputRow<I, O> r : rows) {
                clauses[id].push(this.get_row_rep(id, r));
            }
        }
        while (upper - lower > 1) {
            int current = lower + ((upper - lower) >> 1); // (lower+upper)/2, but without integer overflow
            System.out.println("current: " + current);
            IConstr[] constrs = new IConstr[n];
            try {
                for (int id = 0; id < n; id++)
                    constrs[id] = this.solver.addAtMost(clauses[id], current);
                if (this.solver.isSatisfiable()) {
                    upper = current;
                } else {
                    lower = current;
                }
                for (int id = 0; id < n; id++)
                    this.solver.removeConstr(constrs[id]);
            } catch (ContradictionException e) {
                lower = current;
            }
        }
        System.out.println("upper: " + upper);
        for (int id = 0; id < n; id++) {
            this.solver.addAtMost(clauses[id], upper);
            clauses[id].clear();
        }
        this.solver.findModel();
        this.resultSize = upper;
    }

    private <I> boolean isDecomposable(List<OutputRow<I, O>> rows, int n, int upper)
            throws ContradictionException, TimeoutException {
        IVecInt[] clauses = new VecInt[n];
        for (int id = 0; id < n; id++) {
            clauses[id] = new VecInt();
            for (OutputRow<I, O> r : rows) {
                clauses[id].push(this.get_row_rep(id, r));
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
                return true;
            } else {
                this.resultSize = upper;
                return false;
            }
        } catch (ContradictionException e) {
            this.resultSize = upper;
            return false;
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
    public <I, D> List<Map<O, Integer>> decompose(OutputObservationTable<I, O, D> table, int n, int lower, int upper)
            throws ContradictionException, TimeoutException {
        this.solver.reset();
        setup(table, n);

        GrowingAlphabet<O> outputAlphabet = table.getOutputAlphabet();
        List<OutputRow<I, O>> rows = table.getShortPrefixRows();

        this.addConstants();
        this.addOutputRelTransitive(outputAlphabet, n);
        // this.addRowRelTransitive(rows, n); // Not necessary, see documentation for
        // addRowRelTransitive
        this.addOutputRelInjective(outputAlphabet, n);
        this.addOutputRowRel(table, outputAlphabet, rows, n);
        this.addRepresentativesUnique(rows, n);
        if (this.isDecomposable(rows, n, upper)) {
            this.optimizeRepresentatives(rows, n, lower, upper);
            return convertResultToMaps(outputAlphabet, n);
        } else {
            System.out.println("Table is not decomposable");
            return null;
        }
    }

    private int countRepresentatives(int n) throws TimeoutException {
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
            Pair<Integer, Integer> row = this.representativeMap.get(var);
            representatives[row.getFirst()] += 1;
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
