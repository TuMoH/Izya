package com.timursoft.izya.http;

import com.timursoft.izya.model.Translations;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Single;

public interface HttpService {

    String API_URL = "http://api.lingualeo.com/";

    @GET("gettranslates")
    Single<Translations> getTranslates(@Query("word") String word);

}
