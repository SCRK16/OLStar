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
import net.automatalib.common.util.Triple;

public class DecomposeObservationTable<O> {

    private ISolver solver;
    private Map<String, Integer> variableMap;
    private Map<Integer, Triple<Integer, O, O>> outputMap;
    private int resultSize;

    public DecomposeObservationTable() {
        this.solver = SolverFactory.newDefault();
        //this.solver.getOrder().setPhaseSelectionStrategy(new PositiveLiteralSelectionStrategy());
        this.variableMap = new HashMap<>();
        this.outputMap = new HashMap<>();
    }

    public int getResultSize() {
        return this.resultSize;
    }

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
        List<OutputRow<I, O>> rows = table.getAllRows();
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
                this.variableMap.put("row-rep" + String.valueOf(id) + ":" + r.toString(),
                        this.solver.nextFreeVarId(true));
            }
        }
    }

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

    private <I> Integer get_row_rep(int id, OutputRow<I, O> r) {
        return this.variableMap.get("row-rep" + String.valueOf(id) + ":" + r.toString());
    }

    private void addConstants() throws ContradictionException {
        IVecInt clause = new VecInt();
        clause.push(this.variableMap.get("true"));
        this.solver.addClause(clause);
        clause.clear();
        clause.push(-this.variableMap.get("false"));
        this.solver.addClause(clause);
        clause.clear();
    }

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

    private <I> void addOutputRowRel(Alphabet<O> outputAlphabet, List<OutputRow<I, O>> rows, int n) throws ContradictionException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r1 : rows) {
                for (OutputRow<I, O> r2 : rows) {
                    // outputs equivalent => row equivalent
                    clause.push(this.get_row_rel(id, r1, r2));
                    List<O> r1os = r1.getOutputs();
                    List<O> r2os = r2.getOutputs();
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

    private <I> void optimizeRepresentatives(List<OutputRow<I, O>> rows, int n, int lower, int upper) throws ContradictionException, TimeoutException {
        IVecInt clause = new VecInt();
        for (int id = 0; id < n; id++) {
            for (OutputRow<I, O> r : rows) {
                clause.push(this.get_row_rep(id, r));
            }
        }
        while (upper - lower > 1) {
            int current = lower + ((upper - lower) >> 1); // (lower+upper)/2
            System.out.println("current: " + current);
            try {
                IConstr constr = this.solver.addAtMost(clause, current);
                this.solver.findModel();
                if(this.solver.isSatisfiable()) {
                    upper = current;
                } else {
                    lower = current;
                }
                this.solver.removeConstr(constr);
            } catch (ContradictionException e) {
                lower = current;
            }
        }
        this.solver.addAtMost(clause, upper);
        this.solver.findModel();
        clause.clear();
        this.resultSize = upper;
    }

    public <I, D> List<Map<O, Integer>> decompose(OutputObservationTable<I, O, D> table, int n, int lower)
            throws ContradictionException, TimeoutException {
        this.solver.reset();
        setup(table, n);

        GrowingAlphabet<O> outputAlphabet = table.getOutputAlphabet();
        List<OutputRow<I, O>> rows = table.getAllRows();

        this.addConstants();
        this.addOutputRelTransitive(outputAlphabet, n);
        //this.addRowRelTransitive(rows, n);
        this.addOutputRelInjective(outputAlphabet, n);
        this.addOutputRowRel(outputAlphabet, rows, n);
        this.addRepresentativesUnique(rows, n);
        this.optimizeRepresentatives(rows, n, lower, table.getAllRows().size() + n - 1);
        return convertResultToSupplier(outputAlphabet, n);
    }

    private <I, D> List<Map<O, Integer>> convertResultToSupplier(Alphabet<O> outputAlphabet, int n) throws TimeoutException {
        int[] model = this.solver.model();
        if (!this.solver.isSatisfiable()) {
            return null;
        }
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
            if(result.get(id).containsKey(o1)) {
                result.get(id).put(o2, result.get(id).get(o1));
            } else if(result.get(id).containsKey(o2)) {
                result.get(id).put(o1, result.get(id).get(o2));
            } else {
                Integer key = fresh[id];
                fresh[id] = key + 1;
                result.get(id).put(o1, key);
                result.get(id).put(o2, key);
            }
        }
        for(int id = 0; id < n; id++) {
            for(O o : outputAlphabet) {
                if(!result.get(id).containsKey(o)) {
                    Integer key = fresh[id];
                    fresh[id] = key + 1;
                    result.get(id).put(o, key);
                }
            }
        }
        return result;
    }
}
