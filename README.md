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

## Code
The code can be found in the *Code* folder.
Before running the code enter your credentials in file...

	$ credentials.json
	
Open a shell in the folder "Code".
Then run following command in your shell...

	$ chmod +x start.sh

To execute the code do the following...

     $ ./start.sh
	 
To stop all spot-requests and terminate all running instances run...
	 
	 $ ./terminateAll.sh

## Results

The results can be found in the *Results* folder. The results are stored in a *.csv* file. The measurements are evaluated in an Excel Sheet.
