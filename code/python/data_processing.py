import csv

def read_olstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if len(model) < 10:
            continue
        for i in range(11):
            model[i] = model[i].split(": ")[-1]
    return data

def read_lstar(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if len(model) < 6:
            continue
        for i in range(7):
            model[i] = model[i].split(": ")[-1]
    return data

def read_decompose(filename):
    with open(filename) as f:
        data = [s.split("\n") for s in f.read().split("\n\n")]
    for model in data:
        if len(model) < 8:
            continue
        for i in range(8):
            model[i] = model[i].split(": ")[-1]
        del model[3]
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
        data[i][0] = data[i][0].split("\\")[-1]

def add_header_olstar(data):
    header = ["Model","Stages","States","Short rows","Inconsistent count","Zero outputs count","Two outputs count","Learning queries","Learning symbols","Testing queries","Testing symbols"]
    data.insert(0, header)

def add_header_lstar(data):
    header = ["Model","Stages","States","Learning queries","Learning symbols","Testing queries","Testing symbols"]
    data.insert(0, header)

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

def clean(filename):
    with open(filename) as f:
            data = f.readlines()
    header = data[0]
    footer = data[-1]
    initial = "s" + data[0][:-1]
    data = [x.split(" -> ") for x in data if " -> " in x]
    data = ["s" + r[0] + " -> s" + r[1] for r in data]
    data = [header, f"__start0 -> {initial};\n"] + data + [footer]
    data = ''.join(data)
    return data

def main():
    #data_olstar = read_olstar("D:\\Code\\OLstar\\results\\artificial_my_lstar.txt")
    data_lstar = read_lstar("D:\\Code\\OLstar\\results\\protocols_generic_ttt.txt")
    extract_artificial_model_name(data_lstar)
    data_lstar = data_lstar[:-1]
    data_lstar = sorted(data_lstar)
    add_header_lstar(data_lstar)

    write_to_csv(data_lstar, "D:\\Code\\OLstar\\results\\protocols_generic_ttt.csv")

if __name__ == "__main__":
    main()
