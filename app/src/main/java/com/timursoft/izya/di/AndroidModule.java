package com.timursoft.izya.di;

import android.content.Context;

import com.timursoft.izya.http.HttpService;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
public class AndroidModule {

    private final Context context;

    public AndroidModule(Context context) {
        this.context = context;
    }

    @Provides
    @Singleton
    public HttpService httpService() {
        File cacheDir = new File(context.getCacheDir(), "responses");
        int cacheSize = 10 * 1024 * 1024; // 10 MiB

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .cache(new Cache(cacheDir, cacheSize))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(HttpService.API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();
        return retrofit.create(HttpService.class);
    }

}
