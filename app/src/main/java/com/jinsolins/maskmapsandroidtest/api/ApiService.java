package com.jinsolins.maskmapsandroidtest.api;

import com.jinsolins.maskmapsandroidtest.model.StoreResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {

    @GET("storesByGeo/json")     // m = meter
    Call<StoreResponse> test(@Query("lat") double lat, @Query("lng") double lng, @Query("m") int m);
}