package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import com.unipi.fchatziathanasiou.thirdpartyapi_db.Dynamic_MongoDB_Collection_Names.FinancialAssetRepositoryCustom;
import org.apache.tomcat.jni.Local;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Date;
import java.util.List;


@Repository
public interface FinancialAssetRepository extends MongoRepository<FinancialAsset, String> , FinancialAssetRepositoryCustom {


    List<FinancialAsset> findAllByOrderByDatetimeDesc ();

    List<FinancialAsset> findFinancialAssetsByDatetimeBetweenOrderByDatetimeDesc(Instant startDate, Instant endDate);

}
