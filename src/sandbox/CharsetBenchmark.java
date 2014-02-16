package sandbox;

import java.awt.Color;
import java.awt.Dimension;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class CharsetBenchmark {

	public static void main(String[] args) {
		int thread = 150;
		int lookup = 3000;
		Map<String, LinkedHashMap<Integer, Long>> data = new HashMap<String, LinkedHashMap<Integer, Long>>();
		data.put("StandardCharsetProvider", test(thread, lookup));
		try {
			NonBlockingCharsetProvider.setUp(true);
			data.put("NonBlockingCharsetProvider (lazyInit=true)", test(thread, lookup));
			NonBlockingCharsetProvider.setUp(false);
			data.put("NonBlockingCharsetProvider (lazyInit=false)", test(thread, lookup));
		} catch (Exception e) {
			e.printStackTrace();
		}
		showResult(data);
	}

	private static LinkedHashMap<Integer, Long> test(final int threads, final int lookup) {
		long sum = 0;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		LinkedHashMap<Integer, Long> ts = new LinkedHashMap<>();
		for (int t = 1; t <= threads; t ++) {
			long time = System.currentTimeMillis();
			final CountDownLatch latch = new CountDownLatch(t - 1);
			for (int i = 0; i < t; i++) {
				pool.submit(new Runnable() {
					@Override
					public void run() {
						for (int j = 0; j < lookup; j++) {
							Charset.forName("UTF-8");
							Charset.forName("UTF-16");
							Charset.forName("ISO-8859-1");
							Charset.forName("US-ASCII");
						}
						latch.countDown();

					}
				});
			}
			try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			long ms = System.currentTimeMillis() - time;
			sum += ms;
			ts.put(t, ms);
			System.out.println(t + " threads and " + lookup + " lookups " + ms + "ms");
		}
		pool.shutdown();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Test ended in " + sum + "ms");
		return ts;
	}

	public static void showResult(Map<String, LinkedHashMap<Integer, Long>> data) {
		JFrame frame = new JFrame("Benchmark");

		final XYSeriesCollection dataset = new XYSeriesCollection();
		for (String title : data.keySet()) {
			final XYSeries series = new XYSeries(title);
			for (Entry<Integer, Long> d : data.get(title).entrySet()) {
				series.add(d.getKey(), d.getValue());
			}
			dataset.addSeries(series);
		}

		final JFreeChart chart = ChartFactory.createXYLineChart(null, "#threads", "ms", dataset,
				PlotOrientation.VERTICAL, true, true, false);
		chart.getPlot().setBackgroundPaint(new Color(220, 220, 220));
		final ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new Dimension(650, 350));
		frame.setContentPane(chartPanel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
		frame.setLocationRelativeTo(null);
	}
}
