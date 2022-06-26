package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.data.annotation.Id;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import java.time.Instant;

public class DBAssetsTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public String id;

    //metadata regarding the asset -- That information comes from Rapid API alongside the
    //requested Data.
    //public String name;
    public String symbol;
    public String interval;
    public String currency;
    @JsonProperty("exchange_timezone")
    public String exchangeTimezone;
    public String exchange;
    @JsonProperty("type")
    public String assetType;
    //update Tracker date
    @Column(name="lastUpdate", columnDefinition = "DATE")
    public Instant lastUpdate;

    public boolean iscurrentlyupdating;

    public boolean getIscurrentlyupdating() {
        return iscurrentlyupdating;
    }

    public void setIscurrentlyupdating(boolean iscurrentlyupdating) {
        this.iscurrentlyupdating = iscurrentlyupdating;
    }

    public String getId(){
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String tickerName) {
        this.symbol = tickerName;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExchangeTimezone() {
        return exchangeTimezone;
    }

    public void setExchangeTimezone(String exchangeTimezone) {
        this.exchangeTimezone = exchangeTimezone;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

}
