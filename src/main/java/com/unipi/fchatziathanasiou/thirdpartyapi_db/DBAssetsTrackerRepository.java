package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.Instant;


@Repository
public interface DBAssetsTrackerRepository extends MongoRepository<DBAssetsTracker,String> {

    DBAssetsTracker findDBAssetsTrackerBySymbolAndInterval (String symbol, String interval);


}
