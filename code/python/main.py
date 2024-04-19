import csv

def read_olstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if(len(model) < 10):
            continue
        for i in range(11):
            model[i] = int(model[i].split(": ")[-1])
    return data

def read_lstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if(len(model) < 6):
            continue
        for i in range(7):
            model[i] = int(model[i].split(": ")[-1])
    return data

def write_to_csv(data, filename):
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerows(data)

def total_queries_olstar(data):
    return [model[7] + model[9] for model in data]

def total_symbols_olstar(data):
    return [model[8] + model[10] for model in data]

def total_queries_lstar(data):
    return [model[3] + model[5] for model in data]

def total_symbols_lstar(data):
    return [model[4] + model[6] for model in data]

def main():
    data_olstar = read_olstar("D:\\Data\\results_labbaf_olstar_random.txt")
    data_lstar = read_lstar("D:\\Data\\results_labbaf_lstar_random.txt")

    write_to_csv(list(sorted(data_olstar)), "D:\\Data\\results_labbaf_olstar_random.csv")
    write_to_csv(list(sorted(data_lstar)), "D:\\Data\\results_labbaf_lstar_random.csv")

if __name__ == "__main__":
    main()
