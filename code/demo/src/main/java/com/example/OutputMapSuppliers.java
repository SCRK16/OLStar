package com.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Supplier;

public class OutputMapSuppliers {
    public static List<Map<String, Integer>> artificial2maps() {
        List<Map<String, Integer>> result = new ArrayList<>();
        HashMap<String, Integer> xyMap = new HashMap<>();
        xyMap.put("L", 0);
        xyMap.put("R", 1);
        xyMap.put("x", 2);
        xyMap.put("y", 3);
        xyMap.put("z", 4);
        xyMap.put("w", 4);
        result.add(xyMap);
        HashMap<String, Integer> wzMap = new HashMap<>();
        wzMap.put("L", 0);
        wzMap.put("R", 1);
        wzMap.put("w", 3);
        wzMap.put("z", 2);
        wzMap.put("x", 4);
        wzMap.put("y", 4);
        result.add(wzMap);
        return result;
    }

    public static List<Map<String, Integer>> artificial3maps() {
        List<Map<String, Integer>> result = new ArrayList<>();
        HashMap<String, Integer> xyMap = new HashMap<>();
        xyMap.put("L", 0);
        xyMap.put("R", 1);
        xyMap.put("x", 2);
        xyMap.put("y", 3);
        xyMap.put("u", 4);
        xyMap.put("w", 4);
        xyMap.put("z", 4);
        xyMap.put("v", 4);
        result.add(xyMap);
        HashMap<String, Integer> wzMap = new HashMap<>();
        wzMap.put("L", 0);
        wzMap.put("R", 1);
        wzMap.put("w", 2);
        wzMap.put("z", 3);
        wzMap.put("x", 4);
        wzMap.put("y", 4);
        wzMap.put("u", 4);
        wzMap.put("v", 4);
        result.add(wzMap);
        HashMap<String, Integer> uvMap = new HashMap<>();
        uvMap.put("L", 0);
        uvMap.put("R", 1);
        uvMap.put("u", 2);
        uvMap.put("v", 3);
        uvMap.put("x", 4);
        uvMap.put("y", 4);
        uvMap.put("w", 4);
        uvMap.put("z", 4);
        result.add(uvMap);
        return result;
    }

    public static List<Map<String, Integer>> artificial4maps() {
        List<Map<String, Integer>> result = new ArrayList<>();
        HashMap<String, Integer> xyMap = new HashMap<>();
        xyMap.put("L", 0);
        xyMap.put("R", 1);
        xyMap.put("x", 2);
        xyMap.put("y",3);
        xyMap.put("w", 4);
        xyMap.put("z", 4);
        xyMap.put("u", 4);
        xyMap.put("v", 4);
        xyMap.put("k", 4);
        xyMap.put("l", 4);
        result.add(xyMap);
        HashMap<String, Integer> wzMap = new HashMap<>();
        wzMap.put("L", 0);
        wzMap.put("R", 1);
        wzMap.put("w", 2);
        wzMap.put("z", 3);
        wzMap.put("x", 4);
        wzMap.put("y", 4);
        wzMap.put("u", 4);
        wzMap.put("v", 4);
        wzMap.put("k", 4);
        wzMap.put("l", 4);
        result.add(wzMap);
        HashMap<String, Integer> uvMap = new HashMap<>();
        uvMap.put("L", 0);
        uvMap.put("R", 1);
        uvMap.put("u", 2);
        uvMap.put("v", 3);
        uvMap.put("x", 4);
        uvMap.put("y", 4);
        uvMap.put("w", 4);
        uvMap.put("z", 4);
        uvMap.put("k", 4);
        uvMap.put("l", 4);
        result.add(uvMap);
        HashMap<String, Integer> klMap = new HashMap<>();
        klMap.put("L", 0);
        klMap.put("R", 1);
        klMap.put("k", 2);
        klMap.put("l", 3);
        klMap.put("x", 4);
        klMap.put("y", 4);
        klMap.put("w", 4);
        klMap.put("z", 4);
        klMap.put("u", 4);
        klMap.put("v", 4);
        result.add(klMap);
        return result;
    }

    public static List<Map<String, Integer>> artificial5maps() {
        List<Map<String, Integer>> result = new ArrayList<>();
        HashMap<String, Integer> xyMap = new HashMap<>();
        xyMap.put("L", 0);
        xyMap.put("R", 1);
        xyMap.put("x", 2);
        xyMap.put("y",3);
        xyMap.put("w", 4);
        xyMap.put("z", 4);
        xyMap.put("u", 4);
        xyMap.put("v", 4);
        xyMap.put("k", 4);
        xyMap.put("l", 4);
        xyMap.put("m", 4);
        xyMap.put("n", 4);
        result.add(xyMap);
        HashMap<String, Integer> wzMap = new HashMap<>();
        wzMap.put("L", 0);
        wzMap.put("R", 1);
        wzMap.put("w", 2);
        wzMap.put("z", 3);
        wzMap.put("x", 4);
        wzMap.put("y", 4);
        wzMap.put("u", 4);
        wzMap.put("v", 4);
        wzMap.put("k", 4);
        wzMap.put("l", 4);
        wzMap.put("m", 4);
        wzMap.put("n", 4);
        result.add(wzMap);
        HashMap<String, Integer> uvMap = new HashMap<>();
        uvMap.put("L", 0);
        uvMap.put("R", 1);
        uvMap.put("u", 2);
        uvMap.put("v", 3);
        uvMap.put("x", 4);
        uvMap.put("y", 4);
        uvMap.put("w", 4);
        uvMap.put("z", 4);
        uvMap.put("k", 4);
        uvMap.put("l", 4);
        uvMap.put("m", 4);
        uvMap.put("n", 4);
        result.add(uvMap);
        HashMap<String, Integer> klMap = new HashMap<>();
        klMap.put("L", 0);
        klMap.put("R", 1);
        klMap.put("k", 2);
        klMap.put("l", 3);
        klMap.put("x", 4);
        klMap.put("y", 4);
        klMap.put("w", 4);
        klMap.put("z", 4);
        klMap.put("u", 4);
        klMap.put("v", 4);
        klMap.put("m", 4);
        klMap.put("n", 4);
        result.add(klMap);
        HashMap<String, Integer> mnMap = new HashMap<>();
        mnMap.put("L", 0);
        mnMap.put("R", 1);
        mnMap.put("m", 2);
        mnMap.put("n", 3);
        mnMap.put("x", 4);
        mnMap.put("y", 4);
        mnMap.put("w", 4);
        mnMap.put("z", 4);
        mnMap.put("u", 4);
        mnMap.put("v", 4);
        mnMap.put("k", 4);
        mnMap.put("l", 4);
        result.add(mnMap);
        return result;
    }

    public static Supplier<List<Map<String, Integer>>> artificialMaps(String model) {
        if (model.contains("random-2")) {
            return OutputMapSuppliers::artificial2maps;
        } else if (model.contains("random-3")) {
            return OutputMapSuppliers::artificial3maps;
        } else if (model.contains("random-4")) {
            return OutputMapSuppliers::artificial4maps;
        } else if (model.contains("random-5")) {
            return OutputMapSuppliers::artificial5maps;
        }
        throw new IllegalArgumentException("Illegal model for artificialMaps");
    }

    public static Supplier<List<Map<String, Integer>>> fromFile(File file) throws FileNotFoundException {
        Scanner scanner = new Scanner(file);
        List<String[]> lines = new ArrayList<>();
        while (scanner.hasNextLine()) {
            lines.add(scanner.nextLine().split(" "));
        }
        scanner.close();
        int count = lines.get(0).length;
        List<Map<String, Integer>> result = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            Map<String, Integer> map = new HashMap<>();
            for (String[] line : lines) {
                map.put(line[0], Integer.parseInt(line[i]));
            }
            result.add(map);
        }
        return () -> result;
    }
}
