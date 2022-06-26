package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unipi.fchatziathanasiou.thirdpartyapi_db.technical_indicators.ExponentialMovingAverage;
import com.unipi.fchatziathanasiou.thirdpartyapi_db.technical_indicators.MACD;
import com.unipi.fchatziathanasiou.thirdpartyapi_db.technical_indicators.SimpleMovingAverage;
import com.unipi.fchatziathanasiou.thirdpartyapi_db.tools.CustomLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.json.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;


@RestController
public class AppController {
    @Autowired
    private FinancialAssetRepository financialAssetRepository;
    @Autowired
    private DBAssetsTrackerRepository dbAssetsTrackerRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private AssetsListRepository assetsListRepository;

    public static CopyOnWriteArrayList<CustomLock> pendingDbAssets = new CopyOnWriteArrayList<CustomLock>();
    private static final Logger log = LoggerFactory.getLogger(AppController.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Value("${RAPIDAPI_KEY}")
    private static String rapidapi_key;


    @Scheduled(fixedRate = 1000000)
    public void updateFinancialAssetsList() throws JSONException {
        JSONObject obj = new JSONObject((String) getAssetsListFromRapidApi());
        JSONArray assetValuesArr = obj.getJSONArray("data");
        CopyOnWriteArrayList<AssetsList> alist = new CopyOnWriteArrayList<AssetsList>();
        for (int i = assetValuesArr.length() - 1; i >= 0; i--) {
            //Create a financialAsset Object and fill the data in order to store it into MongoDB.(will be used in the for loop below),
            //********DECLARING BEFORE THE FOR LOOP STORES ONLY ONE OBJECT IN MONGODB ******

            //date reshaping needs a better approach.
            //updating the datetime here provides us with the datetime we need to store to the dBAssetsTracker , at the end of the for loop.
            //Which is the last Close Value.
            alist.add(saveAssetsListToDatabase(assetValuesArr.getJSONObject(i)));
        }
        int size = alist.size();
        ArrayList<AssetsList> sharedNames = (ArrayList<AssetsList>) assetsListRepository.findAll();
        for (AssetsList aslst : sharedNames){
            for (AssetsList alst : alist){
                if (aslst.name.equals(alst.getName())){
                    alist.remove(alst);
                }
            }
        }
        assetsListRepository.saveAll(alist);
        log.info("{} Available Assets Updated", dateFormat.format(new Date()));

    }

    @CrossOrigin
    @GetMapping("/symbol={symbol}&inteval={interval}")
    public String provideFinancialAsset (@PathVariable("symbol") String symbol,@PathVariable("interval") String interval) throws JSONException {
        Map<String,String> body = new HashMap<>();
        body.put("symbol",symbol);
        body.put("interval",interval);
        //Check if the Requested Asset Exists in the Database.
        DBAssetsTracker dbAssetsTracker = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(symbol,interval);
        financialAssetRepository.setCollectionName(body.get("symbol")+"_"+body.get("interval"));
        //If the Requested Asset was not found in the MongoDB database...
        String response;
//        Document result =this.mongoTemplate.executeCommand("{ serverStatus: 1 }");

        if (dbAssetsTracker == null) {
            CustomLock lock = null;
            for (CustomLock c_lock: pendingDbAssets){
                if (c_lock.getLockName().equals(body.get("symbol")+"_"+body.get("interval"))){
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return provideFinancialAsset(body.get("symbol"),body.get("interval"));
                }
            }
            if (lock== null){
                lock = new CustomLock();
                lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
                lock.setLock(new ReentrantLock());
                try {
                    pendingDbAssets.add(lock);
                    lock.getLock().lock();

                    response = insertNewAssetToMongoDB(body);
                    dbAssetsTracker = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"),body.get("interval"));
                    pendingDbAssets.remove(lock);

                }finally {
                    lock.getLock().unlock();
                }
            }
        }
        //if it does exist search in the Financial Assets document and return the data sorted by date.
        else {
            //Indicates the Date at which the Market is Trading right now.
            LocalDateTime ldt = ZonedDateTime.now(ZoneId.of(dbAssetsTracker.exchangeTimezone)).toLocalDateTime();
            LocalDateTime dbAssetsTrackerLastUpdated = LocalDateTime.ofInstant(dbAssetsTracker.getLastUpdate(),ZoneOffset.UTC);
            //check for updates before serving the data to the user.
            if (ldt.toInstant(ZoneOffset.UTC).isAfter(dbAssetsTrackerLastUpdated.toInstant(ZoneOffset.UTC))) {
                CustomLock lock = null;
                for (CustomLock c_lock: pendingDbAssets){
                    if (c_lock.getLockName().equals(body.get("symbol")+"_"+body.get("interval"))){
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return provideFinancialAsset(body.get("symbol"),body.get("interval"));
                    }
                }
                if (lock== null){
                    lock = new CustomLock();
                    lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
                    lock.setLock(new ReentrantLock());
                    try {
                        pendingDbAssets.add(lock);
                        lock.getLock().lock();
                        response = insertDataOfExistingAssetToMongoDB(body,ldt);
                        pendingDbAssets.remove(lock);

                    }finally {
                        lock.getLock().unlock();
                    }
                }
               // return response;
            }
        }

        for (CustomLock c_lock: pendingDbAssets) {
            if (c_lock.getLockName().equals(body.get("symbol") + "_" + body.get("interval"))) {
                try {
                    Thread.sleep(3000);
                    provideFinancialAsset(body.get("symbol"),body.get("interval"));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!(dbAssetsTracker == null)){
            //metadata in string
            String meta = financialAssetsFromDBtoString(dbAssetsTracker);
            //values in string
            String fastring = financialAssetsFromDBtoString(financialAssetRepository.findAllByOrderByDatetimeDesc());
            //If it exists in the DB then it should contain Data.
            return generateResultObject(meta, fastring);
        }else {
            return "Error:This financial Asset might not exist or cannot be provided by the database";
        }


    }

    @CrossOrigin
    @GetMapping("/symbol={symbol}&inteval={interval}/start={startdate}&end={enddate}")
    public String provideFinancialAssetInRange (@PathVariable("symbol") String symbol,@PathVariable("interval") String interval,@PathVariable("startdate") String start, @PathVariable("enddate") String end) throws JSONException {
        Map<String,String> body = new HashMap<>();
        body.put("symbol",symbol);
        body.put("interval",interval);
        body.put("startDate",start);
        body.put("endDate",end);
        Instant startDate = LocalDate.parse(body.get("startDate")).atStartOfDay().plusHours(16).toInstant(ZoneOffset.UTC);
        Instant endDate = LocalDate.parse(body.get("endDate")).atStartOfDay().plusHours(16).toInstant(ZoneOffset.UTC);
        //Check if the Requested Asset Exists in the Database.
        DBAssetsTracker dbAssetsTracker = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"),body.get("interval"));
        financialAssetRepository.setCollectionName(body.get("symbol")+"_"+body.get("interval"));
        String response = null;
        if (dbAssetsTracker == null ){
            CustomLock lock = null;
//            lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
//            lock.setLock(new ReentrantLock());
            for (CustomLock c_lock: pendingDbAssets){
                if (c_lock.getLockName().equals(body.get("symbol")+"_"+body.get("interval"))){
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return provideFinancialAssetInRange(body.get("symbol"),body.get("interval"),body.get("startDate"),body.get("endDate"));
                }
            }
            if (lock== null){
                lock = new CustomLock();
                lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
                lock.setLock(new ReentrantLock());
                try {
                    pendingDbAssets.add(lock);
                    lock.getLock().lock();

                    response = insertNewAssetToMongoDB(body);
                    dbAssetsTracker = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"),body.get("interval"));
                    pendingDbAssets.remove(lock);

                }finally {
                    lock.getLock().unlock();
                }
            }
            if (response != null && dbAssetsTracker!=null){

                //metadata in string
                String meta = financialAssetsFromDBtoString(dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"),body.get("interval")));
                //values in string

                return generateResultObjectInRange(startDate, endDate, meta);

            }else {
                return "Error:This financial Asset might not exist or cannot be provided by the database";
            }

            //if it does exist search in the Financial Assets document and return the data sorted by date.
        }else {
            LocalDateTime ldt = ZonedDateTime.now(ZoneId.of(dbAssetsTracker.exchangeTimezone)).toLocalDateTime();
            LocalDateTime dbAssetsTrackerLastUpdated = LocalDateTime.ofInstant(dbAssetsTracker.getLastUpdate(),ZoneOffset.UTC);
            //check for updates before serving the data to the user.
            if (ldt.toInstant(ZoneOffset.UTC).isAfter(dbAssetsTrackerLastUpdated.toInstant(ZoneOffset.UTC))) {
                // return response; failed
                CustomLock lock = null;
//            lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
//            lock.setLock(new ReentrantLock());
                for (CustomLock c_lock: pendingDbAssets){
                    if (c_lock.getLockName().equals(body.get("symbol")+"_"+body.get("interval"))){
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return provideFinancialAsset(body.get("symbol"),body.get("interval"));
                    }
                }
                if (lock== null){
                    lock = new CustomLock();
                    lock.setLockName(body.get("symbol")+"_"+body.get("interval"));
                    lock.setLock(new ReentrantLock());
                    try {
                        pendingDbAssets.add(lock);
                        lock.getLock().lock();
                        response = insertDataOfExistingAssetToMongoDB(body,ldt);
                        pendingDbAssets.remove(lock);

                    }finally {
                        lock.getLock().unlock();
                    }
                }
            }

        }
        for (CustomLock c_lock: pendingDbAssets) {
            if (c_lock.getLockName().equals(body.get("symbol") + "_" + body.get("interval"))) {
                try {
                    Thread.sleep(3000);
                    provideFinancialAsset(body.get("symbol"),body.get("interval"));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (!(response == null)){
            //metadata in string
            String meta = financialAssetsFromDBtoString(dbAssetsTracker);
            //values in string
            String fastring = financialAssetsFromDBtoString(financialAssetRepository.findAllByOrderByDatetimeDesc());
            //If it exists in the DB then it should contain Data.
            return generateResultObjectInRange(startDate,endDate,meta);
        }else {
            return "Error:This financial Asset might not exist or cannot be provided by the database";
        }
    }

    //16.05.2022 : included the SMA inside every JSON object
    //needs symbol,startDate,endDate,interval,period
    @GetMapping("/symbol/SMA")
    public String simpleMovingAverage (@RequestBody Map<String,String> body) throws JSONException {
        SimpleMovingAverage sma = new SimpleMovingAverage(Integer.parseInt(body.get("SMAPeriod")));
        JSONObject obj = new JSONObject(provideFinancialAssetInRange(body.get("symbol"),body.get("interval"),body.get("startDate"),body.get("endDate")));
        JSONArray arr = obj.getJSONArray("values");
        double closePriceToDouble;
        //StringBuilder stringBuilder = new StringBuilder();
        for (int i = arr.length()-1; i>=0;i--){
            closePriceToDouble = arr.getJSONObject(i).getDouble("close");
            arr.getJSONObject(i).put("SMA",sma.getMean());
        }

        JSONObject result = new JSONObject();
        result.put("meta",obj.get("meta"));
        result.put("values",arr);
        return result.toString();
    }

    @GetMapping("/symbol/EMA")
    public String ExponentialMovingAverage (@RequestBody Map<String,String> body) throws JSONException {
        JSONObject obj = new JSONObject(provideFinancialAssetInRange(body.get("symbol"),body.get("interval"),body.get("startDate"),body.get("endDate")));
        JSONArray arr = obj.getJSONArray("values");
        double[] result = ExponentialMovingAverage.calculateEmaValues(arr,5);
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = result.length-1; i>=0;i--){
            arr.getJSONObject(i).put("EMA",result[i]);
        }
        JSONObject resultobj = new JSONObject();
        resultobj.put("meta",obj.get("meta"));
        resultobj.put("values",arr);
        return resultobj.toString();
    }

    @GetMapping("/symbol/MACD")
    public String MACD (@RequestBody Map<String,String> body) throws JSONException {
        JSONArray arr = new JSONArray(provideFinancialAssetInRange(body.get("symbol"),body.get("interval"),body.get("startDate"),body.get("endDate")));

        JSONArray result = MACD.calculateMACD(arr, Double.parseDouble(body.get("shortPeriod")),Double.parseDouble(body.get("longPeriod")),Double.parseDouble(body.get("signalLinePeriod")));
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0 ; i< result.length();i++){
            arr.getJSONObject(i).put("MACD",result.get(arr.length()-1-i));
        }
        return  arr.toString();
    }
    private <T> String  financialAssetsFromDBtoString(T financialAssetsFromDB){
        //StringBuilder stringBuilder = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        //Set pretty printing of json
        //mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String result = null;
        try {
           result =  mapper.writeValueAsString(financialAssetsFromDB);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
    public String getAssetFromRapidApi(Map<String,String> parameters){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://twelve-data1.p.rapidapi.com/time_series?symbol="+parameters.get("symbol")+"&interval="+parameters.get("interval")+"&outputsize=5000&format=json"))
                    .header("X-RapidAPI-Host", "twelve-data1.p.rapidapi.com")
                    .header("X-RapidAPI-Key", rapidapi_key)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch (Exception e){
            e.printStackTrace();
        }
        return "getAssetFromRapidApi Error";
    }

    public String getAssetsListFromRapidApi(){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://twelve-data1.p.rapidapi.com/stocks?country=US&format=json"))
                    .header("X-RapidAPI-Key",rapidapi_key)
                    .header("X-RapidAPI-Host", "twelve-data1.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch (Exception e){
            e.printStackTrace();
        }
       return "getAssetsListFromRapidApi Error.";
    }

    private void saveDbAssetsTrackerToDatabase (DBAssetsTracker dbAssetsTracker,JSONObject metadata) throws JSONException{
        LocalDateTime ldt = ZonedDateTime.now(ZoneId.of(metadata.getString("exchange_timezone"))).toLocalDateTime();
        Instant formattedDate = formatDbAssetsTrackerDateTime(metadata.getString("interval"),ldt);
        //Store the Meta-Data to MongoDB.
        metadata.put("lastUpdate",formattedDate.toString());
        metadata.put("iscurrentlyupdating",true);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        try {
            dbAssetsTracker = mapper.readValue(metadata.toString(),DBAssetsTracker.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval((String) metadata.get("symbol"), (String) metadata.get("interval")) == null){
            dbAssetsTrackerRepository.save(dbAssetsTracker);
        }else {
            DBAssetsTracker dbAssetsTracker1 = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval((String) metadata.get("symbol"), (String) metadata.get("interval"));
            dbAssetsTracker1.setLastUpdate(dbAssetsTracker.getLastUpdate());
            dbAssetsTrackerRepository.save(dbAssetsTracker1);
        }
    }

    private Instant formatDbAssetsTrackerDateTime(String interval,LocalDateTime ldt) {
        //triggers 1 daily update
        if (interval.equals("1day") || interval.equals("1week") || interval.equals("1month")){
            if(ldt.getHour() >= 16){
                return LocalDateTime.ofInstant(ldt.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),ZoneOffset.UTC).plusHours(16).plusDays(1).toInstant(ZoneOffset.UTC);
            }else {
                return LocalDateTime.ofInstant(ldt.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),ZoneOffset.UTC).plusHours(16).toInstant(ZoneOffset.UTC);
            }
        }
        return null;
    }


    private FinancialAsset saveFinancialAssetToDatabase (Map<String,String> body , JSONObject assetValuesObj,String interval) throws JSONException {
        //FinancialAsset fa = new FinancialAsset();
        //Format the String Date into Instant Java.Time Object.
        //Get the String date, parse it, format it to LocalDateTime with hours and minutes and then convert it to Instant
        // which is an independent of Zone offsets when being stored to the Database. Storing a Date - java.time object
        //will automatically convert the Date to UTC from your system's Timezone.For example 2020-10-22T00:00:00.000+00:00
        //could possibly be stored as 2020-10-21T21:00:00.000+00:00 at the Database.Instant prevents that timezone Conversion.
        //Using different time Objects (ZonedDateTime - OffsetDatetime) throws CodecConfigurationException.
        Instant formattedDate = formatFinancialAssetDateTime(interval,assetValuesObj.getString("datetime"));
        assetValuesObj.put("datetime",formattedDate.toString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        FinancialAsset fa;
        try {
            fa = mapper.readValue(assetValuesObj.toString(),FinancialAsset.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //System.out.println(fa);
        return fa;
    }

    private AssetsList saveAssetsListToDatabase (JSONObject assetValuesObj) throws JSONException {
        //FinancialAsset fa = new FinancialAsset();
        //Format the String Date into Instant Java.Time Object.
        //Get the String date, parse it, format it to LocalDateTime with hours and minutes and then convert it to Instant
        // which is an independent of Zone offsets when being stored to the Database. Storing a Date - java.time object
        //will automatically convert the Date to UTC from your system's Timezone.For example 2020-10-22T00:00:00.000+00:00
        //could possibly be stored as 2020-10-21T21:00:00.000+00:00 at the Database.Instant prevents that timezone Conversion.
        //Using different time Objects (ZonedDateTime - OffsetDatetime) throws CodecConfigurationException.
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        AssetsList al;
        try {
            al = mapper.readValue(assetValuesObj.toString(),AssetsList.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //System.out.println(fa);
        return al;
    }

    private Instant formatFinancialAssetDateTime (String interval,String date){
        Instant responseInstant = null;
        if (interval.equals("1day") || interval.equals("1week") || interval.equals("1month")) {
            responseInstant = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
            responseInstant = LocalDateTime.ofInstant(responseInstant,ZoneOffset.UTC).plusHours(16).toInstant(ZoneOffset.UTC);
        }
        return responseInstant;

    }
//NEW APPROACH
    private String insertNewAssetToMongoDB(Map<String,String> body) throws JSONException{

            //Search for that symbol in RapidApi.
            //Get the response from the API as String.
            String rapidApiResponse = getAssetFromRapidApi(body);
            //Create a JSONObject containing the RapidApi response.
            JSONObject obj = new JSONObject(rapidApiResponse);
            //Check if the Api response status is ok , which indicates that
            //the data was loaded and sent correctly
            //create a list of the objects to be inserted
            ArrayList<FinancialAsset>  falist = new ArrayList<FinancialAsset>();
            if (!obj.getString("status").equals("ok")) {
                //***Return error message, needs more improvement, more specified error messages...***
                return "ERROR";
            } else {
                //Collect Meta-data into a dbAssetsTracker Object.(will be used in the for loop below),
                DBAssetsTracker dbAssetsTracker1 = new DBAssetsTracker();
                //Get the array contained inside the JSONObject response which includes all the requested data.
                JSONArray assetValuesArr = obj.getJSONArray("values");
                //Create a JSONObject Containing all the Meta-Data
                JSONObject metadata = obj.getJSONObject("meta");
                //Set the Collection name we are investigating
                financialAssetRepository.setCollectionName(body.get("symbol") + "_" + body.get("interval"));
                //Iterate all the objects of the JSONArray.
                LocalDateTime ldt = ZonedDateTime.now(ZoneId.of(metadata.getString("exchange_timezone"))).toLocalDateTime();

                for (int i = assetValuesArr.length() - 1; i > 0; i--) {
                    //Create a financialAsset Object and fill the data in order to store it into MongoDB.(will be used in the for loop below),
                    //********DECLARING BEFORE THE FOR LOOP STORES ONLY ONE OBJECT IN MONGODB ******

                    //date reshaping needs a better approach.
                    //updating the datetime here provides us with the datetime we need to store to the dBAssetsTracker , at the end of the for loop.
                    //Which is the last Close Value.
                    falist.add(saveFinancialAssetToDatabase(body, assetValuesArr.getJSONObject(i), metadata.getString("interval"))) ;
                }
                //Handle the Most Recent Asset (its Price might not have been determined by the markets yet)
                if (metadata.getString("interval").equals("1day")) {
                    LocalDateTime ldt2 = ZonedDateTime.now(ZoneId.of(metadata.getString("exchange_timezone"))).toLocalDateTime();
                    if (ldt.getHour() >= 16 || ldt.isBefore(ldt2.withHour(9).withMinute(30))) {
                        falist.add(saveFinancialAssetToDatabase(body, assetValuesArr.getJSONObject(0), metadata.getString("interval")));
                    }
                    saveDbAssetsTrackerToDatabase(dbAssetsTracker1, metadata);
                    dbAssetsTracker1 = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(metadata.getString("symbol"),metadata.getString("interval"));
                    financialAssetRepository.saveAll(falist);
                    dbAssetsTracker1.setIscurrentlyupdating(false);
                    dbAssetsTrackerRepository.save(dbAssetsTracker1);
                    System.out.println("completed DB insertion");
                    //The database is updated up to the next closing datetime.
                    //Return the exact API response from RapidAPI.
                    //***** NEEDS TO RETURN FROM MONGODB DATA ************
                    return rapidApiResponse;

                }
            }
            financialAssetRepository.saveAll(falist);
            return rapidApiResponse;

    }

    private String insertDataOfExistingAssetToMongoDB (Map < String, String > body, LocalDateTime ldt) throws JSONException {
        if (dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"),body.get("interval")) != null) {
            while (dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"), body.get("interval")).getIscurrentlyupdating()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(body.get("symbol"), body.get("interval")).getIscurrentlyupdating()) {
               // String res = provideFinancialAsset(body);
                return "";
            }
        }
        //create a list of the objects to be inserted
        ArrayList<FinancialAsset>  falist = new ArrayList<FinancialAsset>();
        //Collect Meta-data into a dbAssetsTracker Object.(will be used in the for loop below),
        DBAssetsTracker dbAssetsTracker = new DBAssetsTracker();
        //Search for that symbol in RapidApi.
        //Get the response from the API as String.
        String rapidApiResponse = getAssetFromRapidApi(body);
        //Create a JSONObject containing the RapidApi response.
        JSONObject obj = new JSONObject(rapidApiResponse);
        //Get the array contained inside the JSONObject response which includes all the requested data.
        JSONArray arr = obj.getJSONArray("values");
        JSONObject metadata = obj.getJSONObject("meta");
        //Create a JSONObject Containing all the Meta-Data
        //most recent Financial Asset
        financialAssetRepository.setCollectionName(body.get("symbol") + "_" + body.get("interval"));
        FinancialAsset mostRecentFa = financialAssetRepository.findAllByOrderByDatetimeDesc().get(0);
        Instant mostRecentFaDate = mostRecentFa.getDatetime();
        //System.out.println(mostRecentFaDate);
        // Iterate jsonArray using for loop
        JSONObject assetObject;
        for (int i =0 ; i < arr.length() - 1; i++) {
            // store each object in JSONObject
            assetObject = arr.getJSONObject(i);
            Instant objDateTime = LocalDate.parse(assetObject.getString("datetime")).atStartOfDay().plusHours(16).toInstant(ZoneOffset.UTC);
            //System.out.println(objDateTime);
            if (objDateTime.isAfter(mostRecentFaDate)) {
                //System.out.println("im after");
                falist.add(saveFinancialAssetToDatabase(body, assetObject, metadata.getString("interval")));
            } else {
                break;
            }
        }
        //Handle the Most Recent Asset (its Price might not have been determined by the markets yet)
        if (metadata.getString("interval").equals("1day")) {
            LocalDateTime ldt2 = ZonedDateTime.now(ZoneId.of(metadata.getString("exchange_timezone"))).toLocalDateTime();
            if (ldt.getHour() >= 16 || ldt.isBefore(ldt2.withHour(9).withMinute(30))) {

                falist.add(saveFinancialAssetToDatabase(body, arr.getJSONObject(arr.length()-1), metadata.getString("interval")));
            }
        }
        //The database is updated up to the next closing datetime.
        saveDbAssetsTrackerToDatabase(dbAssetsTracker, metadata);
        financialAssetRepository.saveAll(falist);
        dbAssetsTracker = dbAssetsTrackerRepository.findDBAssetsTrackerBySymbolAndInterval(metadata.getString("symbol"),metadata.getString("interval"));
        dbAssetsTracker.setIscurrentlyupdating(false);
        dbAssetsTrackerRepository.save(dbAssetsTracker);
        //We have the datetime in the exchange's timezone , what is left to do
        //is to compare that to the dBassetsTracker document "lastupdate" value and if it is
        // higher than the the stock exchange's timezone , request all the data and filter only the ones with date higher than the "lastupdate" record
        //date.
        List<FinancialAsset> financialAssetsFromDB = financialAssetRepository.findAllByOrderByDatetimeDesc();

        return financialAssetsFromDBtoString(financialAssetsFromDB);

    }

    private String generateResultObject(String meta, String fastring) {
        JsonObject resultobject = new JsonObject();
        JsonObject metaObj =  new Gson().fromJson(meta, JsonObject.class);
        resultobject.add("meta", metaObj);
        JsonArray valuesArray = JsonParser.parseString(fastring).getAsJsonArray();
        resultobject.add("values",valuesArray);
        return resultobject.toString();
    }

    private String generateResultObjectInRange(Instant startDate, Instant endDate, String meta) {
        String fastring = financialAssetsFromDBtoString(financialAssetRepository.findFinancialAssetsByDatetimeBetweenOrderByDatetimeDesc(startDate,endDate));

        return generateResultObject(meta, fastring);
    }
}
