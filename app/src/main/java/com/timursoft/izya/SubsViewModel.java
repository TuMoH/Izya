package com.timursoft.izya;

import com.timursoft.izya.model.Translations;

import me.tatarka.bindingcollectionadapter.ItemView;

public class SubsViewModel {

    public final ItemView itemView = ItemView.of(BR.item, R.layout.translate_item_view);
    public Translations translations;
    public boolean loadingTranslation = true;

}
