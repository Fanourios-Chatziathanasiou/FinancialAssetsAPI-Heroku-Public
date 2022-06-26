package com.unipi.fchatziathanasiou.thirdpartyapi_db.technical_indicators;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MACD {

    private int shortPeriod,longPeriod,signalLinePeriod;
    private double[] shortEma,longEma,signalLine,histoGram;


//
//    public MACD(int shortPeriod, int longPeriod, int signalLinePeriod) {
//        this.shortPeriod = shortPeriod;
//        this.longPeriod = longPeriod;
//        this.signalLinePeriod = signalLinePeriod;
//        this.shortEma = ExponentialMovingAverage.calculateEmaValues()
//    }

//    MACD Line: (12-day EMA - 26-day EMA)
//
//    Signal Line: 9-day EMA of MACD Line
//
//    MACD Histogram: MACD Line - Signal Line

    public static JSONArray calculateMACD(JSONArray candlesticks,double shortPeriod,double longPeriod,double signalLinePeriod) throws JSONException {
        double [] shortEma,longEma;
        //double [] macdLine = new double[candlesticks.length()];
        double [] signalLine = new double[candlesticks.length()];
        shortEma = ExponentialMovingAverage.calculateEmaValues(candlesticks,shortPeriod);
        longEma = ExponentialMovingAverage.calculateEmaValues(candlesticks,longPeriod);
        JSONArray macdLine = new JSONArray();
        for (int i=0; i < shortEma.length;i++){
            //macdLine[i] = longEma[i]-shortEma[i];
            JSONObject js = new JSONObject();
            js.put("close",longEma[i]-shortEma[i]);
            //macdLine.put(longEma[i]-shortEma[i]);
            macdLine.put(i,js);
        }
       // System.out.println(macdLine);
        signalLine = ExponentialMovingAverage.calculateEmaValues(macdLine,signalLinePeriod);
        double[] macdHistogram = new double[candlesticks.length()];
        for (int i=0;i<macdLine.length();i++){
            macdHistogram[i] = macdLine.getJSONObject(i).getDouble("close")-signalLine[i];
        }
        JSONArray result = new JSONArray();
       for (int i=0;i<candlesticks.length();i++){
           JSONObject obj = new JSONObject();

           obj.put("MACD Line",macdLine.getJSONObject(i).get("close"));
           obj.put("signal Line",signalLine[i]);
           obj.put("MACD Histogram",macdHistogram[i]);
           result.put(i,obj);
       }

       return result;

    };


}
