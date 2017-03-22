package com.timursoft.izya.model;

import com.google.gson.annotations.SerializedName;

public class Translate {

    public Integer id;
    public String value;

    @SerializedName("is_user")
    public Integer isUser;

}
