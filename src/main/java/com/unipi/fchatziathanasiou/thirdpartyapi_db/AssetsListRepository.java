package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface AssetsListRepository extends MongoRepository<AssetsList,String> {

     ArrayList<AssetsList> findDBAssetsTrackerBySymbolOrName (String symbol,String name);


}
