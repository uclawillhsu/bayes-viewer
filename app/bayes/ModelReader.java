package bayes;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.text.*;
import java.util.*;
import java.util.List;

import com.google.gson.Gson;

import play.Logger;
import play.api.libs.json.Json;
import smile.*;
import smile.learning.*;

public class ModelReader
{
	private Network network;
	private DataSet dataSetForRawData;
	private DataSet dataSetForTestData;
	private int foldNum = 10;
	private int foldingRandSeed = 2;
	private Map<Integer, double[]> networkLastMap = new HashMap();
	private Map<Integer, Boolean> networkTargetMap = new HashMap();

	private Map<String, Map<String, Integer>> dataSetStateMapForRawData = new HashMap();
	private Map<String, Map<String, Integer>> dataSetStateMapForTestData = new HashMap();

	private Map<String, String> originalNodeAccuracyMap = new HashMap<String, String>();
	private Map<String, String> testNodeAccuracyMap = new HashMap<String, String>();
	private String[][] validationResultArrayForRawData = null;
	private String[][] validationResultArrayForTestData = null;

	private Gson gson;
	private String modelPath;

	public ModelReader()
	{
		gson = new Gson();
	}
	
	public Network getNetwork()
	{
		return network;
	}

	public DataSet getDataSetForRawData() { return dataSetForRawData; }
	public DataSet getDataSetForTestData() { return dataSetForTestData;}

	public void setFoldNum( int num ){
		this.foldNum = num;
	}

	public void setNetwork(Object network)
	{
		this.network = (Network) network;
	}

	public void setDataSetForRawData ( DataSet dataSet ) {
		this.dataSetForRawData = dataSet;
	}

	public void setDataSetForTestData ( DataSet dataSet) {
		this.dataSetForTestData = dataSet;
	}

	public void setDataSetStateMapForRawData( Map<String, Map<String, Integer>> dataSetStateMap ) {
		this.dataSetStateMapForRawData = dataSetStateMap;
	}

	public void setDataSetStateMapForTestData( Map<String, Map<String, Integer>> dataSetStateMap ) {
		this.dataSetStateMapForTestData = dataSetStateMap;
	}

	public Map<String, Map<String, Integer>> getDataSetStateMapForRawData() { return dataSetStateMapForRawData; }

	public Map<String, Map<String, Integer>> getDataSetStateMapForTestData() {
		return dataSetStateMapForTestData;
	}

	public void setModelPath(String path) {this.modelPath = path;}

	public String read(String modelPath)
	{
		loadModel(modelPath);
		return getModelStr();
	}

	public String readUpload(String modelPath, String modelName)
	{
		uploadModel(modelPath, modelName);
		return getModelStr();
	}

	public String readModelFromFileContent ( String modelFullName,
											 String modelXdslContent,
											 String algorithm ) {
		int algorithmType = 0;
		try {
			Field field = Network.BayesianAlgorithmType.class.getDeclaredField(algorithm);
			Object object = field.get(Network.BayesianAlgorithmType.class);
			algorithmType = field.getInt(object);
		} catch ( Exception ex ) {
			Logger.info("get algorithm type error:" + ex.toString());
		}

		network = new Network();
		network.readString( modelXdslContent );
		network.setName(modelFullName);
		network.setBayesianAlgorithm(algorithmType);

		return getModelStr();
	}

	public void modifyNetworkLast(){
		try{
			int[] nodes = network.getAllNodes();

			for (int i=0; i<nodes.length; i++) {
				double[] values = network.getNodeValue(nodes[i]);
				networkLastMap.put(nodes[i], values);
			}
		}catch(Exception e) {
			//e.printStackTrace();
			Logger.info("modifyNetworkLast fault:" + e.toString());
		}
	}

	public void recordNetworkTarget(){
		try{
			int[] nodes = network.getAllNodes();

			for (int i=0; i<nodes.length; i++) {
				//int node = nodes[i];
				String nodeID = network.getNodeId(nodes[i]);
				//String nodeID = network.getNodeId(node);
				//String nodeName = network.getNodeName(node);
				networkTargetMap.put(nodes[i], network.isTarget(nodeID));
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void recoverNetworkTarget(){
		try{
			int[] nodes = network.getAllNodes();

			for (int i=0; i<nodes.length; i++) {
				String nodeID = network.getNodeId(nodes[i]);
				if( networkTargetMap.get(nodes[i]) ) {
					//Logger.info(nodeID + " " + networkTargetMap.get(nodes[i]));
					network.setTarget(nodeID, true);
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	private String resultArrayToCsvString ( String[][] resultArray ) {
		String resultString = "";
		//Logger.info("resultArray i length=" + resultArray.length);
		//Logger.info("resultArray j length=" + resultArray[0].length);
		for( int i = 0; i < resultArray.length; i++) {
		//for( int i = 0; i < 6; i++) {
			for( int j = 0; j < resultArray[i].length; j++) {
				resultString += resultArray[i][j];
				if( j < resultArray[i].length - 1 ) {
					resultString += "$";
				}
			}
			if( i < resultArray.length - 1) {
			//if( i < resultArray.length ) {
			//if( i < 4 -1 ) {
				resultString += "@";
			}
		}
		//Logger.info("resultString=" + resultString);
		return resultString;
	}

	public String getValidationResultStr( boolean isTestDataSet ) {
		//Logger.info("modelReader getValidationResult coming with isTestDataSet=" + isTestDataSet);
		NumberFormat formatter = new DecimalFormat("#0.00");
		StringBuilder strBlder = new StringBuilder("[");

		if( !isTestDataSet && dataSetForRawData != null ) {
			originalNodeAccuracyMap = getValidationMap( dataSetForRawData,
					dataSetStateMapForRawData, false );

			if( originalNodeAccuracyMap.size() > 0 ) {
				if (validationResultArrayForRawData != null &&
						validationResultArrayForRawData.length > 0) {

					return	resultArrayToCsvString(validationResultArrayForRawData);
				}
			}
		}

		if( isTestDataSet && dataSetForTestData != null) {
			testNodeAccuracyMap = getValidationMap(
					dataSetForTestData, dataSetStateMapForTestData, true );
			if( testNodeAccuracyMap.size() > 0 ) {
				if (validationResultArrayForTestData != null &&
						validationResultArrayForTestData.length > 0) {

					return resultArrayToCsvString(validationResultArrayForTestData);
				}
			}
		}
		if( isTestDataSet ) {
			return "Error:The test data set didn't exist.";
		} else {
			return "Error:The raw data set didn't exist.";
		}
	}

	public String getModelStr() {
		//Logger.info("foldNum=" + foldNum);
		NumberFormat formatter = new DecimalFormat("#0.00");
		StringBuilder strBlder = new StringBuilder("[");

		if( dataSetForRawData != null ) {
			originalNodeAccuracyMap = getValidationMap( dataSetForRawData,
					dataSetStateMapForRawData, false );
		}

		if( dataSetForTestData != null) {
			testNodeAccuracyMap = getValidationMap(
					dataSetForTestData, dataSetStateMapForTestData, true );
		}

		try {
			recordNetworkTarget();
			network.clearAllTargets();
			network.updateBeliefs();
			recoverNetworkTarget();

			List<int[]> edgeList = new ArrayList<int[]>();
			boolean accuracyExist = false;

			if( originalNodeAccuracyMap.size() > 0 ) {
				strBlder.append("{\"originalNodeAcc\":\"true\"");
				accuracyExist = true;
				/*
				if (validationResultArrayForRawData != null &&
						validationResultArrayForRawData.length > 0) {

					String rawDataValidationResultString =
						resultArrayToCsvString(validationResultArrayForRawData);

					//strBlder.append(",\"rawDataValidationResult\":" +
					//	rawDataValidationResultString );
					//String test = "result,comma";
					strBlder.append(",\"rawDataValidationResult\":\"" + rawDataValidationResultString + "\"");
					//strBlder.append(",\"rawDataValidationResult\":\"" + "myResult&#13;" + "\"");
				}*/
			}

			if( testNodeAccuracyMap.size() > 0 ) {
				if( originalNodeAccuracyMap.size() > 0 ) {
					strBlder.append(",");
				} else {
					strBlder.append("{");
				}
				strBlder.append("\"testNodeAcc\":\"true\"");
				accuracyExist = true;
				/*
				if (validationResultArrayForTestData != null &&
						validationResultArrayForTestData.length > 0) {

					String testDataValidationResultString =
						resultArrayToCsvString(validationResultArrayForTestData);

					strBlder.append(",\"testDataValidationResult\":\"" +
						testDataValidationResultString + "\"");
				}*/
			}

			if(accuracyExist) {
				strBlder.append(",\"nodes\":[");
			}
			else {
				strBlder.append("{\"nodes\":[");
			}

			int[] nodes = network.getAllNodes();

			for (int i=0; i<nodes.length; i++) {
				int node = nodes[i];
				String nodeID = network.getNodeId(node);
				String nodeName = network.getNodeName(node);

				if(!isAllUpperCase(nodeName)) {
					//modify all first character of node name to lower case
					String nodeNameLastPart = nodeName.substring(1);
					String nodeNameFirstPart = nodeName.substring(0, 1);
					nodeName = nodeNameFirstPart.toLowerCase() + nodeNameLastPart;
				}

				Rectangle rect = network.getNodePosition(node);

				if (i > 0)
					strBlder.append(",");

				strBlder.append("{\"data\":{\"id\":\"" + nodeID + "\"," +
						"\"name\":\"" + nodeName + "\"," );

				strBlder.append("\"nameLabel\":\"" + nodeName );
				if( originalNodeAccuracyMap.size() > 0 || testNodeAccuracyMap.size() > 0 ) {
					strBlder.append("(");
					if( originalNodeAccuracyMap.size() > 0 ) {
						strBlder.append("IO:" + originalNodeAccuracyMap.get("internal" + nodeID));
						strBlder.append(", ");
						strBlder.append("EO:" + originalNodeAccuracyMap.get("external" + nodeID));
					}
					if( testNodeAccuracyMap.size() > 0 ) {
						if( originalNodeAccuracyMap.size() > 0 ) {
							strBlder.append(", ");
						}
						strBlder.append("IT:" + testNodeAccuracyMap.get("internal" + nodeID));
						strBlder.append(", ");
						strBlder.append("ET:" + testNodeAccuracyMap.get("external" + nodeID));
					}
					strBlder.append(")");
				}
				strBlder.append("\"}, \"position\":{\"x\":" + rect.x + ", \"y\":" + rect.y + "}}");

				//edges
				int[] childrenIDs = network.getChildren(node);
				for (int j=0; j<childrenIDs.length; j++) {
					int[] arcIDs = new int[2];
					arcIDs[0] = node;
					arcIDs[1] = childrenIDs[j];
					edgeList.add(arcIDs);
				}
			}
			strBlder.append("], \"edges\":[");
			//edges
			int count = 0;
			for (int[] arcIDs : edgeList) {
				if (count > 0)
					strBlder.append(",");
				strBlder.append("{\"data\":{\"id\":\"" + arcIDs[0] + "|" + arcIDs[1] + "\",\"source\":\"" + network.getNodeId(arcIDs[0]) + "\",\"target\":\"" + network.getNodeId(arcIDs[1]) + "\"}}");
				count++;
			}

			strBlder.append("]},");

			//node values
			strBlder.append("[");
			Map<String, String> outcomeMap = new TreeMap<String, String>();

			for (int i=0; i<nodes.length; i++) {
				StringBuilder outcomeBlder = new StringBuilder();
				String nodeName = network.getNodeName(nodes[i]);
				String nodeID = network.getNodeId(nodes[i]);
				/*boolean isSearch = false;
				if( network.getNodeBgColor(nodeID) == java.awt.Color.PINK ) {
					isSearch = true;
				}*/
				if( !isAllUpperCase(nodeName) ) {
					//modify all first character of node name to lower case
					String nodeNameLastPart = nodeName.substring(1);
					String nodeNameFirstPart = nodeName.substring(0, 1);
					nodeName = nodeNameFirstPart.toLowerCase() + nodeNameLastPart;
				}
				outcomeBlder.append("{\"nodename\":\"" + nodeName + "\", \"id\":\"" + network.getNodeId(nodes[i]) + "\", ");

				//adding isVirtualEvidence, is RealEvidence and isTarget for each node
				outcomeBlder.append("\"isVirtualEvidence\":\"" + network.isVirtualEvidence(nodeID) + "\", ");
				outcomeBlder.append("\"isRealEvidence\":\"" + network.isRealEvidence(nodeID) + "\", ");
				outcomeBlder.append("\"isTarget\":\"" + network.isTarget(nodeID) + "\", ");

				String[] outcomeIDs = network.getOutcomeIds(nodes[i]);
				double[] valuesLast = networkLastMap.get(nodes[i]);

				//adding virtualEvidenceValue
				if( network.isVirtualEvidence(nodeID) ) {
					outcomeBlder.append("\"values\":[");
					double[] virtualEvidenceValues = network.getVirtualEvidence(nodes[i]);

					for (int j = 0; j < outcomeIDs.length; j++) {
						String change = "no";
						if( valuesLast != null ){
							if(virtualEvidenceValues[j] > valuesLast[j]) {
								change = "increase";
							}
							if(virtualEvidenceValues[j] < valuesLast[j]){
								change = "decrease";
							}
						}
						if (j > 0)
							outcomeBlder.append(",");
						outcomeBlder.append("{\"outcomeid\":\"" + outcomeIDs[j] + "\", " +
							"\"change\":\"" + change + "\", " +
							"\"value\":" + formatter.format((virtualEvidenceValues[j])) + "}");
					}
				} else {
					outcomeBlder.append("\"values\":[");
					double[] values = network.getNodeValue(nodes[i]);
					for (int j = 0; j < outcomeIDs.length; j++) {
						String change = "no";
						if (valuesLast != null) {
							if( Double.valueOf(formatter.format(values[j])) >
									Double.valueOf(formatter.format(valuesLast[j])) ) {
								change = "increase";
							} else if ( Double.valueOf(formatter.format(values[j])) <
									Double.valueOf(formatter.format(valuesLast[j])) ) {
								change = "decrease";
							}
						}
						if (j > 0)
							outcomeBlder.append(",");
						outcomeBlder.append("{\"outcomeid\":\"" + outcomeIDs[j] + "\", " +
								"\"change\":\"" + change + "\", " +
								"\"value\":" + formatter.format((values[j])) + "}");
					}
				}
				outcomeBlder.append("]}");
				outcomeMap.put(nodeName, outcomeBlder.toString());
			}

			count = 0;
			for (Map.Entry<String, String> entry : outcomeMap.entrySet()) {
				if (count > 0)
					strBlder.append(",");
				strBlder.append(entry.getValue());
				count++;
			}

			strBlder.append("]");
		} catch (Exception e) {
			recoverNetworkTarget();
			String message = e.toString();
			if( message.contains("Logged information:") ) {
				String[] infoArray = message.split("Logged information:");
				message = infoArray[1];
			}
			return "Error:" + message + " Please try to change another inference algorithm."; // value is not valid.
		}
		strBlder.append("]");
		return strBlder.toString();
	}

	private Map<String, String> getValidationMap (DataSet dataSet,
												  Map<String, Map<String, Integer>> dataSetStateMap,
												  boolean isTestData) {

		Map<String, String> validationNodeAccuracyMap = new HashMap<String, String>();
		int numDataSetColumn = dataSet.getVariableCount();
		int totalRecord = dataSet.getRecordCount();
		DecimalFormat numberFormat = new DecimalFormat("0.00");
		DecimalFormat floatNumberFormat = new DecimalFormat("0.0000");
		// ***get maximal state number***
		int[] nodes = network.getAllNodes();
		recordNetworkTarget();

		int numOfObservationNode = 0;
		int maxStateNum = 0;
		for (int i = 0; i < nodes.length; i++) {
			String nodeID = network.getNodeId(nodes[i]);
			String[] outcomeIDs = network.getOutcomeIds(nodes[i]);
			if( outcomeIDs.length > maxStateNum ) {
				maxStateNum = outcomeIDs.length;
			}
			if( network.isTarget(nodeID) ) {
				numOfObservationNode += outcomeIDs.length * 2 + 2;
				//Logger.info("ob for nodeID=" + nodeID);
			}
			/*
			else {
				Logger.info("ob not for nodeID=" + nodeID);
			}*/
		}
		String[][] validationResultArray;
		if( numOfObservationNode > 0 ) {
			validationResultArray =
					new String[totalRecord + 2 + maxStateNum][numDataSetColumn + numOfObservationNode];
			int row = totalRecord + 2 + maxStateNum;
			int col = numDataSetColumn + numOfObservationNode;
			//Logger.info("observation=" + numOfObservationNode + " and resultArray row=" + row + " and col=" + col );
		} else {
			validationResultArray =
					new String[totalRecord + 2 + maxStateNum][numDataSetColumn*3];
		}

		int currentColumnIndex = numDataSetColumn;
		try {

			ArrayList<DataMatch> tempMatching = new ArrayList<DataMatch>();

			for (int col = 0; col < numDataSetColumn; col++) {
				//get name of current column in the data set
				String colName = dataSet.getVariableId(col);

				validationResultArray[0][col] = colName;
				if( numOfObservationNode == 0 ) {
					validationResultArray[0][numDataSetColumn + col] = colName + "_IP";
					validationResultArray[0][numDataSetColumn * 2 + col] = colName + "_EP";
				}

				String[] stateNameArray = dataSet.getStateNames(col);

				for( int row=0; row < totalRecord; row++ ) {
				//for( int row=0; row < 3; row++ ) {
					int stateSeqNum = dataSet.getInt(col, row);
					String stateLabel = stateNameArray[stateSeqNum];
					validationResultArray[row+1][col] = stateLabel;
				}

				String curNodeName = colName;
				int curSlice = 0;

				if (Arrays.asList(network.getAllNodeIds()).contains(curNodeName)) {
					int nodeNum = network.getNode(curNodeName);
					tempMatching.add(new DataMatch(col, nodeNum, curSlice)); //associate: column, node, slice
				}
				/*else {
					Logger.info("getValidationMap: No node found for columnname: " + colName);
				}*/
			}

			//Convert dataMatch array
			DataMatch[] matches = tempMatching.toArray(new DataMatch[tempMatching.size()]);
			for (int i = 0; i < nodes.length; i++) {
				int node = nodes[i];
				String nodeID = network.getNodeId(node);
				if( numOfObservationNode > 0 && !network.isTarget(nodeID) ) {
					continue;
				}
				//Logger.info("currentColumnIndex=" + currentColumnIndex + " for nodeID=" + nodeID);
				network.clearAllTargets();
				Validator validator = new Validator(network, dataSet, matches);
				validator.addClassNode(nodeID);
				EM em = new EM();
				em.setEqSampleSize(1);  //same as confidence in GeNIe
				em.setRandomizeParameters(false);
				em.setUniformizeParameters(true);

				validator.kFold(em, foldNum, foldingRandSeed);  //10 is K-foldCount
				recoverNetworkTarget();

				int totalCorrectCaseNum = 0;
				String[] outcomeIDs = network.getOutcomeIds(nodes[i]);
				String[] curNodeStateNameArray = dataSet.getStateNames(i);
				String[] totalStateNameArray = new String[outcomeIDs.length];

				if( totalStateNameArray.length != curNodeStateNameArray.length) {
					System.arraycopy(curNodeStateNameArray, 0,
							totalStateNameArray, 0, curNodeStateNameArray.length);
					int stateIndex = curNodeStateNameArray.length;
					for (int m = 0; m < outcomeIDs.length; m++) {
						String state = outcomeIDs[m];
						List<String> totalStateList =
								Arrays.asList(totalStateNameArray);
						if (!totalStateList.contains(state)) {
							totalStateNameArray[stateIndex] = state;
							stateIndex++;
						}
					}
				} else {
					totalStateNameArray = curNodeStateNameArray;
				}
				/*
				for( int m=0; m< totalStateNameArray.length; m++){
					Logger.info("totalStateNameArray for m=" + m + " is " + totalStateNameArray[m]);
				}*/
				//deal with missed state by finding index corresponding to the state
				Map<String, Integer> stateToIndexMap = new HashMap<String, Integer>();
				for( int m=0; m<totalStateNameArray.length; m++ ){
					int realIndex = 0;
					String state = totalStateNameArray[m];
					for( int n=0; n<outcomeIDs.length; n++ ) {
						if( outcomeIDs[n].equals(state) ) {
							stateToIndexMap.put(totalStateNameArray[m], n);
							break;
						}
					}
				}
				for (int j = 0; j < outcomeIDs.length; j++) {
					String outcomeIdLabel = outcomeIDs[j];
					String stateAccSummary = "";
					int index = stateToIndexMap.get(outcomeIdLabel);
					String networkStateLabel = outcomeIDs[index];

					double accuracy = validator.getAccuracy(nodeID,  networkStateLabel); //real state label
					/*if( j==3 || j== 4) {
						Logger.info("accuracy=" + accuracy + " format=" + numberFormat.format(accuracy).toString());
					}*/
					Map<String, Integer> stateCountMap = dataSetStateMap.get(nodeID);

					if (stateCountMap.get(outcomeIdLabel) != null) {
						int stateCount = stateCountMap.get(outcomeIdLabel);
						int correctCaseNum = (int) (accuracy * stateCount);

						totalCorrectCaseNum += correctCaseNum;
						if( Double.isNaN(accuracy) ) {
							stateAccSummary += outcomeIdLabel + " = 0";
						} else {
							stateAccSummary += outcomeIdLabel + " = " +
									numberFormat.format(accuracy).toString();
						}
						stateAccSummary += " (" + correctCaseNum + "/" + stateCount + ")";
					} else {
						stateAccSummary += outcomeIdLabel + " = -nan(ind)";
						stateAccSummary += " (0/0)";
					}
					if( numOfObservationNode == 0 ) {
						validationResultArray[totalRecord+1+j][i+numDataSetColumn]
								= stateAccSummary;
					} else {
						validationResultArray[totalRecord+1+j][currentColumnIndex+outcomeIDs.length]
								= stateAccSummary;
						//Logger.info("ob is target for nodeID=" + nodeID + " stateAccSum=" + stateAccSummary);
					}
				}
				double nodeAccuracy = (double)totalCorrectCaseNum / (double)totalRecord;

				String nodeAccuracyFormat = numberFormat.format(nodeAccuracy).toString();
				String internalValidationNodeID = "internal" + nodeID;
				validationNodeAccuracyMap.put(internalValidationNodeID, nodeAccuracyFormat);

				DataSet resultDataSet = validator.getResultDataSet();

				for( int row=0; row < totalRecord ; row++ ) {
				//for( int row=0; row < 3 ; row++ ) {
					//validationResultArray[row+1][col] = dataSet.getInt(row, col);
					int stateSeqNum = resultDataSet.getInt(numDataSetColumn+outcomeIDs.length, row);
					String stateLabel = totalStateNameArray[stateSeqNum];
					if( numOfObservationNode == 0 ) {
						validationResultArray[row + 1][i + numDataSetColumn] = stateLabel;
					} else {
						for( int z=0; z< outcomeIDs.length; z++ ) {
							if( numOfObservationNode > 0  && row == 0 ) {
								validationResultArray[0][currentColumnIndex + z] =
										resultDataSet.getVariableId(numDataSetColumn+z) + "_I";
							}
							float prob = resultDataSet.getFloat(numDataSetColumn+z, row);
							String probString = floatNumberFormat.format(prob).toString();
							int realStateIndex = (int)stateToIndexMap.get(totalStateNameArray[z]);

							validationResultArray[row + 1][currentColumnIndex + realStateIndex] = probString;
							/*Logger.info("nodeId=" + nodeID + " z="+ z + " and realStateIndex=" +
									realStateIndex + " prob=" + probString);
							*/
							//int columnIndex = currentColumnIndex + z;
							//Logger.info("enter probString=" + probString + " columnIndex=" + columnIndex);
						}
						if( numOfObservationNode > 0 && row == 0 ) {
							validationResultArray[0][currentColumnIndex + outcomeIDs.length] =
										nodeID + "_IP";
						}
						validationResultArray[row + 1][currentColumnIndex + outcomeIDs.length] = stateLabel;
						//Logger.info("enter probString for target nodeID=" + nodeID);
					}
				}

				String accSummary = nodeID + " = " + nodeAccuracyFormat +
						" (" + totalCorrectCaseNum + "/" + totalRecord + ")";

				if( numOfObservationNode == 0 ) {
					validationResultArray[totalRecord + 1 + outcomeIDs.length][i + numDataSetColumn] = accSummary;
				} else if( network.isTarget(nodeID) ) {
					validationResultArray[totalRecord + 1 + outcomeIDs.length][currentColumnIndex + outcomeIDs.length]
							= accSummary;
					currentColumnIndex += outcomeIDs.length + 1;
				}

				/*
				if( i == 1 && totalRecord == 103 ) {
					File tmpFile = new File("/tmp/validationResultDataSetNewTest_new");
					if( !tmpFile.exists() ) {
						tmpFile.createNewFile();
					}
					resultDataSet.writeFile(tmpFile.getAbsolutePath());
				}*/
			}
			//Logger.info("after IP currentColumnIndex=" + currentColumnIndex);
		} catch (Exception ex) {
			Logger.info("getValidationMap: cross validation matches exception is " + ex.toString());
		}


		//The followings are the external validation
		try{
			for(int col = 0; col < numDataSetColumn; col++) {
				String curNodeId = dataSet.getVariableId(col);
				if (numOfObservationNode > 0 && !network.isTarget(curNodeId) ) {
					continue;
				}
				String[] curOutcomeIDs = network.getOutcomeIds(curNodeId);

				int[] curStatePredictCorrectNum = new int[curOutcomeIDs.length];
				for (int i = 0; i < curStatePredictCorrectNum.length; i++) {
					curStatePredictCorrectNum[i] = 0;
				}

				Map<String, Integer> stateCountMap = dataSetStateMap.get(curNodeId);
				String[] curNodeStateNameArray = dataSet.getStateNames(col);
				int totalCorrectPredictiveCase = 0;

				for( int row=0; row < totalRecord ; row++ ) {
				//for (int row = 0; row < 3; row++) {
					network.clearAllEvidence();
					int realStateSeqNum = dataSet.getInt(col, row);

					for (int i = 0; i < dataSet.getVariableCount(); i++) {
						if (i != col) {
							int stateSeqNum = dataSet.getInt(i, row);
							String[] nodeStateNameArray = dataSet.getStateNames(i);
							network.setEvidence(dataSet.getVariableId(i), nodeStateNameArray[stateSeqNum]);
						}
					}
					network.clearAllTargets();
					network.updateBeliefs();
					recoverNetworkTarget();

					double[] values = network.getNodeValue(curNodeId);
					double max = values[0];
					int maxIndex = 0;
					for (int count = 0; count < values.length; count++) {
						if (values[count] > max) {
							max = values[count];
							maxIndex = count;
						}
						if (numOfObservationNode > 0) {
							validationResultArray[row + 1][currentColumnIndex + count]
									= floatNumberFormat.format(values[count]).toString();
							if (row == 0) {
								validationResultArray[0][currentColumnIndex + count]
										= curNodeId + "_" + curOutcomeIDs[count] + "_E";
							}
						}
					}
					if( numOfObservationNode > 0 && row == 0 ) {
						validationResultArray[0][currentColumnIndex + curOutcomeIDs.length]
									= curNodeId + "_EP";
					}

					String[] outcomeIDs = network.getOutcomeIds(curNodeId);
					if (numOfObservationNode == 0) {
						validationResultArray[row + 1][col + numDataSetColumn * 2] =
								outcomeIDs[maxIndex];
					} else {
						validationResultArray[row + 1][currentColumnIndex + outcomeIDs.length]
								= outcomeIDs[maxIndex];
					}

					if (curNodeStateNameArray[realStateSeqNum].equals(outcomeIDs[maxIndex])) {
						totalCorrectPredictiveCase++;
						curStatePredictCorrectNum[maxIndex]++;
					}
				}

				double nodeAcc = (double) totalCorrectPredictiveCase /
						(double) dataSet.getRecordCount();
				String externalValidationProb = numberFormat.format(nodeAcc);
				String externalValidationNodeId = "external" + curNodeId;
				validationNodeAccuracyMap.put(externalValidationNodeId,
						externalValidationProb);

				for (int i = 0; i < curOutcomeIDs.length; i++) {
					String outcomeIdLabel = curOutcomeIDs[i];
					String stateAccSummary = "";
					if (stateCountMap.get(outcomeIdLabel) != null) {
						int stateCount = stateCountMap.get(outcomeIdLabel);
						double accuracy = (double) curStatePredictCorrectNum[i] /
								(double) stateCount;
						stateAccSummary += outcomeIdLabel + " = " +
								numberFormat.format(accuracy).toString() + " (";
						stateAccSummary += curStatePredictCorrectNum[i] + "/" +
								stateCount + ")";
					} else {
						stateAccSummary += outcomeIdLabel + " = ";
						stateAccSummary += "-nan(ind) (0/0)";
					}
					if( numOfObservationNode == 0 ) {
						validationResultArray[totalRecord+1+i][col+numDataSetColumn*2]
								= stateAccSummary;
					} else {
						validationResultArray[totalRecord+1+i][currentColumnIndex+curOutcomeIDs.length]
								= stateAccSummary;
					}
				}

				String accSummary = curNodeId + " = " + externalValidationProb +
						" (" + totalCorrectPredictiveCase + "/" + dataSet.getRecordCount() + ")";

				if (numOfObservationNode == 0) {
					validationResultArray[totalRecord + 1 + curOutcomeIDs.length][col + numDataSetColumn * 2]
							= accSummary;
				} else {
					validationResultArray[totalRecord + 1 + curOutcomeIDs.length][currentColumnIndex + curOutcomeIDs.length]
							= accSummary;
					currentColumnIndex += curOutcomeIDs.length + 1;
				}
			}
			network.clearAllEvidence();
			//Logger.info("after EP currentColumnIndex=" + currentColumnIndex);
		} catch(Exception ex ){
			Logger.error("getValidationMap: external validation exception=" + ex.toString());
		}
		/*
		Logger.info("output array result");
		if( numOfObservationNode > 0 ) {
			for( int row=0; row<3; row++) {
				for(int col=0; col<validationResultArray[row].length; col++) {
				//for(int col=0; col < 14; col++) {
					Logger.info(validationResultArray[row][col]);
				}
				Logger.info("\n");
			}
		}*/

		if( isTestData ) {
			this.validationResultArrayForTestData = validationResultArray;
		} else {
			this.validationResultArrayForRawData = validationResultArray;
		}
		return validationNodeAccuracyMap;
	}

	public boolean isAllUpperCase( String nodeName) {
		for(int i=0; i<nodeName.length(); i++) {
			if(Character.isLowerCase(nodeName.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public String setEvidence(String nodeID, String outcomeID) {
		//loadModel(modelName);
		modifyNetworkLast();
		network.setEvidence(nodeID, outcomeID);
		return getModelStr();
	}
	
	public String setVirtualEvidence(String modelName, String nodeID, double[] outcomeVals) {
		modifyNetworkLast();
		network.setVirtualEvidence(nodeID, outcomeVals);
		return getModelStr();
	}
	
	public String clearAllEvidence(String modelName) {
		//loadModel(modelName);
		modifyNetworkLast();
		//network.clearAllTargets();
		network.clearAllEvidence();
		//Logger.info("clearAllEvidence.");
		return getModelStr();
	}
	
	public String clearEvidence(String modelName, String nodeID)
	{
		//loadModel(modelName);
		modifyNetworkLast();
		//network.clearAllTargets();
		network.clearEvidence(nodeID);
		return getModelStr();
	}
	
	public String setAsTarget(String modelName, String nodeID)
	{
		//loadModel(modelName);
		/*if( !hasEvidence(network) ){
			return "Error: Please set an evidence first.";
		}*/
		network.setTarget(nodeID, true);
		//Logger.info("set a target.");
		return getModelStr();
	}

	public boolean hasEvidence(Network network) {
		boolean hasEvidence = false;
		try {
			int[] nodes = network.getAllNodes();
			for (int i = 0; i < nodes.length; i++) {
				String nodeID = network.getNodeId(nodes[i]);
				if(network.isRealEvidence(nodeID) || network.isVirtualEvidence(nodeID)){
					hasEvidence = true;
					break;
				}
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return hasEvidence;
	}

	public String removeTarget(String modelName, String nodeID)
	{
		//loadModel(modelName);
		network.setTarget(nodeID, false);

		return getModelStr();
	}

	public String clearAll(String modelName)
	{
		//loadModel(modelName);
		network.clearAllTargets();
		network.clearAllEvidence();
		return getModelStr();
	}

	public String clearAllTargets(String modelName)
	{
		//loadModel(modelName);
		network.clearAllTargets();
		
		return getModelStr();
	}
	
	public String getCPT(String modelName, String nodeID)
	{
		StringBuilder strBlder = new StringBuilder("{\"parents\":[");
		String[] parentIDs = network.getParentIds(nodeID);
		for (int i=0; i<parentIDs.length; i++) {
			String parentName = network.getNodeName(parentIDs[i]);
			if (i > 0)
				strBlder.append(",");
			
			strBlder.append("{\"parentName\":\"" + parentName + "\",\"outcomeIDs\":[");
			String[] outcomeIDs  = network.getOutcomeIds(parentIDs[i]);
			
			for (int j=0; j<outcomeIDs.length; j++) {
				if (j > 0)
					strBlder.append(",");
				strBlder.append("\"" + outcomeIDs[j] + "\"");
			}
			
			strBlder.append("]}");
		}
		
		strBlder.append("],\"outcomeIDs\":[");
		
		String[] outcomeIDs = network.getOutcomeIds(nodeID);
		for (int i=0; i<outcomeIDs.length; i++) {
			if (i > 0)
				strBlder.append(",");
			strBlder.append("\"" + outcomeIDs[i] + "\"");
		}
		
		strBlder.append("],\"definition\":[");
		double[] definition = network.getNodeDefinition(nodeID);
		for (int i=0; i<definition.length; i++) {
			if (i > 0)
				strBlder.append(",");
			strBlder.append(definition[i]);
		}
		
		strBlder.append("]}");
		
		return strBlder.toString();
	}

	private void loadModel(String modelName)
	{
		if (network == null) {
			Logger.info("starting loadModel...");
			network = new Network();
			network.readFile(modelName);

			truncateNames();
			network.setName(modelName);
		}
	}

	private void uploadModel(String modelPath, String modelName)
	{
		if (network == null) {
			Logger.info("starting uploadModel...");
			network = new Network();
			network.readFile(modelPath);

			truncateNames();
			network.setName(modelName);
		}
	}

	private void truncateNames() {
		int[] nodes = network.getAllNodes();
		int maxLength = 10;
		for (int i=0; i<nodes.length; i++) {
			int node = nodes[i];
			String nodeID = network.getNodeId(node);
			String nodeName = network.getNodeName(node);

			if (nodeName.length() > maxLength) {
				nodeName = nodeName.substring(0, maxLength);
			} else {
				nodeName = String.format("%-" + maxLength + "s", nodeName);
			}
			network.setNodeName(nodeID, nodeName);
		}
	}
}
