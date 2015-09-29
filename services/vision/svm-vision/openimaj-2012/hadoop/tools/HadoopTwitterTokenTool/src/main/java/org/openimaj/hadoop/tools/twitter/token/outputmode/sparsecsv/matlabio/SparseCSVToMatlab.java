package org.openimaj.hadoop.tools.twitter.token.outputmode.sparsecsv.matlabio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.openimaj.hadoop.tools.twitter.token.outputmode.sparsecsv.TimeIndex;
import org.openimaj.hadoop.tools.twitter.token.outputmode.sparsecsv.WordIndex;
import org.openimaj.hadoop.tools.twitter.token.outputmode.sparsecsv.matlabio.SparseCSVToMatlab.WordTimeDFIDF;
import org.openimaj.hadoop.tools.twitter.utils.WordDFIDF;
import org.openimaj.util.pair.IndependentPair;

import com.Ostermiller.util.CSVParser;
import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLChar;
import com.jmatio.types.MLDouble;
import com.jmatio.types.MLSparse;
public class SparseCSVToMatlab {
	static class WordTimeDFIDF{
		int word;
		int time;
		WordDFIDF idf;
	}
	public static void main(String[] args) throws IOException {
		
		String sparseCSVRoot = "/Users/ss/Development/data/TrendMiner/sheffield/2010/09/tweets.2010-09.24hours.top100k.sparsecsv";
		String outfileName = "mat_file.mat";
		if(args.length > 0){
			sparseCSVRoot = args[0];
			if(args.length > 1){
				outfileName = args[1];
			}
		}
		
		LinkedHashMap<String, IndependentPair<Long, Long>> wordIndex = WordIndex.readWordCountLines(sparseCSVRoot);
		LinkedHashMap<Long, IndependentPair<Long, Long>> timeIndex = TimeIndex.readTimeCountLines(sparseCSVRoot);
		System.out.println("Preparing matlab files");

		MLCell wordCell = new MLCell("words",new int[]{wordIndex.size(),2});
		MLCell timeCell = new MLCell("times",new int[]{timeIndex.size(),2});
		
		System.out.println("... reading times");
		for (Entry<Long, IndependentPair<Long, Long>> ent : timeIndex.entrySet()) {
			long time = (long)ent.getKey();
			int timeCellIndex = (int)(long)ent.getValue().secondObject();
			long count = ent.getValue().firstObject();
			timeCell.set(new MLDouble(null, new double[][]{new double[]{time}}), timeCellIndex,0);
			timeCell.set(new MLDouble(null, new double[][]{new double[]{count}}), timeCellIndex,1);
		}
		
		System.out.println("... reading words");
		for (Entry<String, IndependentPair<Long, Long>> ent : wordIndex.entrySet()) {
			String word = ent.getKey();
			int wordCellIndex = (int)(long)ent.getValue().secondObject();
			long count = ent.getValue().firstObject();
			wordCell.set(new MLChar(null, word), wordCellIndex,0);
			wordCell.set(new MLDouble(null, new double[][]{new double[]{count}}), wordCellIndex,1);
		}
		
		System.out.println("... preapring values array");
		File valuesIn = new File(sparseCSVRoot,"values/part-r-00000");
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(valuesIn),"UTF-8"));
		int nValues = wordIndex.size() * timeIndex.size();
		MLSparse matarr = new MLSparse("values", new int[]{wordIndex.size(),timeIndex.size()}, 0, nValues);
		System.out.println("... reading values");
		String wholeLine = null;
		while((wholeLine = reader.readLine())!=null){
			StringReader strReader = new StringReader(wholeLine);
			CSVParser parser = new CSVParser(strReader);
			String[] line = parser.getLine();
			if(line == null){
				continue;
			}
			WordTimeDFIDF wtd = new WordTimeDFIDF();
			wtd.word = Integer.parseInt(line[0]);
			wtd.time = Integer.parseInt(line[1]);
			wtd.idf = new WordDFIDF();
			wtd.idf.timeperiod = timeCell.getIndex(wtd.time, 0);
			wtd.idf.wf = Integer.parseInt(line[2]);
			wtd.idf.tf = Integer.parseInt(line[3]);
			wtd.idf.Twf = Integer.parseInt(line[4]);
			wtd.idf.Ttf = Integer.parseInt(line[5]);
			
			matarr.set(wtd.idf.dfidf(), wtd.word, wtd.time);
		}
		System.out.println("writing!");
		ArrayList<MLArray> list = new ArrayList<MLArray>();
		list.add(wordCell);
		list.add(timeCell);
		list.add(matarr);
		new MatFileWriter(sparseCSVRoot + File.separator + outfileName,list );
	}
}
