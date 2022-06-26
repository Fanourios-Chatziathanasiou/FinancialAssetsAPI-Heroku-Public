package com.unipi.fchatziathanasiou.thirdpartyapi_db.Dynamic_MongoDB_Collection_Names;

import org.springframework.stereotype.Service;


public class FinancialAssetRepositoryImpl implements FinancialAssetRepositoryCustom {

        private static String collectionName = "";

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }
    }
