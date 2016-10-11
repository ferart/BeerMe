package com.thefirm.beerme.servertalk;

import com.thefirm.beerme.BeerModel.BeerPojo;

/**
 * Created by root on 9/15/16.
 */
public interface SimpleCallback {
    public void success(BeerPojo beerPojo);
    public void error();
}
