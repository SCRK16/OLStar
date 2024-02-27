package com.example;

import com.google.common.collect.Lists;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.util.automaton.builder.AutomatonBuilders;
import net.automatalib.util.automaton.builder.MealyBuilder;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/*
 * A simple way to parse the dot files as occurring in the benchmark suite at http://automata.cs.ru.nl/MealyMachines
 * This is specialized for the circuits from that suite. The output is considered as a bitvector, instead
 * of a unstructured string. All transitions in a circuit has the same length of bitvector.
 */
public class CircuitParser {
    public static class Edge {
        public final String from;
        public final String to;
        public final String label;

        Edge(String b, String e, String l) {
            from = b;
            to = e;
            label = l;
        }
    }

    public final Set<String> nodes;
    public final Set<Edge> edges;
    public String initialState;
    public int dimensionality = 0;

    CircuitParser(Path filename) throws IOException {
        nodes = new HashSet<>();
        edges = new HashSet<>();

        Scanner s = new Scanner(filename);
        while (s.hasNextLine()) {
            String line = s.nextLine();

            if (!line.contains("label"))
                continue;

            if (line.contains("->")) {
                int e1 = line.indexOf('-');
                int e2 = line.indexOf('[');
                int b3 = line.indexOf('"');
                int e3 = line.lastIndexOf('"');

                String from = line.substring(0, e1).trim();
                String to = line.substring(e1 + 2, e2).trim();
                String label = line.substring(b3 + 1, e3).trim();

                if (dimensionality == 0) {
                    String[] io = label.split("/");
                    String output = io[1].trim();
                    dimensionality = stringToList(output).size();
                }

                // First read state will be the initial one.
                if (initialState == null)
                    initialState = from;

                nodes.add(from);
                nodes.add(to);
                edges.add(new Edge(from, to, label));
            } else {
                int end = line.indexOf('[');
                if (end <= 0)
                    continue;
                String node = line.substring(0, end).trim();

                nodes.add(node);
            }
        }
        s.close();
    }

    @SuppressWarnings("null")
    CompactMealy<String, List<String>> createMachine() {
        Set<String> inputs = new HashSet<>();
        for (Edge e : edges) {
            String[] io = e.label.split("/");
            inputs.add(io[0].trim());
        }

        List<String> inputList = Lists.newArrayList(inputs.iterator());
        Alphabet<String> alphabet = Alphabets.fromList(inputList);

        MealyBuilder<?, String, ?, List<String>, CompactMealy<String, List<String>>>.MealyBuilder__1 builder = AutomatonBuilders
                .<String, List<String>>newMealy(alphabet).withInitial(initialState);

        for (Edge e : edges) {
            String[] io = e.label.split("/");
            String input = io[0].trim();
            String output = io[1].trim();

            List<String> outputAsList = stringToList(output);

            builder.from(e.from).on(input).withOutput(outputAsList).to(e.to);
        }

        return builder.create();
    }

    // This function will convert outputs to composed outputs.
    List<String> stringToList(String str) {
        List<String> out = Lists.newArrayListWithCapacity(str.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            sb.append(str.charAt(i));
            // this is used to group bits (so not to get too many components)
            // TODO: make this a parameter
            if (sb.length() >= 2) {
                out.add(sb.toString());
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            out.add(sb.toString());
        }
        return out;
    }
}
