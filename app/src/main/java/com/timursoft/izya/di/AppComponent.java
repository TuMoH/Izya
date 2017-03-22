package com.timursoft.izya.di;

import com.timursoft.izya.http.HttpService;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {AndroidModule.class})
public interface AppComponent {

    HttpService httpService();

}
