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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;

public class CostPerformance {

	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args)
			throws FileNotFoundException, UnsupportedEncodingException, InterruptedException, ExecutionException {

		ExecutorService pool = Executors.newFixedThreadPool(5);
		String path = args[0];

		ArrayList<Future<ArrayList<Result>>> searchResultsFromThreads = new ArrayList<Future<ArrayList<Result>>>();

		// String path = "D:/Desktop/6. Semester/Verteile Systeme/Project/Sheet-12/";
		JsonReader instances = new JsonReader(new FileReader(path + "/jsons/" + "instance-types.json"));
		LinkedTreeMap<String, Object> instancesJ = new Gson().fromJson(instances, LinkedTreeMap.class);
		ArrayList<LinkedTreeMap<String, Object>> instanceTypes = (ArrayList<LinkedTreeMap<String, Object>>) instancesJ
				.get("types");

		for (LinkedTreeMap<String, Object> type : instanceTypes) {
			String ipath = path + "/spots/";
			ipath = ipath + type.get("type");
			searchResultsFromThreads.add(pool.submit(new CostPerfomanceCalculator(ipath)));
		}

		ArrayList<Result> zone1 = new ArrayList<Result>();
		ArrayList<Result> zone2 = new ArrayList<Result>();
		for (Future<ArrayList<Result>> res : searchResultsFromThreads) {
			ArrayList<Result> resForType = res.get();
			zone1.addAll(resForType);
		}
		for (Result res : zone1) {
			if (!res.isBestPrice())
				zone2.add(res);
		}

		zone1.removeAll(zone2);

		pool.shutdown();

		File newFile = new File(path + "/" + "cost-performance.csv");
		PrintWriter pw = new PrintWriter(newFile);
		pw.println("Cost-Performance Table");
		pw.println("1st Zone (Cheapest)");
		pw.println("Instance-Type,Speed [MB/s], Cost [$/h], Zone");
		for (Result res : zone1) {
			pw.println(res.toCSV());
		}

		pw.println();
		pw.println("2nd Zone (2nd-Cheapest)");
		pw.println("Instance-Type,Speed [MB/s], Cost [$/h], Zone");

		for (Result res : zone2) {
			pw.println(res.toCSV());
		}
		pw.close();

		System.out.println("[*] Output in cost-performance.csv! Use , for csv view.");

	}

	public static String prettyJson(LinkedTreeMap<String, Object> entry) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(new Gson().toJson(entry)));
	}

}

class CostPerfomanceCalculator implements Callable<ArrayList<Result>> {

	private String path;

	public CostPerfomanceCalculator(String path) {
		this.path = path;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ArrayList<Result> call() throws Exception {

		ArrayList<Result> results = new ArrayList<Result>();

		JsonReader spotpair = new JsonReader(new FileReader(path + "/" + "spot-pair.json"));
		ArrayList<LinkedTreeMap<String, Object>> spotpairJ = new Gson().fromJson(spotpair, ArrayList.class);
		ArrayList<String> zones = new ArrayList<String>();
		if (null != spotpairJ && !spotpairJ.isEmpty()) {
			for (LinkedTreeMap<String, Object> result : spotpairJ) {
				zones.add(result.get("Zone").toString());
			}

		}

		for (String zone : zones) {

			JsonReader measurement = new JsonReader(new FileReader(path + "/@results/" + zone + "-VM1toVM2.json"));
			LinkedTreeMap<String, Object> measurementJ = new Gson().fromJson(measurement, LinkedTreeMap.class);
			ArrayList<LinkedTreeMap<String, Object>> files = (ArrayList<LinkedTreeMap<String, Object>>) measurementJ
					.get("Files");
			LinkedTreeMap<String, Object> file = files.get(files.size() - 2);
			Result bb = new Result(measurementJ.get("instanceType").toString(),
					Double.parseDouble(file.get("speedInMBpS").toString()),
					Double.parseDouble(measurementJ.get("price").toString()), zone == zones.get(0),
					measurementJ.get("zone").toString());
			// System.out.println(bb);
			results.add(bb);
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
