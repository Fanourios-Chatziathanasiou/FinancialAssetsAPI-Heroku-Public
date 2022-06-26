package com.unipi.fchatziathanasiou.thirdpartyapi_db.technical_indicators;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;


public class ExponentialMovingAverage {


    /**
     * Calculates the Exponential moving average (EMA) of the given data
     * @param candlesticks
     * @param n : number of time periods to use in calculating the smoothing factor of the EMA
     * @return an array of EMA values
     */
    public static double[] calculateEmaValues(JSONArray candlesticks, double n){

        double[] results = new double[candlesticks.length()];

        try {
            calculateEmasHelper(candlesticks, n, candlesticks.length()-1, results);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    public static double calculateEmasHelper(JSONArray candlesticks, double n, int i, double[] results) throws JSONException {

        if(i == 0){
            results[0] =(Double) candlesticks.getJSONObject(0).get("close");
            return results[0];
        }else {
            double close =  candlesticks.getJSONObject(i).getDouble("close");
            double factor = ( 2.0 / (n +1) );
            double ema =  close * factor + (1 - factor) * calculateEmasHelper(candlesticks, n, i-1, results) ;
            results[i] = ema;
            return ema;
        }


    }

}
