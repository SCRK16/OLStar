import csv
import matplotlib.pyplot as plt

def read_olstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        for i in range(11):
            model[i] = int(model[i].split(": ")[-1])
    return data

def read_lstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
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
    data_olstar = read_olstar("D:\\Data\\results_labbaf_olstar.txt")
    data_lstar = read_lstar("D:\\Data\\results_labbaf_lstar.txt")

    plot_data_olstar = total_queries_olstar(data_olstar)
    plot_data_lstar = total_queries_lstar(data_lstar)

    fig, ax = plt.subplots(figsize=(10, 10))
    plt.plot(plot_data_lstar, plot_data_olstar, 'ro', markersize=4)
    plt.plot(plot_data_lstar, plot_data_lstar, "b--")
    plt.xscale('log')
    plt.yscale('log')
    plt.xlabel('#Queries L*')
    plt.ylabel('#Queries OL*')
    plt.show()

if __name__ == "__main__":
    main()
