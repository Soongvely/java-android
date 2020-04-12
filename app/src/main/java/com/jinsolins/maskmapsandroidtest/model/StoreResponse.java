package com.jinsolins.maskmapsandroidtest.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

import lombok.Data;

@Data
public class StoreResponse {
    private int count;
    private ArrayList<Store> stores;

    @Data
    public class Store {

        @SerializedName("addr")
        private String address;
        private String code;
        private double lng;
        private double lat;
        private int m;
        @SerializedName("remain_stat")
        private String remainStat;
        @SerializedName("stock_at")
        private String stockAt;
        @SerializedName("created_at")
        private String createdAt;
        private String name;
        private String type;
    }
}
