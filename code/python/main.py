import csv

def read_olstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if(len(model) < 10):
            continue
        for i in range(11):
            model[i] = model[i].split(": ")[-1]
    return data

def read_lstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if(len(model) < 6):
            continue
        for i in range(7):
            model[i] = model[i].split(": ")[-1]
    return data

def remove_filepath(raw_data):
    data = raw_data.split("\n")
    if data[-1] == "":
        data = data[:-1]
    header = data[0]
    data = data[1:]
    for i, d in enumerate(data):
        data[i] = d.split("\\")[-1]
    return header, data

def extract_artificial_model_name(data):
    for i in range(len(data)):
        data[i][0] = data[i][0].split("\\")[-1][:-4]

def add_header_olstar(data):
    data.extendleft(["Model","Stages","States","Short rows","Inconsistent count","Zero outputs count","Two outputs count","Learning queries","Learning symbols","Testing queries","Testing symbols"])

def add_header_lstar(data):
    data.extendleft(["Model","Stages","States","Learning queries","Learning symbols","Testing queries","Testing symbols"])

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
    #data_olstar = read_olstar("D:\\Data\\results_labbaf_olstar_random.txt")
    data_lstar = read_lstar("D:\\Data\\results_artificial_ilstar_olstar.txt")
    extract_artificial_model_name(data_lstar)

    #write_to_csv(list(sorted(data_olstar)), "D:\\Data\\results_labbaf_olstar_random.csv")
    write_to_csv(list(sorted(data_lstar)), "D:\\Data\\results_artificial_ilstar_olstar.csv")

if __name__ == "__main__":
    main()
