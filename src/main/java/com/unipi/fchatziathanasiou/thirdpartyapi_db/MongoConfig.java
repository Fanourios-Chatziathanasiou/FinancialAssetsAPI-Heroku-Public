package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

public abstract class MongoConfig extends AbstractMongoClientConfiguration {

    // rest of the config goes here

    @Override
    protected boolean autoIndexCreation() {
        return true;
    }
}