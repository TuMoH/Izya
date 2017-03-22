package com.timursoft.izya.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Translations {

    @SerializedName("error_msg")
    public String errorMsg;

    @SerializedName("is_user")
    public Integer isUser;

    @SerializedName("word_forms")
    public List<WordForm> wordForms = new ArrayList<>();

    public List<Translate> translate = new ArrayList<>();

}
