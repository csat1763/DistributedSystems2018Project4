import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;

/* This java program uses measurement files in order to generate a global overview as a csv output-file: cost-performance.csv.
 * 
 * Prerequisites:
 * 		1. Existing folder structure:
 * 			1. For every instance-type 1 folder
 * 			2. Within that folder a folder called: @results
 * 			3. Exisiting file in folder: /jsons/instance-types.json
 * 		2. Existing files (2) within folder @results: ZONE1-VM1toVM2.json and ZONE2-VM1toVM2.json
 * Output:
 * 		1. cost-performance.csv
 *  */
public class CostPerformance {

	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args)
			throws FileNotFoundException, UnsupportedEncodingException, InterruptedException, ExecutionException {

		ExecutorService pool = Executors.newFixedThreadPool(5);

		/* Path to shell execution */
		String path = args[0];

		ArrayList<Future<ArrayList<Result>>> resultFromMeasurementFutures = new ArrayList<Future<ArrayList<Result>>>();

		/*
		 * Get the instance-types from a file and submit a CostPerformanceReader for
		 * each type which then returns a Result-Array which consists of Resutlts for
		 * the first and second zone.
		 */
		JsonReader instances = new JsonReader(new FileReader(path + "/jsons/" + "instance-types.json"));
		LinkedTreeMap<String, Object> instancesJ = new Gson().fromJson(instances, LinkedTreeMap.class);
		ArrayList<LinkedTreeMap<String, Object>> instanceTypes = (ArrayList<LinkedTreeMap<String, Object>>) instancesJ
				.get("types");

		for (LinkedTreeMap<String, Object> type : instanceTypes) {
			String pathToResultFile = path + "/spots/";
			pathToResultFile = pathToResultFile + type.get("type");
			resultFromMeasurementFutures.add(pool.submit(new CostPerformanceReader(pathToResultFile)));
		}

		/* Add all results to resultsForZone1 */
		ArrayList<Result> resultsForZone1 = new ArrayList<Result>();
		ArrayList<Result> resultsForZone2 = new ArrayList<Result>();
		for (Future<ArrayList<Result>> res : resultFromMeasurementFutures) {
			ArrayList<Result> resForType = res.get();
			resultsForZone1.addAll(resForType);
		}

		/*
		 * Then remove all results which are not the best price for that spot-instance
		 * from resultsForZone1.
		 */
		for (Result res : resultsForZone1) {
			if (!res.isBestPrice())
				resultsForZone2.add(res);
		}

		resultsForZone1.removeAll(resultsForZone2);

		pool.shutdown();

		/* Export results to CSV */
		File newFile = new File(path + "/" + "cost-performance.csv");
		PrintWriter pw = new PrintWriter(newFile);
		pw.println("Cost-Performance Table");
		pw.println("1st Zone (Cheapest)");
		pw.println("Instance-Type,Speed [MB/s], Cost [$/h], Zone");
		for (Result res : resultsForZone1) {
			pw.println(res.toCSV());
		}

		pw.println();
		pw.println("2nd Zone (2nd-Cheapest)");
		pw.println("Instance-Type,Speed [MB/s], Cost [$/h], Zone");

		for (Result res : resultsForZone2) {
			pw.println(res.toCSV());
		}
		pw.close();

		System.out.println("[*] Output in cost-performance.csv! Use , for csv view.");

	}

}

class CostPerformanceReader implements Callable<ArrayList<Result>> {

	private String path;

	public CostPerformanceReader(String path) {
		this.path = path;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ArrayList<Result> call() throws Exception {

		ArrayList<Result> results = new ArrayList<Result>();

		JsonReader spotpair = new JsonReader(new FileReader(path + "/" + "spot-pair.json"));
		ArrayList<LinkedTreeMap<String, Object>> spotpairJ = new Gson().fromJson(spotpair, ArrayList.class);
		ArrayList<String> zones = new ArrayList<String>();

		/* Extract the two zones from spot-pair.json. */
		if (null != spotpairJ && !spotpairJ.isEmpty()) {
			for (LinkedTreeMap<String, Object> spot : spotpairJ) {
				zones.add(spot.get("Zone").toString());
			}

		}
		/*
		 * Run routine for the two entries in spot-pair.json, and specify that the 2nd
		 * entry is not best price by passing "zone == zones.get(0)" to "result".
		 */
		for (String zone : zones) {

			JsonReader measurement = new JsonReader(new FileReader(path + "/@results/" + zone + "-VM1toVM2.json"));
			LinkedTreeMap<String, Object> measurementJ = new Gson().fromJson(measurement, LinkedTreeMap.class);
			ArrayList<LinkedTreeMap<String, Object>> files = (ArrayList<LinkedTreeMap<String, Object>>) measurementJ
					.get("Files");

			/* Get second last entry from measurement (Files). */
			LinkedTreeMap<String, Object> file = files.get(files.size() - 2);
			Result result = new Result(measurementJ.get("instanceType").toString(),
					Double.parseDouble(file.get("speedInMBpS").toString()),
					Double.parseDouble(measurementJ.get("price").toString()), zone == zones.get(0),
					measurementJ.get("zone").toString());
			results.add(result);
		}

		return results;

	}
}

class Result {

	private String instanceType;
	private double speed;
	private double cost;
	private boolean bestPrice;
	private String zone;

	public Result(String instanceType, double speed, double cost, boolean bestPrice, String zone) {
		super();
		this.instanceType = instanceType;
		this.speed = speed;
		this.cost = cost;
		this.bestPrice = bestPrice;
		this.zone = zone;
	}

	public String getZone() {
		return zone;
	}

	public boolean isBestPrice() {
		return bestPrice;
	}

	public String getInstanceType() {
		return instanceType;
	}

	public double getSpeed() {
		return speed;
	}

	public double getCost() {
		return cost;
	}

	@Override
	public String toString() {
		return "Result [instanceType=" + instanceType + ", speed=" + speed + ", cost=" + cost + ", bestPrice="
				+ bestPrice + ", zone=" + zone + "]";
	}

	public String toCSV() {
		return instanceType + ", " + speed + ", " + cost + ", " + zone;
	}

}
