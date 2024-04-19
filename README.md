# OL*
Output-decomposed Learning of Mealy Machines based on L*.

Java source code for OL* can be found in the folder [`code/demo/src/main/java/com/example`](code/demo/src/main/java/com/example/).
Python source code for analysing the results can be found in the folder [`code/python`](code/python/).
Models from our own benchmark can be found in the folder [`models`](models/). To 
Results in the form of .csv files containing all of the data used for the analysis can be found in the folder [`results`](results/).

## Usage instructions

All code was tested to run on a Windows 10 machine. For the Java code we used OpenJDK version 17.0.9. They were run in Visual Studio Code using maven as a package manager.
The main file takes two command line arguments. The first argument is the full filename of the model to be learned. The second argument is the name of the algorithm to be used. The options for these are:
- [L* (Angluin, 1987)](https://doi.org/10.1016/0890-5401(87)90052-6), as implemented in the LearnLib library using the SUFFIX1BY1 counterexample handler.
- [TTT (Isberner et al., 2014)](https://doi.org/10.1007/978-3-319-11164-3_26), as implemented in the LearnLib library.
- OL*, our new algorithm based on L* which decomposes the Mealy machine based on its outputs (but with a single observation table).
- Decompose, which runs a separate instance of the TTT algorithm for each output of the machine.

The Python code was tested to run using Python version 3.12.2. Please also install matplotlib, numpy, pandas and seaborn using `pip install` and use an appropriate Jupyter notebook environment.