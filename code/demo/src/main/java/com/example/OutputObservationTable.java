package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.GrowingAlphabet;
import net.automatalib.alphabet.GrowingMapAlphabet;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

/**
 * Observation table used by OL*.
 */
public class OutputObservationTable<I, O, D> {

    private final Alphabet<I> inputAlphabet;
    private final GrowingAlphabet<O> outputAlphabet;

    private final MembershipOracle<I, Word<O>> mqOracle;

    private List<Map<O, D>> outputMaps = new ArrayList<>();

    private final List<OutputRow<I, O>> shortPrefixRows = new ArrayList<>();
    private final List<OutputRow<I, O>> longPrefixRows = new ArrayList<>();
    private final List<OutputRow<I, O>> allRows = new ArrayList<>();
    private final Map<List<Word<O>>, Integer> rowContentIds = new HashMap<>();
    /**
     * For every output map, maps the contents of a row for that output map to
     * the ids of the short prefix rows with those contents
     */
    private List<Map<List<Word<D>>, List<Integer>>> outputContentIds = new ArrayList<>();

    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixSet = new HashSet<>();
    private ArrayList<ArrayList<Word<O>>> table = new ArrayList<>();

    public OutputObservationTable(Alphabet<I> inputAlphabet, MembershipOracle<I, Word<O>> mqOracle) {
        this.inputAlphabet = inputAlphabet;
        this.outputAlphabet = new GrowingMapAlphabet<>();
        this.mqOracle = mqOracle;
    }

    /**
     * Initialize the observation table with initial prefixes and suffixes.
     * The first prefix in the list should be the empty word.
     * The first suffixes in the list should be the input alphabet symbols.
     *
     * @param prefixes The initial prefixes
     * @param suffixes The initial suffixes
     */
    public void initialize(List<Word<I>> prefixes, List<Word<I>> suffixes) {
        assertInitializeValid(prefixes, suffixes);

        for (Word<I> suffix : suffixes) {
            if (this.suffixSet.add(suffix)) {
                this.suffixes.add(suffix);
            }
        }

        int initialCapacity = (this.inputAlphabet.size() + 1) * prefixes.size() * suffixes.size();
        List<DefaultQuery<I, Word<O>>> queries = Lists.newArrayListWithCapacity(initialCapacity);

        HashMap<Word<I>, OutputRow<I, O>> rowMap = new HashMap<>();
        // Pass 1: Add short prefixes
        for (Word<I> prefix : prefixes) {
            OutputRow<I, O> spRow = createSpRow(prefix);
            buildQueries(queries, prefix, suffixes);
            rowMap.put(prefix, spRow);
        }
        // Pass 2: Add long prefixes
        for (OutputRow<I, O> spRow : this.shortPrefixRows) {
            Word<I> sp = spRow.getLabel();
            for (int i = 0; i < this.inputAlphabet.size(); i++) {
                I sym = this.inputAlphabet.getSymbol(i);
                Word<I> lp = sp.append(sym);
                OutputRow<I, O> succRow = rowMap.get(lp);
                if (succRow == null) {
                    succRow = createLpRow(lp);
                    buildQueries(queries, lp, suffixes);
                }
                spRow.setSuccessor(i, succRow);
            }
        }

        this.mqOracle.processQueries(queries);
        this.growOutputAlphabet(queries);

        Iterator<DefaultQuery<I, Word<O>>> queryIt = queries.iterator();

        for (OutputRow<I, O> spRow : this.shortPrefixRows) {
            List<Word<O>> rowContents = new ArrayList<>(suffixes.size());
            this.fetchResults(queryIt, rowContents);
            this.processContents(spRow, rowContents);
        }

        for (OutputRow<I, O> lpRow : this.longPrefixRows) {
            List<Word<O>> rowContents = new ArrayList<>(suffixes.size());
            this.fetchResults(queryIt, rowContents);
            this.processContents(lpRow, rowContents);
        }
    }

    private void assertInitializeValid(List<Word<I>> prefixes, List<Word<I>> suffixes) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but the table was already initialized");
        }
        if (!prefixes.get(0).isEmpty()) {
            throw new IllegalArgumentException(
                    "First prefix when calling 'OutputObservationTable.initialize' should be the empty word");
        }
        for (int i = 0; i < this.inputAlphabet.size(); i++) {
            if (suffixes.get(i).length() != 1
                    || !suffixes.get(i).firstSymbol().equals(this.inputAlphabet.getSymbol(i))) {
                throw new IllegalArgumentException(
                        "First suffixes when calling 'OutputObservationTable.initialize' should be the symbols in the input alphabet");
            }
        }
    }

    public void setOutputMaps(List<Map<O, D>> outputMaps) {
        this.outputMaps = outputMaps;
        this.outputContentIds = new ArrayList<>(this.outputMaps.size());
        for (int i = 0; i < this.outputMaps.size(); i++) {
            this.outputContentIds.add(new HashMap<>());
        }
        for (OutputRow<I, O> row : this.allRows) {
            row.resetShortRows();
        }
    }

    public List<Map<O, D>> getOutputMaps() {
        return this.outputMaps;
    }

    /**
     * Check the list of queries for previously unseen output symbols, and add them
     * to the output alphabet
     *
     * @param queries The list of queries in which the new output symbols can be
     *                found
     * @return {@code true} if the output alphabet grew, {@code false} otherwise
     */
    private boolean growOutputAlphabet(List<DefaultQuery<I, Word<O>>> queries) {
        int outputAlphabetSize = this.outputAlphabet.size();
        for (DefaultQuery<I, Word<O>> query : queries) {
            for (O symbol : query.getOutput()) {
                if (!this.outputAlphabet.containsSymbol(symbol)) {
                    this.outputAlphabet.add(symbol);
                }
            }
        }
        return this.outputAlphabet.size() > outputAlphabetSize;
    }

    /**
     * Add the rowContents to the table and
     * set the outputs of row for the transitions
     * If the row is short, add it to the outputContentIds map
     *
     * @param row         The row for the contents
     * @param rowContents The contents of the row
     */
    private void processContents(OutputRow<I, O> row, List<Word<O>> rowContents) {
        table.set(row.getRowId(), new ArrayList<>(rowContents));
        List<O> outputContents = rowContents.stream().limit(inputAlphabet.size()).map(Word::lastSymbol).toList();
        row.setOutputs(outputContents);
        if (row.isShortPrefixRow()) {
            this.updateContentIds(row, rowContents, 0);
        }
    }

    /**
     * Transforms a list of words words to a list of boolean words,
     * where a character is set to true if it is equal to the specified output
     *
     * @param rowContents The row to be transformed
     * @param output      The output to be compared to
     * @return The transformed list of boolean words
     */
    private List<Word<D>> toOutputWords(List<Word<O>> rowContents, Map<O, D> map) {
        List<Word<D>> outputRowContents = new ArrayList<>(rowContents.size());
        for (Word<O> word : rowContents) {
            WordBuilder<D> wb = new WordBuilder<>();
            for (O current : word) {
                wb.add(map.get(current));
            }
            outputRowContents.add(wb.toWord());
        }
        return outputRowContents;
    }

    private void fetchResults(Iterator<DefaultQuery<I, Word<O>>> queryIt, List<Word<O>> rowContents) {
        for (int i = 0; i < suffixes.size(); i++) {
            rowContents.add(queryIt.next().getOutput());
        }
    }

    public Alphabet<I> getInputAlphabet() {
        return this.inputAlphabet;
    }

    public GrowingAlphabet<O> getOutputAlphabet() {
        return outputAlphabet;
    }

    public List<OutputRow<I, O>> getLongPrefixRows() {
        return Collections.unmodifiableList(this.longPrefixRows);
    }

    public Word<I> getRow(int index) {
        return this.allRows.get(index).getLabel();
    }

    public List<OutputRow<I, O>> getShortPrefixRows() {
        return Collections.unmodifiableList(this.shortPrefixRows);
    }

    public List<Word<I>> getSuffixes() {
        return this.suffixes;
    }

    private OutputRow<I, O> createSpRow(Word<I> word) {
        OutputRow<I, O> row = new OutputRow<>(word, allRows.size());
        row.makeShort(this.inputAlphabet.size());
        this.allRows.add(row);
        this.shortPrefixRows.add(row);
        this.table.add(new ArrayList<>(suffixes.size()));
        return row;
    }

    private OutputRow<I, O> createLpRow(Word<I> lp) {
        OutputRow<I, O> row = new OutputRow<>(lp, allRows.size());
        for (int i = 0; i < suffixes.size(); i++) {
            row.addShortRow(null);
        }
        this.allRows.add(row);
        this.longPrefixRows.add(row);
        this.table.add(new ArrayList<>(suffixes.size()));
        return row;
    }

    private void buildQueries(List<DefaultQuery<I, Word<O>>> queries, Word<I> prefix, List<Word<I>> suffixes) {
        for (Word<I> suffix : suffixes) {
            queries.add(new DefaultQuery<>(prefix, suffix));
        }
    }

    public boolean addSuffix(Word<I> suffix) {
        return addSuffixes(Collections.singletonList(suffix));
    }

    /**
     * Add new suffixes to the observation table. Also updates the content id maps
     * and the output alphabet.
     *
     * @param suffixes List of suffixes to be added
     * @return {@code true} if the list contained a new suffix
     */
    public boolean addSuffixes(List<Word<I>> suffixes) {
        int suffixesCount = this.suffixes.size();
        List<Word<I>> suffixesToAdd = new ArrayList<>();
        for (Word<I> suffix : suffixes) {
            if (this.suffixSet.contains(suffix)) {
                continue;
            }
            this.suffixSet.add(suffix);
            this.suffixes.add(suffix);
            suffixesToAdd.add(suffix);
        }
        List<DefaultQuery<I, Word<O>>> queries = new ArrayList<>();
        for (int i = 0; i < allRows.size(); i++) {
            OutputRow<I, O> row = allRows.get(i);
            List<Word<O>> tableRow = table.get(i);
            for (Word<I> suffix : suffixesToAdd) {
                DefaultQuery<I, Word<O>> query = new DefaultQuery<>(row.getLabel(), suffix);
                mqOracle.processQuery(query);
                queries.add(query);
                tableRow.add(query.getOutput());
            }
            if (row.isShortPrefixRow()) {
                updateContentIds(row, tableRow, suffixesCount);
            }
        }
        this.growOutputAlphabet(queries);
        return this.suffixes.size() > suffixesCount;
    }

    private void updateContentIds(OutputRow<I, O> row, List<Word<O>> rowContents, int oldCount) {
        List<Word<O>> previousRowContents = rowContents.subList(0, oldCount);
        this.rowContentIds.remove(previousRowContents);
        this.rowContentIds.putIfAbsent(rowContents, row.getRowId());
    }

    /**
     * Update the {@code outputContentIds} with the new suffixes. After executing
     * this function, {@code outputContentIds} will map {@code rowContents} to the
     * same integer that {@code rowContents.subList(0, oldCount)} was mapped to.
     *
     * @param rowContents The row contents after adding the suffixes
     * @param oldCount    The old number of suffixes
     * @implNote Assumes that the entries for the new suffixes were appended to the
     *           end of the row contents, leaving the previous contents unmodified
     *           (so the suffixes are sorted by when they are added, not
     *           lexicographically)
     */
    private void updateOutputContentIds() {
        this.outputContentIds = new ArrayList<>(this.outputMaps.size());
        for (int i = 0; i < this.outputMaps.size(); i++) {
            Map<List<Word<D>>, List<Integer>> iMap = new HashMap<>();
            Map<O, D> outputMap = this.outputMaps.get(i);
            for (OutputRow<I, O> curRow : this.shortPrefixRows) {
                int curRowId = curRow.getRowId();
                List<Word<O>> curRowContents = this.table.get(curRowId);
                List<Word<D>> curOutputContents = this.toOutputWords(curRowContents, outputMap);
                List<Integer> outputIds = iMap.getOrDefault(curOutputContents, new ArrayList<>());
                iMap.remove(outputIds);
                outputIds.add(curRowId);
                iMap.put(curOutputContents, outputIds);
            }
            this.outputContentIds.add(iMap);
        }
    }

    /**
     * Moves a row from the long prefix rows to the short prefix rows.
     * As a result, its successors are created as long prefix rows.
     *
     * @param newShortRow The row to be made short
     */
    public void makeShort(OutputRow<I, O> newShortRow) {
        this.longPrefixRows.remove(newShortRow);
        this.shortPrefixRows.add(newShortRow);
        newShortRow.makeShort(this.inputAlphabet.size());
        List<Word<O>> rowContents = this.table.get(newShortRow.getRowId());
        this.rowContentIds.putIfAbsent(rowContents, newShortRow.getRowId());
        for (int i = 0; i < inputAlphabet.size(); i++) { // Create new long prefix rows
            I sym = inputAlphabet.getSymbol(i);
            Word<I> lp = newShortRow.getLabel().append(sym);
            OutputRow<I, O> lpRow = createLpRow(lp);
            newShortRow.setSuccessor(i, lpRow);
            List<DefaultQuery<I, Word<O>>> queries = new ArrayList<>(suffixes.size());
            this.buildQueries(queries, lp, suffixes);
            mqOracle.processQueries(queries);
            this.growOutputAlphabet(queries);
            List<Word<O>> lpRowContents = queries.stream().map(DefaultQuery::getOutput).toList();
            this.processContents(lpRow, lpRowContents);
        }
    }

    /**
     * Check if the table is closed without projecting on outputs.
     * If yes, we can skip checking if it is closed for every output.
     *
     * @return True if and only if the table is closed
     */
    public boolean isRegularClosed() {
        for (OutputRow<I, O> row : this.allRows) {
            List<Word<O>> rowContents = this.table.get(row.getRowId());
            Integer contentId = this.rowContentIds.get(rowContents);
            if (contentId == null) {
                return false;
            }
            OutputRow<I, O> shortRow = this.allRows.get(contentId);
            row.setShortRow(0, shortRow);
        }
        System.out.println("Regular closed");
        return true;
    }

    /**
     * Computes the output-unclosed rows in the table.
     * <p>
     * For every long prefix row and every output map, checks if there is a short
     * prefix row that is equal to the long prefix row under the projection of
     * the output map.
     * If there is, then it stores that short prefix row in the long prefix row.
     * If there is not, then the long prefix row is output-unclosed.
     * <p>
     * The output-unclosed rows are added to a list of lists. The rows in each list
     * all have the same behaviour for some output map. Making any one of these rows
     * a short prefix row will solve the closedness defect.
     * <p>
     * Note that a long prefix row may be in multiple equivalence classes: one for
     * every output symbol in the output alphabet. In this case, adding it to the
     * short prefix rows solves multiple closedness defects at once.
     * <p>
     * We also check for short prefix rows which rows they are equal to for every
     * output. This is because some short prefix rows are actually equal to other
     * short prefix rows for certain outputs, only being their own row for some
     * outputs. Checking this eliminates duplicate rows.
     *
     * @return A list of equivalence classes of unclosed rows, or an empty list if
     *         there are no unclosed rows
     */
    public List<List<OutputRow<I, O>>> findUnclosedRows() {
        this.updateOutputContentIds();
        List<Map<List<Word<Boolean>>, Integer>> unclosedIndexes = new ArrayList<>(this.outputMaps.size());
        List<List<OutputRow<I, O>>> unclosed = new ArrayList<>();
        for (int i = 0; i < this.outputMaps.size(); i++) {
            unclosedIndexes.add(new HashMap<>());
            for (OutputRow<I, O> row : this.allRows) {
                List<Word<O>> rowContents = this.table.get(row.getRowId());
                List<Word<D>> outputContents = this.toOutputWords(rowContents, this.outputMaps.get(i));
                List<Integer> contentIds = this.outputContentIds.get(i).get(outputContents);
                if (contentIds == null) { // The row is unclosed for this map
                    Integer unclosedIndex = unclosedIndexes.get(i).get(outputContents);
                    if (unclosedIndex == null) { // There is no equivalence class for this row, so add one
                        unclosedIndex = unclosed.size();
                        unclosed.add(new ArrayList<>());
                    }
                    unclosed.get(unclosedIndex).add(row);
                } else { // The row is closed for this map
                    row.setShortRow(i, this.allRows.get(contentIds.get(0)));
                }
            }
        }
        return unclosed;
    }

    /**
     * Finds rows that are inconsistent when projected to some output map
     * Two rows are inconsistent if they are equal, but their successor rows aren't
     *
     * @return A word that would fix an inconsistency, or null if none exist
     */
    public Word<I> findInconsistentRows() {
        for (int i = 0; i < this.outputMaps.size(); i++) {
            Map<List<Word<D>>, List<Integer>> currentOutputContentIds = this.outputContentIds.get(i);
            for (List<Integer> currentList : currentOutputContentIds.values()) {
                if (currentList.size() <= 1) {
                    continue;
                }
                for (int a = 0; a < this.inputAlphabet.size(); a++) {
                    List<List<Word<D>>> successors = new ArrayList<>();
                    for (Integer current : currentList) { // Build successor rows
                        OutputRow<I, O> currentRow = this.allRows.get(current);
                        OutputRow<I, O> sucRow = currentRow.getSuccessor(a);
                        List<Word<O>> sucOutputs = this.table.get(sucRow.getRowId());
                        successors.add(this.toOutputWords(sucOutputs, this.outputMaps.get(i)));
                    }
                    List<Word<D>> first = successors.get(0);
                    for (int j = 1; j < successors.size(); j++) { // Check they are all equal
                        List<Word<D>> other = successors.get(j);
                        for (int k = 0; k < first.size(); k++) {
                            if (!first.get(k).equals(other.get(k))) { // Inconsistency found
                                I infix = this.inputAlphabet.getSymbol(a);
                                Word<I> suffix = this.suffixes.get(k);
                                return Word.fromLetter(infix).concat(suffix);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds all rows that are inconsistent when projected to some output map
     * Two rows are inconsistent if they are equal, but their successor rows aren't
     *
     * @return All words that would fix an inconsistency, or null if none exist
     */
    public List<Word<I>> findAllInconsistentRows() {
        ArrayList<Word<I>> result = new ArrayList<>();
        for (int i = 0; i < this.outputMaps.size(); i++) {
            Map<List<Word<D>>, List<Integer>> currentOutputContentIds = this.outputContentIds.get(i);
            for (List<Integer> currentList : currentOutputContentIds.values()) {
                if (currentList.size() <= 1) {
                    continue;
                }
                for (int a = 0; a < this.inputAlphabet.size(); a++) {
                    List<List<Word<D>>> successors = new ArrayList<>();
                    for (Integer current : currentList) { // Build successor rows
                        OutputRow<I, O> currentRow = this.allRows.get(current);
                        OutputRow<I, O> sucRow = currentRow.getSuccessor(a);
                        List<Word<O>> sucOutputs = this.table.get(sucRow.getRowId());
                        successors.add(this.toOutputWords(sucOutputs, this.outputMaps.get(i)));
                    }
                    List<Word<D>> first = successors.get(0);
                    for (int j = 1; j < successors.size(); j++) { // Check they are all equal
                        List<Word<D>> other = successors.get(j);
                        for (int k = 0; k < first.size(); k++) {
                            if (!first.get(k).equals(other.get(k))) { // Inconsistency found
                                I infix = this.inputAlphabet.getSymbol(a);
                                Word<I> suffix = this.suffixes.get(k);
                                result.add(Word.fromLetter(infix).concat(suffix));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}
