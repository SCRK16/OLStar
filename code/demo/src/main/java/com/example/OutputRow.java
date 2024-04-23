package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import net.automatalib.word.Word;

/**
 * The rows in the observation table used by OL*.
 * They record their own successor rows and outputs,
 * so they can be used effectively as the states in a Mealy machine.
 */
public class OutputRow<I, O> {

    private final Word<I> label;
    private final int rowId;
    /**
     * A list of successor rows for every input.
     * Only short prefix rows have this set. For long prefix rows, it is null.
     */
    private ArrayList<OutputRow<I, O>> successors;
    /**
     * For every output map, the short row this row is equal to.
     */
    private ArrayList<OutputRow<I, O>> shortRows;
    /** For every input, the output from this row */
    private List<O> outputs;

    public OutputRow(Word<I> label, int rowId) {
        this.label = label;
        this.rowId = rowId;
        this.successors = null;
        this.shortRows = new ArrayList<>();
    }

    public Word<I> getLabel() {
        return this.label;
    }

    public int getRowId() {
        return this.rowId;
    }

    public OutputRow<I, O> getSuccessor(int inputIndex) {
        return this.successors.get(inputIndex);
    }

    /**
     * @param inputIndex The index of the input symbol in the input alphabet
     * @param suc        The successor row for the input
     * @return {@code true} if and only if this output row is a short prefix row
     */
    public boolean setSuccessor(int inputIndex, OutputRow<I, O> suc) {
        if (this.successors == null) {
            return false;
        }
        this.successors.set(inputIndex, suc);
        return true;
    }

    public boolean isShortPrefixRow() {
        return this.successors != null;
    }

    public void resetShortRows() {
        this.shortRows = new ArrayList<>();
    }

    public boolean setShortRow(int outputIndex, OutputRow<I, O> row) {
        while (this.shortRows.size() < outputIndex) {
            this.shortRows.add(null);
        }
        if (this.shortRows.size() == outputIndex) {
            this.shortRows.add(row);
        } else {
            this.shortRows.set(outputIndex, row);
        }
        return true;
    }

    public boolean addShortRow(OutputRow<I, O> row) {
        this.shortRows.add(row);
        return true;
    }

    public OutputRow<I, O> getShortRow(int outputIndex) {
        return this.shortRows.get(outputIndex);
    }

    public List<OutputRow<I, O>> getShortRows() {
        return Collections.unmodifiableList(this.shortRows);
    }

    public void makeShort(int inputAlphabetSize) {
        if (this.isShortPrefixRow()) {
            return;
        }
        this.successors = Lists.newArrayListWithCapacity(inputAlphabetSize);
        for (int i = 0; i < inputAlphabetSize; i++) {
            this.successors.add(null);
        }
    }

    public void setOutputs(List<O> outputContents) {
        this.outputs = outputContents;
    }

    public List<O> getOutputs() {
        return Collections.unmodifiableList(this.outputs);
    }

    public O getOutput(int index) {
        return this.outputs.get(index);
    }

    @Override
    public String toString() {
        return this.label.toString();
    }
}
