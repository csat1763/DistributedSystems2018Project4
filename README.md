# Distributed Systems 2018
## Project #4

### Team

- Aspir Ahmet
- Nardi Mark
- Pfeifhofer Martin

## Task

1. Start the cheapest spot instances (check automatically in each region / availability zone)
2. Measure transfer time VM2VM (different instance types)
3. Determine the cost-performance trade-off

## Submission
See *Documentation/Submission.txt* to check task fulfillment.

## Software and hardware requirements
Windows 64 Bit with Cygwin or a Bash (eg. GIT-Bash) or any Unix System.
Regular hardware is able to run this software.

## Code
The code can be found in the *Code* folder.
Before running the code enter your credentials in file...

	$ credentials.json
	
Open a shell in the folder *Code*. 
Then run following command in your shell...

	$ chmod +x start.sh

To execute the code do the following...

     $ ./start.sh
	 
To stop all spot-requests and terminate all running instances run...
	 
	 $ ./terminateAll.sh

The output from running ./start.sh will be the file below which is then further evaluated.

	cost-performance.csv

## Results

The results can be found in the *Results* folder. The results are stored in a *.csv* file. The measurements are evaluated in an Excel Sheet.
Also a HTML version of the results is available at: http://htmlpreview.github.io/?https://github.com/csat1763/DistributedSystems2018Project4/blob/master/Results/Evaluation%20Measurements.htm