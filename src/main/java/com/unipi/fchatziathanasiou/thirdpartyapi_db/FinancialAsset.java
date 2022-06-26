package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tomcat.jni.Local;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import javax.annotation.processing.Generated;
import javax.persistence.*;
import java.time.*;
import java.time.LocalDate;
import java.util.Date;


@Document(collection = "#{@financialAssetRepository.getCollectionName()}")
@JsonPropertyOrder({ "id","datetime","open","high","low","close","volume" })
public class FinancialAsset {



    @GeneratedValue (strategy = GenerationType.AUTO)
    public String id;
    public Double open,high,low,close;
    public Long volume;

    //Indexed(unique = true)

    @Column(name="datetime", columnDefinition = "DATE",unique = true)
    public Instant datetime;

//    public FinancialAsset(String tickerName, long open, long high, long low, long close, int volume,String date) {
//        this.tickerName = tickerName;
//        this.open = open;
//        this.high = high;
//        this.low = low;
//        this.close = close;
//        this.volume = volume;
//        this.date = date;
//    }


    public String getId() {
        return id;
    }

//    public void setId(String id) {
//        this.id = id;
//    }



    public Double getOpen() {
        return open;
    }

    public void setOpen(Double open) {
        this.open = open;
    }

    public Double getHigh() {
        return high;
    }

    public void setHigh(Double high) {
        this.high = high;
    }

    public Double getLow() {
        return low;
    }

    public void setLow(Double low) {
        this.low = low;
    }

    public Double getClose() {
        return close;
    }

    public void setClose(Double close) {
        this.close = close;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public Instant getDatetime() {
        return datetime;
    }

    public void setDatetime(Instant datetime) {
        this.datetime = datetime;
    }

    @Override
    public String toString() {
        return "{" +
                "\"datetime\": \"" + datetime.toString() +  "\"" +
                ", \"open\": \"" + open.toString() +  "\"" +
                ", \"high\": \"" + high.toString() +  "\"" +
                ", \"low\": \"" + low.toString() +  "\"" +
                ", \"close\": \"" + close.toString() +  "\"" +
                ", \"volume\": \"" + volume.toString() +  "\"" +
                '}';
    }
}
