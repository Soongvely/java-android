package com.jinsolins.maskmapsandroidtest.api;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static long REQUEST_TIMEOUT = 60;
    private static String baseURL = "https://8oi9s0nnth.apigw.ntruss.com/corona19-masks/v1/";
    private static Retrofit retrofit;

    public static Retrofit getRetrofit() {

        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseURL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(getHttpLogClient())
                    .build();
        }

        return retrofit;
    }

    private static OkHttpClient getHttpLogClient() {
        return new OkHttpClient().newBuilder()
                .connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)  // Retrofit이 설정된 연결 시간 제한 내에서 서버에 연결할 수없는 경우 해당 요청을 실패한 것으로 계산
                .readTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)     // 서버와 연결 후 서버로부터의 응답까지의 시간이 읽기 시간 초과보다 크면 요청이 실패로 계산
                .writeTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)    // 읽기와 반대로 얼마나 빨리 서버에 바이트를 보낼 수 있는지 확인
                .addInterceptor(new HttpLoggingInterceptor())
                .build();
    }

    public static ApiService getApiService() {
        return getRetrofit().create(ApiService.class);
    }
}
