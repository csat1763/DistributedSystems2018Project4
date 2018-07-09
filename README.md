# Distributed Systems 2018
## Project #4a Measurements-in-AWS-VM2VM
### GitHub https://github.com/csat1763/DistributedSystems2018Project4

### Team

- Aspir Ahmet
- Nardi Mark
- Pfeifhofer Martin

## Task

1. For all given instance-types: Start a pair of spot instances for the 2 cheapest zones (check automatically in each region / availability zone)
2. Measure transfer time VM2VM within same zone (different instance types)
3. Determine the cost-performance trade-off

## Submission
See *Documentation/Submission.txt* to check task fulfillment.

## Software and hardware requirements
Windows 64 Bit with Cygwin or a Bash (eg. GIT-Bash) or any Unix System.
Regular hardware is able to run this software.

## Documentation
For detailed documentation see folder *Documentation*.
* Code: *Code-Documentation.txt*
* Work-flow: *General.txt*
* Problems: *Problems.txt*
* Brief overview as presentation: *Presentation/Measurements-in-AWS-VM2VM.pdf*

## Prerequisites
Before running the code enter your AWS-credentials in file...

	$ credentials.json

If you wish to test different instance-types modify the file below accordingly in json format...

	$ instance-types.json
	
This requires the phases in main to be adjusted the phases in *main.sh* accordingly.

## Code
The code can be found in the *Code* folder.

	
Open a shell in the folder *Code*. 
Then run following command in your shell...

	$ chmod +x start.sh

To execute the code do the following...

     $ ./start.sh
	 	 
To stop all spot-requests and terminate all running instances run...
	 
	 $ ./terminateAll.sh

The output from running *./start.sh* will be the file below which is in the folder *Code*.
	
	$ cost-performance.csv

## Results
All results were extracted from measurements with file-size of 1GB.
The results can be found in the *Results* folder. The results are stored in a *.csv* file. The measurements are evaluated in an Excel Sheet.
Also a HTML version of the results is available at: http://htmlpreview.github.io/?https://github.com/csat1763/DistributedSystems2018Project4/blob/master/Results/Evaluation%20Measurements.htm